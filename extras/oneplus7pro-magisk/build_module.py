"""Rebuild the installer ZIP from its source tree and the exact original library."""
import argparse
import hashlib
import json
import stat
import zipfile
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify_patch(original, spec):
    if digest(original) != spec['original_sha256']:
        raise ValueError('Original library checksum does not match this device-specific patch')
    offset = spec['file_offset']
    before = bytes.fromhex(spec['original_bytes'])
    after = bytes.fromhex(spec['patch_bytes'])
    if len(before) != len(after) or not after or len(after) % 4:
        raise ValueError('Expected equal-sized, complete AArch64 instructions')
    if offset % 4 or original[offset:offset + len(before)] != before:
        raise ValueError('Patch location or original instruction bytes do not match')
    patched = original[:offset] + after + original[offset + len(after):]
    if len(patched) != len(original) or digest(patched) != spec['patched_sha256']:
        raise ValueError('Patched file does not match the verified output')
    differences = [i for i, (a, b) in enumerate(zip(original, patched)) if a != b]
    if not differences or any(not offset <= i < offset + len(after) for i in differences):
        raise ValueError('Unexpected changed bytes')
    return patched, differences


def write_zip(source, destination):
    with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(source.rglob('*')):
            if not path.is_file():
                continue
            name = path.relative_to(source).as_posix()
            if name.startswith('system/'):
                raise ValueError('Installer must not bundle a system library')
            info = zipfile.ZipInfo(name, (2026, 10, 21, 0, 0, 0))
            info.create_system = 3
            mode = 0o755 if path.suffix == '.sh' or path.name == 'update-binary' else 0o644
            info.external_attr = (stat.S_IFREG | mode) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, path.read_bytes())
    with zipfile.ZipFile(destination) as archive:
        assert archive.testzip() is None
    return digest(destination.read_bytes())


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--module-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--patched-output', type=Path)
    args = parser.parse_args()
    spec = json.loads((args.module_dir / 'patch-manifest.json').read_text(encoding='utf-8'))
    patched, differences = verify_patch(args.original.read_bytes(), spec)
    if (args.module_dir / 'patch.bin').read_bytes() != bytes.fromhex(spec['patch_bytes']):
        raise ValueError('Payload does not match the patch manifest')
    for path in args.module_dir.rglob('*'):
        if path.is_file() and (path.suffix in ('.sh', '.prop', '.conf') or path.name in ('update-binary', 'updater-script')):
            if b'\r' in path.read_bytes():
                raise ValueError(f'Non-UNIX line endings in {path}')
    if args.patched_output:
        args.patched_output.write_bytes(patched)
    result = {'zip': args.output.name, 'zip_sha256': write_zip(args.module_dir, args.output),
              'original_sha256': spec['original_sha256'], 'patched_sha256': digest(patched),
              'changed_byte_offsets': differences, 'source_library_bundled': False}
    print(json.dumps(result, indent=2))
