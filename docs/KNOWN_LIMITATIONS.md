# Known limitations

## Themes

Bruce Trigger works with custom themes, but the default theme provides smoother performance. Responsiveness varies with the theme, phone and device workload.

Animated GIF themes show a still preview of the first frame.

## Screen mirroring

Bruce Trigger recreates drawing commands sent by the firmware. Graphics that use unsupported drawing methods may be missing or incomplete.

- Some native games cannot be mirrored.
- JavaScript `drawJpg`, `drawGif` and `gifOpen` are not supported by the graphics bridge.
- Some custom sprite sizes, fonts and text alignment may render differently.
- Megalodon uses the included adaptation; it does not mirror the compiled firmware game.

Use the physical device's screen whenever the mirrored view is incomplete.

## Compatibility

Version 1.0 targets **Bruce 1.16.1** on the **LILYGO T-Embed CC1101**. The USB profile also recognizes the CC1101 Plus, but it has not been verified on hardware. Compatibility with other boards or firmware versions has not been confirmed.

The phone must support USB host / OTG. Starting mirroring or loading a script can take a moment while the device prepares it.

## USB connection

Keep Bruce Trigger in the foreground while using it. Switching to another app closes the connection.

If the app cannot confirm that a script or mirroring session has stopped, it will ask you to restart the LILYGO before reconnecting. Device queries in **Settings → Diagnostics** may interrupt the current firmware screen.

## Test status

Software and package checks passed. Android runtime and physical-device tests remain outstanding. See [Validation](../VALIDATION.md) for details and the [hardware test checklist](DEVICE_ACCEPTANCE.md) to record results for your setup.
