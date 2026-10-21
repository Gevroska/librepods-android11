# OnePlus 7 Pro hardware validation

Tested on 21 October 2026 with GM1917, Android 11/API 30,
LineageOS 18.1 microG 20221002, Magisk 30.7, LibrePods
`1.0.4-android11.1` and AirPods Pro 3 model A3064.

## Failure and correction

The original test1 module was mounted and its instructions were verified in
the running Bluetooth process. The app found the paired AirPods and attempted
the proprietary connection, so the failure was not missing Phone permission
or BLE discovery. The AirPods accepted the connection request, but the phone
requested ERTM and rejected their Basic Mode configuration.

Test2 replaces the unconditional success stub with a change scoped to real
PSM `0x1001`: select and permit Basic Mode. It restores the original behavior
for other channels. The real PSM is used because Android assigns a different
virtual registration PSM to outgoing connections.

The captured test2 negotiation contains Basic configuration requests and
successful responses in both directions. The host MTU is 1691, compared with
the earlier ERTM request's 8087. No vendor identity spoofing was needed.

## Confirmed checks

- 756 ARM64 feature/mode/PSM cases, plus actual outgoing and incoming
  configuration-function checks, passed in emulation.
- All 17 Linux BusyBox installer/boot-guard fixture cases passed, including
  upgrading the existing test1 overlay and rejecting damaged restoration data.
- Magisk installation succeeded. After the user rebooted, the boot guard
  approved test2. The loaded library hash, original function entry and all
  72 replacement bytes were verified in the Bluetooth process.
- LibrePods identified model A3064 and displayed live battery data.
- With both earbuds worn, incoming device packets confirmed Transparency
  (`0x0d -> 0x03`) and restoration of Noise Cancellation (`0x0d -> 0x02`).
  The initial test with both earbuds out of ear produced no acknowledgment;
  outgoing commands or optimistic UI state alone were not treated as success.
- Phone/call permissions remained denied. Pairing and app data were retained.
  The existing signed APK was used without replacement.
- Audio-profile disconnect/reconnect retained pairing and was followed by
  device-confirmed mode changes. A separate app-only restart opened a fresh
  control socket and restored model identification and the control UI.
- With LibrePods backgrounded and the screen off for approximately 31 seconds,
  the foreground service remained alive. Incoming device packets confirmed
  Transparency and restoration of Noise Cancellation. The phone was then woken.

These tests were performed with USB/ADB attached. Long-term Doze, unplugged
battery life, a full Bluetooth toggle, physical range/case cycles, calls,
acoustic quality, every optional feature, other Bluetooth devices and other
ROM builds are not established by these checks.

## Artifact identity

- Installer: `OnePlus7Pro-Bluetooth-LibrePods-1.0.0-test2.zip`
- Installer SHA-256: `efe278ebac1f7d5dc63f8aef1d081d6d6ea997824d039a15449ad82000861405`
- Patched library SHA-256: `812eabe13936471b46d56e15380627113e31fcc9445ecbf9ffca47b6dca8b60c`
- Signed APK SHA-256: `51fd1e3a99ee2d3c9abeaa18befcdca0a1436a3cf7c5855ee555bab4930c03f2`

The installer remains the exact archive used in the phone test. Its internal
README records that hardware testing was pending when it was packaged; this
document records subsequent results. Private device extracts, signing keys,
pairing keys and raw captures are not included in this repository.
