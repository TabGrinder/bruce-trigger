# User guide

## Install

1. Download **Bruce-trigger-1.0.apk** from the [1.0 release](https://github.com/TabGrinder/bruce-trigger/releases/tag/v1.0).
2. Open the file on your Android phone.
3. If prompted, allow your browser or file manager to install unknown apps, then complete the installation.

You can turn that installation permission off afterward.

## Connect

1. Start Bruce on your LILYGO T-Embed CC1101.
2. Connect it to your phone with a USB data cable. Enable OTG if your phone requires it.
3. Open Bruce Trigger and tap **CONNECT**.
4. Allow USB access and wait for the mirrored screen to appear.

The app is designed for Android 8.0 or later and Bruce 1.16.1. If it asks you to restart the LILYGO, restart the device before reconnecting.

## Controls

| Control | Action |
| --- | --- |
| Rotary ring | Browse menu items |
| Center / SELECT | Select an item |
| PREV | Move to the previous item |
| NEXT | Move to the next item |
| EXIT | Go back |
| DISCONNECT | Close the USB connection |

Games and scripts may assign different actions to these buttons. Keep the device's screen in view when the phone cannot display the current screen correctly.

## App screens

**Remote** shows the mirrored screen and navigation controls. **Device** shows connection status and device information.

Open **Settings → Diagnostics** for connection details. **Copy** copies a report you can include in a bug report. **Info**, **Memory** and **Storage** query the device and may interrupt its current screen; follow any confirmation prompt. **Clear view** clears the displayed diagnostic text.

**Settings → Help & about** contains setup help, credits and licenses. Settings also provides additional controls and power actions.

## Themes

Bruce Trigger works with custom themes, but the default theme provides smoother performance. Choose a theme in Bruce's settings, then reconnect. Animated GIF themes appear as a still preview of the first frame.

## JavaScript games

1. Open **Files** in the mirrored Bruce menu.
2. Select a `.js` or `.bjs` file and choose **JS Script Run**.
3. Wait for the game to load, then use the on-screen controls.

The script runs on the LILYGO. Bruce Trigger adds a temporary graphics bridge without changing the saved file. Hold **PREV** or **NEXT** for repeated input; **SELECT** and **EXIT** send a single press. Depending on the script, EXIT may open a menu instead of ending the game.

Only run scripts you trust. The graphics bridge does not restrict what a script can do on the device. See [Known limitations](KNOWN_LIMITATIONS.md) for unsupported graphics.

## Megalodon

Open **Others → Megalodon** to play the included adaptation of **IncursioHack's** game.

| Control | Action |
| --- | --- |
| PREV | Move up |
| NEXT | Move down |
| SELECT | Pause or resume |
| EXIT | Return to the menu |

This version runs temporarily in the LILYGO's memory. It does not replace the game in the firmware.

## Disconnect

Tap **DISCONNECT** before removing the cable. Switching to another app also closes the USB connection; tap CONNECT when you return.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| Device not detected | Use a data cable, enable OTG if needed and allow USB access. |
| Connection stalls | Stop any running script, return to Bruce's main menu and reconnect. Check that you are using Bruce 1.16.1. |
| Restart requested | Restart the LILYGO, then reconnect. |
| Slow navigation with a custom theme | Switch to Bruce's default theme. |
| GIF theme is static | GIF themes show a still preview. |
| Missing or incomplete graphics | Check the [mirroring limitations](KNOWN_LIMITATIONS.md#screen-mirroring) and use the device's screen. |
| Connection closes after switching apps | Keep Bruce Trigger in the foreground while using it. |

## Privacy

Bruce Trigger works over USB and includes no analytics. It requests USB access, with no Internet, Bluetooth or location permissions. Saved device names stay in the app's local storage.

If **Saved notes** appears in Settings, you can read, export or delete those local records. Remove private details before sharing notes or diagnostic reports.

## Verify the download

Download **SHA256SUMS** from the release page and compare it with the APK's SHA-256.

Windows PowerShell:

```powershell
Get-FileHash .\Bruce-trigger-1.0.apk -Algorithm SHA256
```

Linux, with both files in the same directory:

```bash
sha256sum -c SHA256SUMS
```

macOS:

```bash
shasum -a 256 Bruce-trigger-1.0.apk
```

Expected APK hash:

```text
3d04a453b60c1875d029d2e3ed11d2bf304eb322532b0151db58abd3a7f0c53a
```

## Get help

[Report a bug](https://github.com/TabGrinder/bruce-trigger/issues/new?template=bug_report.yml) with your phone model, Android version, LILYGO model, Bruce version, active theme and steps to reproduce the problem. Include a screenshot or connection report if it helps.

For educational purposes only. Use only on systems you own or are explicitly authorized to assess. Read the [full disclaimer](../DISCLAIMER.md).
