# Android 11 compatibility fork

This fork starts from upstream LibrePods v1.0.4, commit
`89356c08cea3db0b6b54792d9329d09a87cc85fa`. It does not rebuild newer features on
top of the old v0.1.0-rc.4 app. Upstream history is retained, with the adaptation
on the `android11-backport` branch and the original repository named `upstream`.
The published fork is
[Gevroska/librepods-android11](https://github.com/Gevroska/librepods-android11),
with `android11-backport` as its default branch.

The README includes upstream documentation for reference. Its OS requirements,
installation steps and download links may describe the original app; this guide
covers the Android 11 fork.

## Scope and identity

The FOSS build supports Android 11 / API 30 and retains upstream AirPods Pro 3
model recognition, capabilities and protocol handling. Its label is
**LibrePods Android 11**, and its version has the suffix `-android11.1`.
The package ID remains `me.kavishdevar.librepods` to preserve compatibility with
the upstream Xposed integration and optional system-app module. This fork is
not signed by upstream. Installing over an upstream-signed APK will require
uninstalling that APK first, which removes its app data.

The separately produced OnePlus 7 Pro Magisk module is still required for that
phone's Android 11 Bluetooth stack. The APK does not install it. The module is
restricted to the specific extracted ROM library; it is not a generic patch
for every Android 11 device. The test2 module now has initial hardware validation
on the target phone; see [module sources and results](extras/oneplus7pro-magisk/README.md).
CI artifacts remain
unsigned; the downloaded APK is signed and verified locally before installation.

## Compatibility changes

- The FOSS minimum Android version is API 30; the Play flavor retains its
  upstream minimum and is outside this fork's delivery workflow.
- Android 11 requests Location permission for BLE discovery. Android 12 and
  later use Nearby devices permissions. Notifications request a runtime
  permission only on Android 13 and later.
- Android 11 explains the system Location switch and offers an optional route
  to allow background discovery through the app's system permission settings.
  Foreground location must be granted before that option is offered.
- Material styling uses a static palette on Android 11; dynamic system colors
  are used only on Android 12 and later.
- Internal receiver registration uses AndroidX compatibility handling.
- Call-state monitoring uses `PhoneStateListener` on Android 11 and
  `TelephonyCallback` on Android 12 and later, with permission-aware registration
  and cleanup. This retains the existing call and head-gesture behavior.
- Bluetooth alias updates use the older hidden method on Android 11 and the
  public API on newer versions. A failed local alias update does not prevent
  the AirPods rename command or the saved app name. The newest device-wide
  disconnect API is guarded; older Android uses upstream's profile fallback.
- Device diagnostics use the SDK level available on Android 11.
- Closing onboarding before the Bluetooth service initializes no longer tries
  to clean up uninitialized service state or an unregistered receiver.
- Setup explains the Bluetooth patch requirement instead of telling Android 11
  users that they must upgrade their operating system. Continuing acknowledges
  the patch; it does not verify the module or claim Bluetooth success.

## Build locally

Use JDK 21 and the checked-in Gradle wrapper (9.4.1). Install Android command-line
tools 23.0 (build 16111833), set `ANDROID_HOME`, and install the versions pinned
by the project:

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;37.0.0' 'ndk;30.0.14904198' 'cmake;3.22.1'
cd android
./gradlew --no-daemon --no-configuration-cache --max-workers=2 -Pandroid11ApiCheck lintFossDebug testFossDebugUnitTest assembleFossRelease
```

The modern compile/target SDK is retained; minimum SDK determines installation
eligibility. `android11ApiCheck` selects the `NewApi` and `InlinedApi` lint checks
for the compatibility audit. Omit it to run the broader upstream lint checks.
Do not add a lint baseline or suppress new API errors to force a passing audit.
The documented command disables the configuration cache and runs the Kotlin
compiler in the Gradle process with a 2 GiB heap. On small VMs, use one worker.

With no release signing configuration, the release output is:

`android/app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk`

Development builds use Gradle's standard debug certificate. That certificate
is not the future release key and should not be used for distributed releases.

## GitHub Actions and signing

`.github/workflows/ci-android.yml` builds on ordinary GitHub-hosted Ubuntu
runners. It checks APIs, runs unit tests, builds the unsigned FOSS release,
inspects its minimum SDK, and uploads the APK, checksum and test reports.
It does not require signing secrets, publish a GitHub release, upload to Google
Play, or send messages. The inherited Android publishing workflow was replaced.

Builds and test reports are available from the fork's
[Android workflow](https://github.com/Gevroska/librepods-android11/actions/workflows/ci-android.yml).
Download the `LibrePods-Android11-unsigned` artifact from a successful run.
The APK must be signed locally before installation. Keep the release key on the
local signing machine and reuse it for every update.
Align the APK, sign it locally using Android SDK `apksigner`, verify its
signature, and compute a new checksum of the signed APK. Alignment must precede
signing; do not change the APK after signing. Keep a secure backup of the same
private key for future updates. The unsigned checksum will differ from the
signed checksum. Never commit a keystore, passwords, `local.properties`, or a
device extraction to Git.

## Validation boundaries

On 21 October 2026, the focused API audit completed with zero errors and zero
warnings. All 16 automated test cases passed: four SDK/model policy cases,
two socket-constructor cases, four call-state cases, four permission-screen
cases, and two startup/cleanup cases. The framework tests run on API 30 and 33.

[GitHub Actions run 39605291144](https://github.com/Gevroska/librepods-android11/actions/runs/39605291144)
also passed these checks. Its downloaded APK was signed locally and its signature
verified for API 30. All original APK file contents were unchanged by signing.

Automated tests cover the permission boundaries, retained Pro 3 model mapping,
and Android 11/13 startup and permission screens in Robolectric. The final
delivery report records which checks actually passed. Robolectric runs Android
framework behavior on the JVM; it is not a Bluetooth controller or an AirPods
simulator. Compilation and API lint alone do not establish hardware operation.

The target OnePlus 7 Pro subsequently connected to AirPods Pro 3 model A3064
with the test2 Magisk module and this existing APK. Battery reporting,
device-confirmed noise-mode changes, audio-profile reconnection, app restart
and a brief screen-off test passed while Phone permission stayed denied.
The initial module needed a Basic Mode negotiation correction; see the
[hardware validation record](extras/oneplus7pro-magisk/VALIDATION.md) for evidence
and the remaining test boundaries.

All phone tests must go through **Agent ADB**. After release signing, validate:

1. The specific Magisk module is installed and its patched Bluetooth library
   is actually loaded; record module state and Bluetooth process stability.
2. Install the signed APK, complete setup, grant Location for discovery, and
   verify the Location switch. Test both granting and declining optional
   permissions. If background discovery is needed, select Allow all the time.
3. Connect the Pro 3 and check model identification, battery, noise-control
   commands/responses, ear detection, and reconnection. Test other desired
   features separately; their inclusion in upstream does not prove operation.
4. Check background behavior, screen-off recovery, Bluetooth toggling and a
   second boot. Verify normal media audio, calls, and another Bluetooth device.
5. If needed, disable the Magisk module and reboot to restore the original
   Bluetooth library. Remove the fork independently through Android settings.

## References

- [Upstream v1.0.4](https://github.com/librepods-org/librepods/tree/v1.0.4)
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [Robolectric](https://robolectric.org/)

The upstream GPL-3.0 license and attribution are retained. This is an unofficial
fork and has not been endorsed by LibrePods, OnePlus, Magisk or Apple.
