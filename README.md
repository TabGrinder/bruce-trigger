# Bruce Trigger

**Control your LILYGO T-Embed CC1101 running Bruce from Android over USB.**

Bruce Trigger is an independent Android companion app by [TabGrinder](https://github.com/TabGrinder). It was inspired by using [Bruce firmware](https://bruce.computer/) on the **LILYGO T-Embed CC1101**, with the goal of bringing its controls and supported screen output to a phone.

**Release:** 1.0.1 · **Android:** 8.0+ · **Field-tested:** LILYGO T-Embed CC1101 Plus with Bruce 1.17

[Previously published version 1.0](https://github.com/TabGrinder/bruce-trigger/releases/tag/v1.0-repoint) · [1.0.1 compatibility notes](docs/BRUCE_DYNAMIC_COMPATIBILITY.md) · [User guide](docs/USAGE.md) · [Known limitations](docs/KNOWN_LIMITATIONS.md) · [Credits](CREDITS.md)

## Use disclaimer

**For educational purposes only.** Use only on systems you own or are explicitly authorized to assess. Read the [full disclaimer](DISCLAIMER.md).

## What it does

- Connects to the device through USB-C and Android USB host mode.
- Provides a rotary control, selection and navigation buttons.
- Displays supported Bruce menus and drawing output on Android.
- Works with custom themes; the default theme provides smoother performance.
- Launches supported JavaScript files from Bruce's Files menu through a temporary graphics bridge.
- Includes a Megalodon adaptation that runs in the device's RAM and sends its drawing output to Android.
- Keeps connection reports and device queries under **Settings → Diagnostics**.

The connection uses Bruce's existing interfaces and a temporary RAM bridge. It does not require reflashing the device or rewriting stored scripts. Screen mirroring is based on supported drawing operations, so some graphics and functions have [limitations](docs/KNOWN_LIMITATIONS.md).

## Requirements

| Component | Requirement |
| --- | --- |
| Phone | Android 8.0 or later with USB host / OTG support |
| Hardware | LILYGO T-Embed CC1101; the USB profile also targets the CC1101 Plus |
| Firmware | Bruce with compatible `info`, JavaScript bridge and USB drawing operations; **not restricted to a release number** |
| Connection | USB data cable and a suitable OTG adapter if needed |
| Device setup | Bruce BLE API off; other scripts stopped before connecting |

Version **1.0.1** checks JavaScript capabilities and validates the capture response rather than restricting Bruce release numbers. The maintainer reported successful physical use on a **LILYGO T-Embed CC1101 Plus with Bruce 1.17** (USB connection, mirroring, controls and restored icon). The standard CC1101 profile is supported by code but was not separately field-tested for this update. Future protocol changes may still require an app update.

## Navigation

- **Remote:** screen output, rotary navigation, selection and connection controls.
- **Device:** connection status, device identity and setup guidance.
- **Settings → Diagnostics:** connection report, Copy, Info, Memory, Storage and Clear view.
- **Settings → Help & about:** setup, themes, compatibility, credits, licenses and privacy.

## Getting started

1. Download the signed APK from the [Bruce Trigger v1.0.1 release](https://github.com/TabGrinder/bruce-trigger/releases/tag/v1.0.1) or build the project in Android Studio.
2. Power on the LILYGO with Bruce running. Turn off the Bruce BLE API and stop any active script.
3. Connect the phone and device with a USB data cable. Enable OTG if your phone requires it.
4. Open Bruce Trigger, tap **CONNECT** and grant Android's USB access request.
5. Use the rotary ring to navigate and its center to select. **PREV** and **NEXT** move between items; **EXIT** goes back.
6. Tap **DISCONNECT** when finished.

See the [user guide](docs/USAGE.md) for installation details, games, troubleshooting and checksum verification.

## Building from source

Open this project in Android Studio with Android SDK 35, JDK 17 and the Gradle 8.9 wrapper. Android Gradle Plugin is 8.7.3. The repository contains **no private signing key**; the signed APK is distributed separately as a GitHub Release asset. The maintainer tested the uploaded release APK with Bruce 1.17 and the LILYGO T-Embed CC1101 Plus; this is not a guarantee for other hardware or future firmware. See [compatibility and test notes](docs/BRUCE_DYNAMIC_COMPATIBILITY.md). Keep any signing/upload key private.

## Screenshots

| Bruce menu | Megalodon | Space Shooter |
| --- | --- | --- |
| <img src="docs/images/remote-menu.png" alt="Bruce Trigger Remote screen showing the Bruce menu" width="220"> | <img src="docs/images/megalodon.png" alt="Megalodon displayed above the Bruce Trigger rotary controls" width="220"> | <img src="docs/images/space-shooter.png" alt="Space Shooter displayed above the Bruce Trigger rotary controls" width="220"> |

Project-provided examples of the Android interface and mirrored content. See the [full gallery](docs/SCREENSHOTS.md) for captions and attribution. Screenshots illustrate the experience; the [v1.0.1 validation record](docs/VALIDATION_v1.0.1.md) states which checks were completed on the release APK.

## Known limitations

**Bruce Trigger works with custom themes, but the default theme provides smoother performance. For the most responsive experience, use Bruce's default theme.**

Animated theme GIFs display a first-frame preview. The mirror reproduces supported drawing operations rather than capturing every pixel produced by the firmware; some native games and JavaScript graphics cannot be mirrored. The USB connection closes when the app leaves the foreground.

See [Known limitations](docs/KNOWN_LIMITATIONS.md) for the supported scope.

## Local data and permissions

Bruce Trigger requests USB access to communicate with the connected device. The supplied application declares no Internet, Bluetooth or location permissions and includes no analytics SDK. The saved device name and any existing notes stay in private app storage. When saved notes exist, **Settings → Saved notes** provides access for reading, exporting or deleting them. New notes are not created in this release. Uninstalling removes this local data.

## Credits

Developed by **[TabGrinder](https://github.com/TabGrinder)**. This project uses **Codex** to assist with code optimization and documentation.

The inspiration for Bruce Trigger comes from **Bruce firmware running on the LILYGO T-Embed CC1101**. Credit belongs to the **Bruce firmware developers and contributors** for the firmware and to **LILYGO** for the hardware.

The bundled Megalodon adaptation credits **IncursioHack**. The classic font credits **Adafruit Industries**, with provenance through **TFT_eSPI by Bodmer**. See [CREDITS.md](CREDITS.md) and [NOTICE.md](NOTICE.md) for component-level attribution.

Bruce Trigger is an independent community project, not an official BruceDevices or LILYGO application. Project names and trademarks belong to their respective owners.

## License

The combined distribution is provided under the **GNU Affero General Public License v3**, as supplied in [LICENSE](LICENSE). The Android companion's MIT notice is retained in [LICENSE-MIT.txt](LICENSE-MIT.txt). Third-party components retain their applicable notices and terms.

The corresponding source accompanies the release. See [NOTICE.md](NOTICE.md) for attribution and source references.

## Documentation

| Task | Guide |
| --- | --- |
| Install, connect and use the controls | [User guide](docs/USAGE.md) |
| Check theme, graphics and hardware limits | [Known limitations](docs/KNOWN_LIMITATIONS.md) |
| Browse interface examples | [Screenshot gallery](docs/SCREENSHOTS.md) |
| Build and run software checks | [Building from source](docs/BUILDING.md) |
| Review completed checks and remaining work | [v1.0.1 validation](docs/VALIDATION_v1.0.1.md) and [historical v1.0 review](docs/REVIEW.md) |
| Record tests on a phone and LILYGO | [Device acceptance guide](docs/DEVICE_ACCEPTANCE.md) |
| Report a bug or contribute a change | [Contributing](CONTRIBUTING.md) |
| Understand trust boundaries or report a vulnerability | [Security policy](SECURITY.md) |
| Review credits, licensing and authorized use | [Credits](CREDITS.md), [notices](NOTICE.md) and [disclaimer](DISCLAIMER.md) |

The source includes the Android app in `app/`, build and regression tools in `tools/`, host fixtures in `tests/`, and documentation and additional audit sources in `docs/`.

## Feedback

Report reproducible problems with the [bug report form](https://github.com/TabGrinder/bruce-trigger/issues/new?template=bug_report.yml). Include your phone model, Android version, exact LILYGO model, Bruce version, active theme and steps to reproduce the problem. Review screenshots and diagnostic reports for private information before posting. For sensitive reports, follow [SECURITY.md](SECURITY.md).
