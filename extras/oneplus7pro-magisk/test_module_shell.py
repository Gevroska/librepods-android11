"""Exercise install and boot guards in an isolated Linux fixture, never on Android."""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    source = args.source.resolve()
    original = args.original.read_bytes()
    spec = json.loads((source / 'patch-manifest.json').read_text())
    assert hashlib.sha256(original).hexdigest() == spec['original_sha256']
    results = []

    def run_case(name, install_change=None, boot_change=None, install_ok=True, boot_ok=None):
        with tempfile.TemporaryDirectory(prefix='op7pro-test-') as temporary:
            root = Path(temporary)
            module = root / 'modules' / spec['module_id']
            shutil.copytree(source, module)
            stock = root / 'stock.so'
            stock.write_bytes(original)
            conf = module / 'patch.conf'
            conf.write_text(conf.read_text().replace(f"TARGET='{spec['device_path']}'", f"TARGET='{stock}'"))
            checks = module / 'checks.sh'
            checks.write_text(checks.read_text().replace('/data/adb/modules_update', str(root / 'modules_update'))
                              .replace('/data/adb/modules', str(root / 'modules')))
            bindir = root / 'bin'
            bindir.mkdir()
            getprop = bindir / 'getprop'
            getprop.write_text('#!/bin/sh\ncase "$1" in\n'
                               'ro.product.device) echo "${TEST_DEVICE:-OnePlus7Pro}";;\n'
                               'ro.product.model) echo "${TEST_MODEL:-GM1917}";;\n'
                               'ro.build.version.sdk) echo "${TEST_API:-30}";;\nesac\n')
            getprop.chmod(0o755)
            env = dict(os.environ, PATH=f'{bindir}:{os.environ["PATH"]}', BOOTMODE='true',
                       ARCH='arm64', API='30', MODPATH=str(module))
            runner = root / 'install.sh'
            runner.write_text('ui_print() { printf "%s\\n" "$*"; }\n'
                              'abort() { printf "ABORT: %s\\n" "$*"; exit 73; }\n'
                              'set_perm() { :; }\nset_perm_recursive() { :; }\n'
                              '. "$MODPATH/customize.sh"\n')
            if install_change:
                install_change(root, module, stock, env)
            source_before_install = stock.read_bytes()
            result = subprocess.run(['busybox', 'ash', str(runner)], env=env,
                                    capture_output=True, text=True, timeout=20)
            assert (result.returncode == 0) == install_ok, (name, result.stdout, result.stderr)
            if install_ok:
                output = module / spec['module_file']
                assert hashlib.sha256(output.read_bytes()).hexdigest() == spec['patched_sha256']
                assert (module / 'skip_mount').exists()
                assert stock.read_bytes() == source_before_install, 'Installer modified its source file'
            if boot_ok is not None:
                if boot_change:
                    boot_change(root, module, stock, env)
                boot = subprocess.run(['busybox', 'ash', str(module / 'post-fs-data.sh')], env=env,
                                      capture_output=True, text=True, timeout=20)
                assert boot.returncode == 0, (name, boot.stdout, boot.stderr)
                assert (module / 'skip_mount').exists() != boot_ok, name
                assert (module / 'disable').exists() != boot_ok, name
                status = (module / 'boot-status.txt').read_text()
                assert status.startswith('APPROVED:' if boot_ok else 'DISABLED:'), (name, status)
            results.append({'case': name, 'passed': True})

    def corrupt(path):
        data = bytearray(path.read_bytes())
        data[-1] ^= 1
        path.write_bytes(data)

    def conflict(root, module, stock, env):
        target = root / 'modules' / 'other_bluetooth_patch' / spec['module_file']
        target.parent.mkdir(parents=True)
        target.write_bytes(b'conflicting overlay')

    run_case('exact library installs and valid boot enables overlay', boot_ok=True)
    run_case('different source library rejected', lambda r, m, s, e: corrupt(s), install_ok=False)
    run_case('different Android API rejected', lambda r, m, s, e: e.update(API='31'), install_ok=False)
    run_case('different architecture rejected', lambda r, m, s, e: e.update(ARCH='arm'), install_ok=False)
    run_case('different device rejected', lambda r, m, s, e: e.update(TEST_DEVICE='other'), install_ok=False)
    run_case('recovery installation rejected', lambda r, m, s, e: e.update(BOOTMODE='false'), install_ok=False)
    run_case('corrupt payload rejected', lambda r, m, s, e: corrupt(m / 'patch.bin'), install_ok=False)
    run_case('conflicting module rejected at installation', conflict, install_ok=False)
    run_case('ROM library change disables at boot', boot_change=lambda r, m, s, e: corrupt(s), boot_ok=False)
    run_case('module corruption disables at boot', boot_change=lambda r, m, s, e: corrupt(m / spec['module_file']), boot_ok=False)
    run_case('missing metadata disables at boot', boot_change=lambda r, m, s, e: (m / 'patch.conf').unlink(), boot_ok=False)
    run_case('Android version change disables at boot', boot_change=lambda r, m, s, e: e.update(TEST_API='31'), boot_ok=False)
    run_case('conflicting module disables at boot', boot_change=conflict, boot_ok=False)
    if (source / 'previous-original.bin').exists():
        def test1_overlay(root, module, stock, env):
            data = bytearray(original)
            data[0x2ab504:0x2ab50c] = bytes.fromhex('20008052c0035fd6')
            stock.write_bytes(data)

        def current_overlay(root, module, stock, env):
            data = bytearray(original)
            offset = spec['file_offset']
            payload = bytes.fromhex(spec['patch_bytes'])
            data[offset:offset+len(payload)] = payload
            stock.write_bytes(data)

        run_case('known test1 overlay upgrades without changing source', test1_overlay,
                 boot_change=lambda r,m,s,e:s.write_bytes(original), boot_ok=True)
        run_case('known current overlay reinstalls without changing source', current_overlay,
                 boot_change=lambda r,m,s,e:s.write_bytes(original), boot_ok=True)
        def corrupt_restore(root, module, stock, env):
            test1_overlay(root,module,stock,env)
            corrupt(module / 'previous-original.bin')
        run_case('corrupt rollback payload rejected by original hash', corrupt_restore, install_ok=False)
        run_case('different model rejected', lambda r,m,s,e:e.update(TEST_MODEL='other'), install_ok=False)
    report = {'environment': 'Linux BusyBox ash with simulated Magisk paths and properties',
              'limitations': 'No device execution, mounts, SELinux, or Magisk installation was tested. Permission helpers are mocked.',
              'cases': results}
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
