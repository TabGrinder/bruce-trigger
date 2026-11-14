> **Version note:** This guide covers Bruce Trigger v1.0.1. The signed APK is available under the v1.0.1 GitHub release; the older v1.0 build restricts firmware support.

# Bruce Trigger 1.0.1 — User Guide

**For educational purposes only.** Use only on systems you own or are explicitly authorized to assess. Read the [full disclaimer](../DISCLAIMER.md).

## Install the APK

1. Open the [version 1.0.1 release](https://github.com/TabGrinder/bruce-trigger/releases/tag/v1.0.1).
2. Download **BruceTrigger-v1.0.1.apk**.
3. Open the downloaded file.
4. If Android asks, allow the browser or file manager used for this installation to install unknown apps.
5. Complete the installation and open **Bruce Trigger**.

You can disable that install permission afterward. Menu names vary between Android versions and manufacturers.

## Connect over USB

Use an Android phone with USB host / OTG support and a data-capable cable.

1. Start Bruce on the LILYGO T-Embed CC1101.
2. Turn off Bruce's BLE API and stop any active script.
3. Connect the LILYGO to the phone. Enable OTG if required by the phone.
4. Tap **CONNECT** in Bruce Trigger.
5. Grant the USB access request and wait for the connection and screen state to finish preparing.

Initial screen preparation can take time. Wait for the app's connection status to settle before navigating.

If the app asks you to restart Bruce because the capture state is unknown, restart the physical device before reconnecting.

## Controls

| Control | Action |
| --- | --- |
| Rotary ring | Move through available items |
| Center / SELECT | Select the highlighted item |
| PREV | Move to the previous item |
| NEXT | Move to the next item |
| EXIT | Send the firmware's back / exit input |
| DISCONNECT | Close the current USB session |

PREV and EXIT perform different actions. In a running script, input behavior is defined by that script.

Keep the device's physical screen available when an operation cannot be mirrored.

## Themes

**Bruce Trigger works with custom themes, but the default theme provides smoother performance. For the most responsive experience, use Bruce's default theme.**

Choose the theme through Bruce's own configuration. Animated GIF themes appear as a first-frame preview in the app. If navigation feels less responsive with a custom theme, select the default theme and reconnect.

## JavaScript files

1. Open **Files** on the mirrored Bruce interface.
2. Select a `.js` or `.bjs` file.
3. Choose **JS Script Run**.
4. Wait for the graphics bridge to prepare the selected script.
5. Use the on-screen controls.

The script runs on Bruce through a temporary graphics adapter; its stored source is not rewritten. PREV and NEXT repeat while held. SELECT and EXIT send a single press. EXIT may open a script's own menu instead of ending it.

The bridge supports a defined set of drawing operations. See [Known limitations](KNOWN_LIMITATIONS.md) before assuming that every script will render. Scripts retain their normal Bruce privileges; the graphics bridge is not a script sandbox. Review a script and its intended effects before running it, and stop if the physical screen and mirrored view disagree about the current operation.

## Megalodon

Open **Others → Megalodon** from Bruce Trigger.

This launches a bundled RAM adaptation of the game by **IncursioHack**. The adaptation runs on the LILYGO and streams its drawing operations to Android. It is separate from the compiled game in the firmware.

| Control | Megalodon action |
| --- | --- |
| PREV | Move up |
| NEXT | Move down |
| SELECT | Pause or resume |
| EXIT | Return |

The adaptation does not overwrite the compiled game, firmware or stored scripts.

## Diagnostics

Open **Settings → Diagnostics**, or tap **Diagnostics** on the Device screen.

- **Connection report** displays the available connection and rendering details. Use **Copy** to share the report when requesting help.
- **Info**, **Memory** and **Storage** query the connected device. Stop capture when the app asks and follow the confirmation prompt: these queries can interrupt the current firmware screen.
- **Clear view** clears the diagnostic text shown in the app. It does not delete device files.

Use **Back to Remote** or **Back to Device** to return to the screen from which you opened Diagnostics. Android's Back action follows the same route.

## Help and device settings

**Settings → Help & about** contains connection instructions, theme and compatibility guidance, script instructions, screen status, credits, licenses and privacy information.

**Clear saved device name** appears when a USB name is stored. It disconnects and clears that local name; it does not reset the LILYGO or revoke Android's USB access permission.

**Power & restart** and **Additional controls** remain under Settings. Follow the app's prompts before sending a power action.

## Existing saved notes

If notes already exist in private app storage, **Settings → Saved notes** lets you read, export as JSON or delete them. The option is hidden on a fresh installation. There is no Sessions tab or new-note action.

An unreadable note remains stored until you explicitly choose **Delete** and confirm. Deleting it does not remove other notes.

## Disconnect

Tap **DISCONNECT** and allow the app to finish closing the session before removing the cable. The connection also closes when the app leaves the foreground.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| Device not detected | Use a data-capable cable, enable USB host / OTG and grant the USB access request. |
| Connection does not complete | Confirm the board and firmware version; turn off the Bruce BLE API and stop other scripts. |
| Restart requested | Restart the physical LILYGO, then reconnect. |
| Custom theme feels slower | Select Bruce's default theme for smoother navigation. |
| GIF theme appears static | The app shows its first frame; this is a known limitation. |
| Script graphics are missing | Check the drawing limitations; do not assume the script is fully mirrored. |
| Connection closes when switching apps | Keep Bruce Trigger in the foreground while using it. |

## Verify the download

Download **SHA256SUMS** from the same release and compare it with the APK's SHA-256.

On Windows PowerShell:

```powershell
Get-FileHash .\BruceTrigger-v1.0.1.apk -Algorithm SHA256
```

On Linux, from the directory containing the APK and SHA256SUMS:

```bash
sha256sum -c SHA256SUMS
```

On macOS:

```bash
shasum -a 256 BruceTrigger-v1.0.1.apk
```

The expected hash for this release artifact is:

```text
3d04a453b60c1875d029d2e3ed11d2bf304eb322532b0151db58abd3a7f0c53a
```

## Report a problem

Use the [bug report form](https://github.com/TabGrinder/bruce-trigger/issues/new?template=bug_report.yml) with:

- Phone model and Android version.
- Exact LILYGO model and Bruce firmware version.
- Active theme: default or the custom theme's name.
- Steps to reproduce, expected behavior and actual behavior.
- A screenshot or relevant connection report, with private information removed.

For an organized hardware check, follow the [device acceptance guide](DEVICE_ACCEPTANCE.md). Report suspected vulnerabilities through the process in [SECURITY.md](../SECURITY.md).
