# Building Bruce Trigger 1.0.1

The maintained build path for this release is **Android Studio / Gradle**. The source tree matches the Java and Android resource files included in the maintainer's uploaded Android Studio 1.0.1 project. The APK distributed in GitHub Releases is signed separately and must not be committed to the source repository.

## Requirements

- Android Studio with Android SDK Platform 35 installed.
- JDK 17 for the Gradle daemon.
- Gradle Wrapper 8.9 (`gradle/wrapper/gradle-wrapper.properties`).
- Android Gradle Plugin 8.7.3 (root `build.gradle`).

## Debug build

Open the directory containing `settings.gradle` in Android Studio, allow Gradle sync and use **Build > Build APK(s)**. To build using the included wrapper:

```powershell
.\gradlew.bat :app:assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`.

## Signed Release build

Use **Build > Generate Signed Bundle / APK > APK**, choose the existing private keystore, and select the `release` build variant. Alternatively, configure secure release signing locally without committing your key or credentials. After a successful build, the release APK is available under the destination selected in the signing wizard, often `app/release/app-release.apk`.

The application ID is `com.tabgrinder.brucetrigger`, version name `1.0.1`, code `101`. Do not add `.test` to the **release** application ID. Do not hardcode `android:debuggable` in `AndroidManifest.xml`.

## Verify the release

Use your Android SDK `apksigner` utility to verify the signed APK:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs "PATH_TO_APP_RELEASE_APK"
```

Check version and package with Android Studio's APK Analyzer. Compare the SHA-256 with the checksum provided in the same release:

```powershell
Get-FileHash "PATH_TO_APP_RELEASE_APK" -Algorithm SHA256
```

The v1.0.1 checks are documented in [`VALIDATION_v1.0.1.md`](VALIDATION_v1.0.1.md). The historical 1.0 validation is preserved in [`../VALIDATION.md`](../VALIDATION.md).

## Regression tests

```powershell
python tools/test.py
python tools/test_usb.py
```

These are host tests with USB doubles; they do not substitute for hardware validation. The older `tools/build.py` / `tools/check_apk.py` path is a separate legacy Android SDK pipeline and should not be used to identify or verify the APK generated through Android Studio for v1.0.1.

The private keystore, passwords, `local.properties`, APK build products, `.gradle/`, and `.idea/` must not be committed to GitHub.
