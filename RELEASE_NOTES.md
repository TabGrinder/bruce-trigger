# Bruce Trigger 1.0.1 — Dynamic firmware compatibility

**Release artifact:** `BruceTrigger-v1.0.1.apk` — SHA-256 `34a5b877a111f3bcf6f6b1ac3eee43b08016bfa89c182145a89bf0eca0c255b7`. Android Studio Release build was successful and the maintainer reported that the APK works with Bruce 1.17 on a LILYGO T-Embed CC1101 Plus. Not all device-acceptance test cases have been recorded.

- Removed the exact `1.16.1` firmware-release gate; `info` still must identify Bruce on the reviewed LILYGO T-Embed CC1101 / Plus.
- Added a bounded, RAM-only JavaScript capability handshake before mirror or theme scripts; no display or filesystem mutation occurs during negotiation.
- Checks basic serial/delay interfaces separately from the availability of the read-only storage module. Theme sync is skipped if storage APIs are not available, while native capture can still be attempted.
- Maintains fail-closed stream/packet validators, single-interpreter ownership, USB timeouts, no command replay and physical-screen navigation if mirroring is unavailable.
- Preserves the previously added privacy-policy link and the original interface and screen-control features.
- Version bumped to `1.0.1` (`versionCode 101`), targeting the existing Android application ID.
- Java host regression suites include 127 capability-negotiation checks and USB/graphics/navigation tests. Android Release compilation succeeded; the APK signature and APK-content digest have been independently verified. The maintainer confirmed basic use with Bruce 1.17 on the CC1101 Plus. Extended hardware and multi-device testing remains outstanding.

For design, exact tests and limitations, see [docs/BRUCE_DYNAMIC_COMPATIBILITY.md](docs/BRUCE_DYNAMIC_COMPATIBILITY.md).

---

# Bruce Trigger 1.0 Release

**Bruce Trigger 1.0** is an independent Android USB companion for **Bruce firmware on the LILYGO T-Embed CC1101**.

Bruce Trigger was inspired by using Bruce on the LILYGO and brings device navigation and supported screen output to Android.

## Use disclaimer

**For educational purposes only.** Use only on systems you own or are explicitly authorized to assess. Read the [full disclaimer](https://github.com/TabGrinder/bruce-trigger/blob/v1.0/DISCLAIMER.md).

## Included in this release

- USB-C connection with Android USB host / OTG support.
- Rotary navigation, selection and back controls.
- Mirroring of supported Bruce menus and drawing operations.
- Custom-theme support.
- Launching supported JavaScript files through Bruce's Files menu.
- A Megalodon RAM adaptation that runs on the connected device.
- Focused **Remote** and **Device** navigation.
- **Settings → Diagnostics** for connection reports and device queries.
- **Help & about** for setup, compatibility, credits and privacy.

The app uses a temporary RAM bridge without reflashing the device or rewriting stored scripts.

## Requirements

- Android **8.0 or later** with USB host / OTG support.
- **LILYGO T-Embed CC1101**, with the USB profile also targeting the CC1101 Plus.
- **Bruce 1.16.1**.
- A USB data cable.

Turn off Bruce's BLE API and stop other scripts before connecting.

## Installation

Download **Bruce-trigger-1.0.apk** from the release assets, install it on your phone, connect the LILYGO, tap **CONNECT** and grant USB access.

The release also provides the matching source and **SHA256SUMS**.

The [user guide](https://github.com/TabGrinder/bruce-trigger/blob/v1.0/docs/USAGE.md) includes checksum verification and troubleshooting. See the [screenshots](https://github.com/TabGrinder/bruce-trigger/blob/v1.0/docs/SCREENSHOTS.md) for interface examples.

## Known limitations

**Bruce Trigger works with custom themes, but the default theme provides smoother performance. For the most responsive experience, use Bruce's default theme.**

Animated theme GIFs show a first-frame preview. Mirroring covers supported drawing operations; some native games and JavaScript graphics cannot be mirrored. Megalodon is a RAM adaptation, separate from the compiled firmware game. The USB session closes when the app leaves the foreground.

See the [full limitations](https://github.com/TabGrinder/bruce-trigger/blob/v1.0/docs/KNOWN_LIMITATIONS.md) and [user guide](https://github.com/TabGrinder/bruce-trigger/blob/v1.0/docs/USAGE.md).

## Credits

Thank you to the **Bruce firmware developers and contributors** and **LILYGO** for the firmware and hardware that inspired this project. Additional credit goes to **IncursioHack** for Megalodon and **Adafruit Industries / Bodmer's TFT_eSPI** for the classic font and its provenance.

Developed by **[TabGrinder](https://github.com/TabGrinder)**. This project uses **Codex** to assist with code optimization and documentation. Bruce Trigger is an independent community project; full [credits](https://github.com/TabGrinder/bruce-trigger/blob/v1.0/CREDITS.md) and license notices accompany the source.

## Validation

Host regressions, modeled USB tests and package checks passed for this APK. Android runtime and physical-device validation remain pending. See the [validation record](https://github.com/TabGrinder/bruce-trigger/blob/v1.0/VALIDATION.md) for the exact scope and the [device acceptance guide](https://github.com/TabGrinder/bruce-trigger/blob/v1.0/docs/DEVICE_ACCEPTANCE.md) for recording hardware results.

Report issues through the [Bruce Trigger bug report form](https://github.com/TabGrinder/bruce-trigger/issues/new?template=bug_report.yml), including your device, firmware version, active theme and reproduction steps.
