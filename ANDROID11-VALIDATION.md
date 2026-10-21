# Android 11 runtime validation

Test date: 21 October 2026. Target: OnePlus 7 Pro GM1917, Android 11 / API 30,
LineageOS 18.1 with microG, AirPods Pro 3, and the exact-ROM test2 Magisk module.
All phone operations were performed through Agent ADB. Screenshots, recordings,
checksums and focused logs were exchanged privately through MEGAsync.

## Build and installation

- Installed version: `1.0.4-android11.3`, version code `100040303`.
- Source: `3e27a8da92468883fe81ecf8c4904dcd62f376b0`.
- [GitHub build 39667232320](https://github.com/Gevroska/librepods-android11/actions/runs/39667232320):
  23 tests passed; focused Android API lint reported zero issues.
- The GitHub artifact was downloaded, verified and signed locally using the
  existing release key. Signature verification for API 30, archive alignment,
  and comparison of every original APK file against the unsigned artifact passed.
- The APK was installed as an update. App data, pairing and Phone permission
  choices were preserved. No new Magisk module or reboot was needed.

Unsigned APK SHA-256:
`10b766eea54a57c00367f72aec934a142c3a0221966232880351fc6775ecd086`

Installed signed APK SHA-256:
`d47bbef777b6bd376ca517a0aecd92ceb8f7d92f0f8728dfccf403e6c4afaeeb`

Signing certificate SHA-256:
`098e195c361a668732768353b4434157529912a1de1670c4d4379ac2869fd47c`

## Popup behavior

The original popup showed its video rectangle but allowed the underlying app
title and settings button to appear through the label area. The android11.2
renderer fixed the opaque rounded background, video composition, dismissal and
repeat display. Phone screenshots and recordings established this independently
of the connection trigger.

Testing then exposed a separate upstream dependency: with automatic ear detection
disabled, no natural connection Island was triggered. Re-enabling that preference
restored the popup, which the user also confirmed visually. The preference was
subsequently disabled again by the user.

In android11.3, a fresh control-socket connection triggers the popup independently
of ear automation. On the phone, a fresh app-only connection with the preference
verified OFF displayed the Island automatically, without a test broadcast. The
right AirPod was worn and the left was in the case. The popup showed the active
earbud's 61% battery with legible content and an opaque rounded background. The
user confirmed that it worked. The popup's own setting and overlay permission
still apply; ear automation remains a separate choice.

A second fresh connection also displayed the popup with ear detection OFF.
Window snapshots showed exactly one top Island during each display and zero
after automatic dismissal. The user setting was left OFF.

## Other runtime checks

The android11.2 checks and the final android11.3 repeat verified Home/resume and
Back/relaunch binding ownership without the previous uninitialized-receiver or
unbind errors. The service remained foreground: Home/resume retained one activity
binding, Back removed it, and relaunch created one new binding. Battery data and
actual incoming control-state replies continued arriving. No new notification
throttling or app crash was observed in the scoped post-update captures.

During the android11.3 checks with ear detection OFF, the music track remained
active and Bluetooth A2DP reported playback. No playback or listening-mode command
was issued by the test agent. This supports continuity in the sampled interval,
not a guarantee of uninterrupted sound over long sessions.

While both AirPods were worn, actual incoming device replies confirmed a change
to Transparency and restoration of the original Noise Cancellation mode. Later
single-ear tests did not issue unsuitable listening-mode changes. These are
control-channel tests, not a claim that every upstream AirPods feature works.

## Remaining boundaries

Android FMQ, ion, Codec2 and MediaPlayer diagnostic messages remain in the
captured logs. They were not accompanied by an observed app crash or failed
popup rendering in these tests. They have not all been eliminated, and this
record does not claim an error-free ROM or exhaustive app coverage.

The parser retains unhandled opcodes and malformed-frame diagnostics. Recognizing
the observed short control/status envelopes does not establish full head-tracking
support. Call permissions remain denied by choice, so call handling was not
validated. Long-duration audio, every firmware variation and every upstream
feature remain outside these focused checks.

For the Bluetooth patch and its separate validation boundaries, see the
[Magisk validation record](extras/oneplus7pro-magisk/VALIDATION.md).
