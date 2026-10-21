"""Build and emulate a PSM-scoped Basic Mode fix for the verified Qualcomm ROM."""
import argparse
import hashlib
import io
import json
import lzma
import struct
from pathlib import Path
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from elftools.elf.elffile import ELFFile
from keystone import Ks, KS_ARCH_ARM64, KS_MODE_LITTLE_ENDIAN
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import *

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--original', required=True, type=Path)
parser.add_argument('--module-dir', required=True, type=Path)
parser.add_argument('--output-dir', required=True, type=Path)
args = parser.parse_args()
ROOT = args.output_dir.resolve()
ROOT.mkdir(parents=True, exist_ok=True)
SOURCE = args.original.resolve()
ORIGINAL_SHA = 'bb648946419dbd10a96787d892e8d45f868ba4ea76d8595e59a2171e577914a8'
ENTRY, OFFSET, RETURN, WARNING = 0x2ab504, 0x2ab554, 0x2ab5cc, 0x2ab59c
MODULE = args.module_dir.resolve()
raw = SOURCE.read_bytes()
sha = lambda b: hashlib.sha256(b).hexdigest()
assert sha(raw) == ORIGINAL_SHA
elf = ELFFile(io.BytesIO(raw))
debug = ELFFile(io.BytesIO(lzma.decompress(elf.get_section_by_name('.gnu_debugdata').data())))
symbols = {s.name: s for s in debug.get_section_by_name('.symtab').iter_symbols() if s.name}
assert symbols['_Z22l2c_fcr_chk_chan_modesP9t_l2c_ccb']['st_value'] == ENTRY
assert symbols['_Z22l2c_fcr_chk_chan_modesP9t_l2c_ccb']['st_size'] == 248

# RCB.psm (+2) is an internal virtual registration ID. The wire PSM is real_psm
# (+4), as independently verified in l2cu_send_peer_connect_req at 0x2b11bc.
# Keep the original prologue, null check, warning path and common epilogue.
assembly = f'''
    ldr x9, [x19, #0x50]
    cbz x9, normal
    ldrh w9, [x9, #4]
    mov w8, #0x1001
    cmp w9, w8
    b.ne normal
    mov w8, #0x100
    strh w8, [x19, #0x108]
    b {RETURN:#x}
normal:
    ldr x9, [x19, #0x38]
    ldr w8, [x9, #0x60]
    ldrb w9, [x19, #0x109]
    tbnz w8, #3, streaming
    and w9, w9, #0xfffffff7
streaming:
    tbnz w8, #4, store
    and w9, w9, #0xffffffef
store:
    strb w9, [x19, #0x109]
    cbnz w9, {RETURN:#x}
'''
patch = bytes(Ks(KS_ARCH_ARM64, KS_MODE_LITTLE_ENDIAN).asm(assembly, OFFSET)[0])
assert len(patch) == WARNING - OFFSET == 72
patched = raw[:OFFSET] + patch + raw[OFFSET + len(patch):]
assert patched[ENTRY:OFFSET] == raw[ENTRY:OFFSET]
assert patched[WARNING:ENTRY+248] == raw[WARNING:ENTRY+248]
assert len(patched) == len(raw)
loads = [s for s in elf.iter_segments() if s['p_type'] == 'PT_LOAD']

class Machine:
    def __init__(self, code):
        self.uc = uc = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
        end = max(s['p_vaddr'] + s['p_memsz'] for s in loads)
        uc.mem_map(0, (end + 4095) & ~4095)
        for seg in loads:
            uc.mem_write(seg['p_vaddr'], code[seg['p_offset']:seg['p_offset']+seg['p_filesz']])
        self.base = base = 0x10000000
        uc.mem_map(base, 0x20000)
        self.ccb, self.lcb, self.tls, self.shadow, self.stack, self.stop, self.rcb, self.cfg = [
            base+x for x in (0,0x2000,0x4000,0x6000,0x10000,0x18000,0x8000,0xa000)]
        uc.mem_write(0x36ea50, struct.pack('<Q', base+0x7000))
        self.disconnected = False
        def hook(machine, address, size, _):
            if address in (0xe0928,0xe0b0c):
                machine.reg_write(UC_ARM64_REG_PC,machine.reg_read(UC_ARM64_REG_LR))
            elif address == symbols['_Z20l2cu_disconnect_chnlP9t_l2c_ccb']['st_value']:
                self.disconnected = True
                machine.reg_write(UC_ARM64_REG_PC,machine.reg_read(UC_ARM64_REG_LR))
        uc.hook_add(UC_HOOK_CODE, hook)

    def reset(self, features, modes, preferred, real_psm, virtual_psm=0x1002):
        uc=self.uc
        uc.mem_write(self.ccb, bytes(0x1000))
        uc.mem_write(self.cfg, bytes(0x100))
        uc.mem_write(self.ccb+0x38,struct.pack('<Q',self.lcb))
        uc.mem_write(self.ccb+0x50,struct.pack('<Q',self.rcb if real_psm is not None else 0))
        uc.mem_write(self.rcb,struct.pack('<BxHH',1,virtual_psm,real_psm or 0))
        uc.mem_write(self.lcb+0x60,struct.pack('<I',features))
        uc.mem_write(self.ccb+0x108,bytes((preferred,modes)))
        uc.mem_write(self.tls+0x28,struct.pack('<Q',0x123456789abcdef0))
        self.disconnected=False

    def call(self, entry, arg1=0):
        uc=self.uc
        saved={reg:0x12340000+reg for reg in range(UC_ARM64_REG_X19,UC_ARM64_REG_X28+1)}
        saved[UC_ARM64_REG_X29]=0x1234abcd
        for reg,value in saved.items():uc.reg_write(reg,value)
        uc.reg_write(UC_ARM64_REG_X0,self.ccb)
        uc.reg_write(UC_ARM64_REG_X1,arg1)
        uc.reg_write(UC_ARM64_REG_X18,self.shadow)
        uc.reg_write(UC_ARM64_REG_SP,self.stack)
        uc.reg_write(UC_ARM64_REG_TPIDR_EL0,self.tls)
        uc.reg_write(UC_ARM64_REG_LR,self.stop)
        uc.emu_start(entry,self.stop,count=1500)
        assert uc.reg_read(UC_ARM64_REG_PC)==self.stop
        assert uc.reg_read(UC_ARM64_REG_SP)==self.stack
        assert uc.reg_read(UC_ARM64_REG_X18)==self.shadow
        for reg,value in saved.items(): assert uc.reg_read(reg)==value
        return uc.reg_read(UC_ARM64_REG_W0)

old,new=Machine(raw),Machine(patched)
count=0
for features in (0,8,16,24):
    for modes in (0,1,8,9,16,17,24,25,255):
        for preferred in (0,3,4):
            for real_psm in (None,1,0x17,0x19,0x1f,0x1001,0x1003):
                for m in (old,new):m.reset(features,modes,preferred,real_psm)
                before,after=old.call(ENTRY),new.call(ENTRY)
                state=bytes(new.uc.mem_read(new.ccb,0x1000))
                if real_psm==0x1001:
                    assert after==1 and state[0x108:0x10a]==b'\x00\x01'
                    expected=bytearray(old.uc.mem_read(old.ccb,0x1000))
                    expected[0x108:0x10a]=b'\x00\x01'
                    assert state==expected
                else:
                    assert before==after and state==bytes(old.uc.mem_read(old.ccb,0x1000))
                count+=1

# Catch accidental matching of the virtual ID instead of the real wire PSM.
new.reset(24,8,3,0x1003,virtual_psm=0x1001)
assert new.call(ENTRY)==8
assert bytes(new.uc.mem_read(new.ccb+0x108,2))==b'\x03\x08'

# Run the actual peer-configuration validator for the captured Basic request.
peer_cfg=symbols['_Z28l2c_fcr_process_peer_cfg_reqP9t_l2c_ccbP15tL2CAP_CFG_INFO']['st_value']
new.reset(0,8,3,0x1001)
before_peer=new.call(peer_cfg,new.cfg)
new.call(ENTRY)
after_peer=new.call(peer_cfg,new.cfg)
assert before_peer != after_peer
assert before_peer==2 and after_peer==1, (before_peer,after_peer)

# The outgoing request must actually drop ERTM, not merely accept the peer.
adjust=symbols['_Z27l2c_fcr_adj_our_req_optionsP9t_l2c_ccbP15tL2CAP_CFG_INFO']['st_value']
new.reset(0,8,3,0x1001)
new.call(ENTRY)
new.uc.mem_write(new.cfg+0x24,b'\x01')
new.uc.mem_write(new.cfg+0x26,b'\x03')
assert new.call(adjust,new.cfg)==1
assert new.uc.mem_read(new.cfg+0x24,1)[0]==0
assert new.uc.mem_read(new.cfg+0x26,1)[0]==0
assert not new.disconnected

assert (MODULE/'customize.sh').is_file(), 'Use the supplied module source directory'
(MODULE/'patch.bin').write_bytes(patch)
spec=json.loads((MODULE/'patch-manifest.json').read_text())
spec.update(version='1.0.0-test2',patched_sha256=sha(patched),patch_sha256=sha(patch),
            virtual_address=OFFSET,file_offset=OFFSET,original_bytes=raw[OFFSET:WARNING].hex(),
            patch_bytes=patch.hex(),runtime_verified=False,app_fork='LibrePods 1.0.4-android11.1',
            behavior='Force preferred Basic mode and allow only Basic for real PSM 0x1001; preserve original behavior for other channels.',
            patch_instructions=[f'{i.mnemonic} {i.op_str}' for i in Cs(CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN).disasm(patch,OFFSET)])
# Preserve the Windows-packaged test2 manifest bytes on every build host.
(MODULE/'patch-manifest.json').write_text(json.dumps(spec,indent=2)+'\n',newline='\r\n')
conf = f'''MODULE_ID='op7pro_bt_librepods'
EXPECTED_DEVICE='OnePlus7Pro'
EXPECTED_MODEL='GM1917'
TARGET='/system_ext/lib64/libbluetooth_qti.so'
MODULE_FILE='system/system_ext/lib64/libbluetooth_qti.so'
ORIGINAL_SHA256='{ORIGINAL_SHA}'
PATCHED_SHA256='{sha(patched)}'
PATCH_SHA256='{sha(patch)}'
PATCH_OFFSET={OFFSET}
FILE_UID=0
FILE_GID=0
FILE_MODE=0644
FILE_CONTEXT='u:object_r:system_lib_file:s0'
PREVIOUS_SHA256='ec690875645f369797afa45fafcfeb8a773e2589f0a9cee39f88445fdf6c1d4e'
PREVIOUS_OFFSET=2798852
'''
(MODULE/'patch.conf').write_text(conf,newline='\n')
(MODULE/'previous-original.bin').write_bytes(raw[ENTRY:ENTRY+8])
(MODULE/'current-original.bin').write_bytes(raw[OFFSET:WARNING])
(MODULE/'module.prop').write_text('id=op7pro_bt_librepods\nname=OnePlus 7 Pro Bluetooth compatibility (unofficial)\nversion=1.0.0-test2\nversionCode=2\nauthor=Gevroska\ndescription=Exact-ROM AirPods control-channel Basic Mode fix for GM1917, LineageOS 18.1 microG 20221002.\n',newline='\n')
(ROOT/'libbluetooth_qti.test2.so').write_bytes(patched)
result={'original_sha256':sha(raw),'patched_sha256':sha(patched),'offset':OFFSET,'patch_length':len(patch),
        'emulated_mode_cases':count,'virtual_psm_false_match_rejected':True,
        'captured_peer_basic_config_result_before':before_peer,'captured_peer_basic_config_result_after':after_peer,
        'outgoing_configuration_uses_basic_without_fcr_option':True,
        'callee_saved_registers_stack_and_shadow_stack_preserved':True,
        'limitations':'Function-level emulation; actual controller and AirPods negotiation must be tested separately.'}
(ROOT/'test2-binary-verification.json').write_text(json.dumps(result,indent=2)+'\n',newline='\n')
print(json.dumps(result,indent=2))
