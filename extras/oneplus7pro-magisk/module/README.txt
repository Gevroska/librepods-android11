OnePlus 7 Pro / LibrePods Bluetooth module 1.0.0-test2
Author: Gevroska

Exact target: GM1917, OnePlus7Pro, arm64, Android 11/API 30,
LineageOS 18.1 microG 20221002. This is not a generic ROM patch.

The test1 patch was loaded but did not connect to the AirPods Pro 3.
The captured negotiation showed the phone requesting ERTM while the AirPods
required Basic Mode. The phone rejected the peer's Basic configuration.

This revision restores the original function entry and changes 72 bytes inside
l2c_fcr_chk_chan_modes. For real wire PSM 0x1001 only, it sets preferred mode to
Basic and allowed modes to Basic. For every other PSM, the original feature
masking, return value and warning behavior are preserved. The original null
check, stack handling and return path remain unchanged. The check uses the real
PSM, not Android's internal virtual registration number.

No device identity spoofing, SELinux policy changes or app permission grants
are included. The APK is separate. Phone/call permissions remain optional.

Install from Magisk while Android is running, then reboot. Upgrading from the
known test1 overlay is supported: the installer restores the original bytes
only in its copied library and validates the complete stock checksum before
applying test2. Unknown ROM/library hashes are rejected. The system partition
is not modified. The boot guard validates the original ROM and module files
before enabling the overlay; mismatches disable the module.

Original library SHA-256:
bb648946419dbd10a96787d892e8d45f868ba4ea76d8595e59a2171e577914a8
Test2 library SHA-256:
812eabe13936471b46d56e15380627113e31fcc9445ecbf9ffca47b6dca8b60c
Patch file offset: 0x2ab554, length 72 bytes.

Offline validation: 756 mode/feature/PSM combinations emulated against the
actual ARM64 functions. Non-target behavior matches the original library.
The original peer validator rejects the captured Basic request before the
mode fix and accepts it after; the outgoing configuration drops the FCR option.
These tests do not substitute for a real device connection. Runtime validation
of this revision is pending at packaging time.

Rollback: disable this module in Magisk and reboot to use the original ROM
library. The app and its data are independent. Do not flash on another ROM.

Source reference:
https://github.com/LineageOS/android_vendor_qcom_opensource_system_bt/tree/6c64ff7760e082c2af0048d28cef8b533e2bc3ba/stack/l2cap
The original LibrePods GPL-3.0 license is included. This unofficial module is
not endorsed by LibrePods, OnePlus, Qualcomm, LineageOS, Magisk or Apple.
