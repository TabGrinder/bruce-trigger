# Bruce Trigger 1.0.1 — Release validation

This page describes the **uploaded release APK** supplied by the maintainer on 2026-11-14, not the original v1.0 package.

- APK name: `BruceTrigger-v1.0.1.apk`
- Package: `com.tabgrinder.brucetrigger`
- Version name/code: `1.0.1` / `101`
- APK SHA-256: `34a5b877a111f3bcf6f6b1ac3eee43b08016bfa89c182145a89bf0eca0c255b7`
- Signature certificate SHA-256: `041ca9035b9e807025a0b12c3f01229800448267d1f17a46ec517cdf4514440b`
- APK ZIP integrity: pass
- APK Signature Scheme v2 RSA PKCS#1 SHA-256 signed-data signature: pass
- APK Signature Scheme v2 content digest: pass
- APK file inside the uploaded Android Studio project ZIP: byte-identical to the separately uploaded APK
- Android Studio Release build: successful as reported by maintainer
- Device test: maintainer confirms installation, USB control/mirroring and app icon with Bruce 1.17 on LILYGO T-Embed CC1101 Plus
- Known limitation: default Bruce theme runs more smoothly than some custom themes

The APK signature matches the certificate historically documented for Bruce Trigger v1.0. This does not itself establish how Google Play App Signing might handle Play-installed versions. Extended hardware, OS, endurance, game, and regression tests are not claimed.

Source code and Gradle configuration are supplied in the corresponding GitHub commit. Gradle Wrapper distribution was corrected from an incompatible 9.6 URL to 8.9; this is a build configuration change and does not modify the already signed, tested APK.
