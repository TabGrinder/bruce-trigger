# Building from source

The release APK is built with the included Python script. Android Studio and Gradle configuration is also included, but that build path has not been tested.

## Requirements

- Python 3.
- JDK 17, including the Java compiler.
- Android SDK Platform 35.
- Android SDK Build Tools 35.0.0.

## Build

Run this from the repository root, replacing the SDK paths with your local paths:

```bash
python3 tools/build.py \
  --android-jar /path/to/android-sdk/platforms/android-35/android.jar \
  --build-tools /path/to/android-sdk/build-tools/35.0.0 \
  --new-signing-key
```

The APK is saved to `build/Bruce-trigger-1.0.apk`.

Use `--new-signing-key` for your first local build. For subsequent builds, omit it to reuse your key. Keep the generated `.signing/` directory private and backed up. An APK signed with your key cannot be installed as an update to the official release APK.

## Run software checks

```bash
python3 tools/test.py
python3 tools/test_usb.py
python3 tools/check_apk.py --build-tools /path/to/android-sdk/build-tools/35.0.0
```

These checks cover parsing, rendering, simulated USB behavior, the APK manifest, signatures and the embedded build-input inventory.

## Android tests

Instrumentation sources are in `docs/audit-tests/`. The runner expects an SDK directory containing:

- `android-35/android.jar`
- `android-15/` with Build Tools 35.0.0
- `platform-tools/`
- `emulator/`

Create an AVD, then run:

```bash
python3 docs/audit-tests/run_android_audit.py \
  --sdk /path/to/test-sdk \
  --avd-home /path/to/avds \
  --avd BruceRelease35 \
  --port 5580 \
  --adb-port 5041
```

The port options let the test emulator run separately from other Android sessions.

See [Validation](../VALIDATION.md) for the release's test status and the [hardware test checklist](DEVICE_ACCEPTANCE.md) for checks that need a phone and LILYGO.
