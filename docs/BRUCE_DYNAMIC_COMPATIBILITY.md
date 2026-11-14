# Bruce Trigger 1.0.1 — Dynamic firmware compatibility

## Goal

Connect to a reviewed LILYGO T-Embed CC1101 / Plus without needing a new Bruce Trigger build merely because the **Bruce firmware version string changes**. A firmware update that removes or alters commands or graphics packet formats can still require an application update. Firmware version and hardware identity are logged, not used as an allowlist of firmware releases.

## Connection sequence

1. Android opens the selected native USB CDC port only after user permission.
2. A read-only `info` query must identify Bruce and the reviewed CC1101 / Plus board; unknown boards remain out of scope.
3. A **single nonce-scoped RAM JavaScript probe** checks `serial.println`, `serial.cmd` and `delay`; it separately checks for a usable `storage` module (`read`, `readdir`). It does not start a display capture, write storage, invoke navigation or flash the device.
4. Results: **FULL** permits an optional theme read, then native capture; **MIRROR** bypasses the theme read and attempts native capture; **FAIL** retains verified USB navigation, with the physical device screen required.
5. Native capture is **not inferred from the probe**: the existing `display start` return marker, interpreter exit + prompt, geometry, framing, supported drawing-operation validation and no-replay timeout logic must still complete.
6. Probe state is scoped to the current USB session and cleared on disconnection. Unexpected data, malformed frames and unconfirmed interpreter state still fail closed.

## Why this is not an unrestricted bypass

Some firmware updates keep their release-number differences but retain the USB protocol. These now have a route to work without an app update. For unsupported hardware or a genuinely incompatible firmware the application must not try arbitrary scripts or interpret unknown binary packets. An API-presence probe **cannot certify future graphics-format compatibility**; successful mirroring on Bruce 1.17 was observed only on the tested CC1101 Plus configuration. Hardware acceptance remains essential.

## Changed files

- `app/src/main/java/com/tabgrinder/brucetrigger/FirmwareCompatibility.java` (new): identity gate independent of firmware release.
- `UsbMirrorBridge.java`: nonce-based capability probe and session-local capability flags.
- `UsbSerialSession.java`: script operations require positively probed capability.
- `UsbController.java`: serialized USB capability negotiation and diagnostic state.
- `MainActivity.java`: new probe stage and navigation-only fallback without changing the existing controls or interface.
- `app/build.gradle` and `app/src/main/AndroidManifest.xml`: version `1.0.1`, code `101`.
- Host regression tests in `tests/usb-stubs/`, including `CapabilityProbeRegressionTest.java`.

## Tests performed in the source environment

- `python tools/test.py`: **PASS** (existing frame, layout, transport, budget, diagnostics, screen, input and serial-boundary suites).
- `python tools/test_usb.py`: **PASS** (existing USB/session, capture bridge, theme, upload, menus, game, lifecycle, input ownership and graphics suites, plus 127 new compatibility/probe assertions).
- Deterministic host-reply simulations exercise identities `1.16.1`, `1.17`, `1.18`, `2.0.0` and `23.7.1-beta`, probe outcomes, stale nonce, missing output, and prevented capture before verification. These are **not physical firmware tests**.

**Release verification:** The maintainer compiled and signed a Release APK in Android Studio, installed it, and confirmed USB mirroring and controls on the LILYGO T-Embed CC1101 Plus running Bruce 1.17. APK signature and content integrity were independently verified. No Google Play upload or comprehensive multi-device testing is claimed.

## Android Studio build

1. Unzip the archive, then in Android Studio choose **File → Open** and select the folder containing `settings.gradle` and `app/build.gradle`.
2. Install or select Android SDK Platform 35 and a matching build-tools package. Android Gradle Plugin is 8.7.3; use JDK 17+ and Gradle 8.9. If Android Studio asks for a Gradle distribution, choose a compatible local installation. The project includes the Gradle 8.9 wrapper; dependencies are fetched through the configured Maven repositories.
3. Sync the project. In the **Build** menu choose **Build APK(s)** (or run `gradle assembleDebug` if Gradle is installed).
4. Install the resulting **debug** APK on an Android phone and test with the physical device. An Android Studio debug-signed APK should not be uploaded as a release update.
5. For Google Play, create a properly signed production **AAB** using the same private signing/upload key as your release workflow; never publish that key in the source archive. Increment `versionCode` again for any subsequent upload already using code 101.

## Hardware acceptance before distribution

Record phone model, Android version, exact Bruce version, CC1101 / Plus board, USB cable, app build and logs. Confirm (a) capture and navigation on Bruce 1.16.1, (b) capture and navigation on Bruce 1.17, (c) theme read/no-theme fallback, (d) USB attach/detach and reconnect, (e) submenus and supported JS, (f) packet corruption and timeouts cause safe stop. Use [DEVICE_ACCEPTANCE.md](DEVICE_ACCEPTANCE.md) to record each outcome; do **not** mark tests passed until observed.

## Privacy and signing

Original local-USB architecture retained. The existing privacy-policy URL in Help & about is preserved. No permissions to access the Internet were added. No private Android keystore, signing passwords, or signed APKs are packaged.
