# Bruce Trigger v1.0.1 — Dynamic Firmware Compatibility

Bruce Trigger is an independent Android USB-C companion for the LILYGO T-Embed CC1101 / CC1101 Plus running compatible Bruce Firmware.

## What's new

- Removed the fixed Bruce 1.16.1 version restriction.
- Added bounded, session-scoped capability checks before display mirroring and theme operations.
- Confirmed by the maintainer to work with Bruce 1.17 on the LILYGO T-Embed CC1101 Plus.
- Preserved USB-C navigation, supported screen mirroring, and the existing application interface.
- Restored the selected Bruce Trigger launcher icon.

## Compatibility

- Android 8.0+ with USB host / OTG support.
- LILYGO T-Embed CC1101 / CC1101 Plus (the **CC1101 Plus** was field-tested for this update).
- Bruce firmware with compatible USB, JavaScript, and drawing interfaces.

The application no longer blocks newer firmware solely because of its version string. A future firmware change to the USB or graphics protocol may still require an app update.

## Known limitations

Bruce's default theme provides smoother performance than some custom themes. The mirror reproduces supported drawing operations, not every native screen pixel, so certain graphics or animations may be incomplete.

## Installation

Download **BruceTrigger-v1.0.1.apk** below, install it on your Android device, connect your LILYGO with a USB-C data cable, tap **CONNECT** and grant USB access.

## Verification

See `SHA256SUMS.txt` in the release assets for the APK SHA-256 checksum. Source code for this release is linked under GitHub's automatically generated source archives.

## Credits

Developed by **TabGrinder**. Thanks to the Bruce Firmware developers and contributors and LILYGO. Component attributions and licenses remain available in the repository.

Bruce Trigger is an independent community project and is not affiliated with or endorsed by BruceDevices or LILYGO.
