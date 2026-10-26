# Validation

## Release 1.0

| Item | Value |
| --- | --- |
| Application ID | `com.tabgrinder.brucetrigger` |
| Version name / code | `1.0` / `100` |
| APK SHA-256 | `3d04a453b60c1875d029d2e3ed11d2bf304eb322532b0151db58abd3a7f0c53a` |
| Signing certificate SHA-256 | `041ca9035b9e807025a0b12c3f01229800448267d1f17a46ec517cdf4514440b` |
| Build-input digest | `922bae32e60c02607fff7bf63f6b2c54bdd5691914df11d8fafed32d8aea7c63` |

## Completed checks

| Check | Result |
| --- | --- |
| Release build: JDK 17, Android SDK Platform 35, Build Tools 35.0.0 | Passed |
| Host tests: parsing, framing, rendering records and input handling | Passed |
| USB tests with simulated Android and device behavior | Passed |
| APK integrity, manifest and v2/v3 signatures | Passed |
| Build inputs against the APK's embedded source inventory | Passed |
| Android instrumentation compilation and signing | Passed |

The [host](docs/validation/host.log), [USB](docs/validation/usb.log) and [package](docs/validation/package.log) logs are included with the source. Build and test commands are in [Building from source](docs/BUILDING.md).

## Outstanding checks

- Android runtime and visual tests: the test emulator failed to start.
- Physical phone and LILYGO tests: no hardware was available for this test run.
- JavaScript engine harness: MicroQuickJS was unavailable.

Compilation and simulated tests do not confirm hardware compatibility or performance. Use the [hardware test checklist](docs/DEVICE_ACCEPTANCE.md) to record device results. The screenshots are interface examples, not test results for this APK.
