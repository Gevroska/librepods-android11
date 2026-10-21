# Reproducing the OnePlus 7 Pro Bluetooth patch

This exact-ROM module fixes the observed Basic/ERTM negotiation mismatch on
the AirPods control channel (real PSM `0x1001`). See `module/README.txt` for
the target ROM, installation and rollback instructions. The proprietary ROM
library is not distributed in this source bundle.

Use Python 3.13 and the pinned `requirements.txt`. Supply the privately extracted
original `libbluetooth_qti.so`; its SHA-256 must be
`bb648946419dbd10a96787d892e8d45f868ba4ea76d8595e59a2171e577914a8`.

```sh
python -m pip install -r requirements.txt
python prepare_patch_test2.py --original /path/to/libbluetooth_qti.so --module-dir module --output-dir verification
python build_module.py --original /path/to/libbluetooth_qti.so --module-dir module --output OnePlus7Pro-Bluetooth-LibrePods-1.0.0-test2.zip
```

The preparation script assembles and emulates the actual ARM64 code. It checks
756 feature/mode/PSM combinations, preservation of non-target behavior and
callee-saved registers, rejection of a misleading virtual PSM, the outgoing
Basic configuration and acceptance of the captured peer configuration.
The original null check, logging path, stack and shadow-call-stack handling
remain intact. Patch scope is the real PSM, rather than a particular device
address: another service using real PSM `0x1001` will also use Basic Mode.

On Linux with BusyBox installed, run the installer fixtures separately:

```sh
python test_module_shell.py --source module --original /path/to/libbluetooth_qti.so --output installer-results.json
```

All 17 fixture cases passed before installation. They cover known-overlay
upgrades, source-file preservation, corruption, incompatible ROM/device/API
and competing modules. They mock Magisk permission helpers; actual mounting,
SELinux and radio behavior require phone validation.

The independently reproduced installer SHA-256 is
`efe278ebac1f7d5dc63f8aef1d081d6d6ea997824d039a15449ad82000861405`.
The JSON test reports describe pre-installation checks. Subsequent phone results
are recorded in [VALIDATION.md](VALIDATION.md).

Attribution: Gevroska. Upstream licensing is retained in `module/LICENSE`.
