# Known Limitations

## Custom themes and responsiveness

**Bruce Trigger works with custom themes, but the default theme provides smoother performance. For the most responsive experience, use Bruce's default theme.**

Custom-theme support does not imply identical responsiveness across all themes. This release does not promise a fixed frame rate or latency.

Animated theme GIFs display a first-frame preview rather than full animation.

## Screen mirroring

The mirror reconstructs supported drawing operations received from Bruce. It is not a universal framebuffer capture.

Graphics drawn through unsupported paths may be incomplete or unavailable. This includes other compiled native games that do not expose their pixels, JavaScript `drawJpg`, `drawGif` and `gifOpen`, unsupported custom sprite sizes, and font or text-alignment details omitted by the firmware's drawing output.

The bundled Megalodon feature is a RAM adaptation of the game. It is not a capture of the original compiled game.

## Compatibility

Release 1.0.1 targets the **LILYGO T-Embed CC1101 / Plus** and native USB CDC **303A:1001**. It does not restrict a Bruce version number, but instead checks available JavaScript interfaces before uploading bridge scripts. A changed native drawing protocol, missing `display start`, unsupported graphics or a different board may still require app changes. Navigation may remain available without mirroring. **The maintainer confirmed basic hardware use with Bruce 1.17 on a LILYGO T-Embed CC1101 Plus; other boards and firmware releases are not independently field-validated for this update.** The Android phone must support USB host / OTG.

Firmware-side preparation still takes time when starting capture, changing capture mode or launching a script. Remote operation is not guaranteed to match the timing of the physical controls.

## USB session lifecycle

The app closes USB when it leaves the foreground. A background connection is not maintained.

If the app cannot confirm that the interpreter or capture session stopped, restart Bruce on the physical device before reconnecting. A device query from Settings → Diagnostics can leave or interrupt the current firmware screen. Viewing or copying the connection report uses the information already available to the app.

## Feature scope

This release provides USB control. It does not provide Bluetooth or Wi-Fi transport, OTA firmware updates, hardware BOOT/RST control or LED telemetry.

Saved notes, when present, are local notes rather than recordings of device activity. The release offers access to existing notes but no new-note creation.

## Validation scope

The existing [VALIDATION.md](../VALIDATION.md) describes the historical 1.0 APK, not a 1.0.1 artifact. See [1.0.1 validation](VALIDATION_v1.0.1.md) and [compatibility details](BRUCE_DYNAMIC_COMPATIBILITY.md) for this release. The old 1.0 release passed the recorded host, modeled USB and package checks; its Android instrumentation compiled, but Android runtime, visual and physical-device checks remained pending. For 1.0.1, host tests and Android Release compilation completed; basic real-device USB, mirroring, controls and icon behavior were reported by the maintainer on the CC1101 Plus with Bruce 1.17. Other acceptance checks and performance measurements remain pending. No frame-rate, power-stability or long-duration reliability claim is made.

The custom-theme recommendation records the maintainer's observed behavior. Performance remains dependent on the phone, USB connection, firmware workload and active theme.

The [screenshot gallery](SCREENSHOTS.md) provides visual examples of these limits. Use the [device acceptance guide](DEVICE_ACCEPTANCE.md) when recording results for a specific phone, board and APK.
