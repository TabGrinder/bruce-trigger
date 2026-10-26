# Hardware test checklist

Use this checklist to test Bruce Trigger on a phone and LILYGO. Release test results are listed in [Validation](../VALIDATION.md).

## Setup

Record the date, APK version and SHA-256, phone model, Android version, LILYGO model, Bruce version, theme and USB cable or adapter. [Verify the APK checksum](USAGE.md#verify-the-download) before testing.

Start at Bruce's main menu with the default theme. Keep the physical screen visible.

## Checks

Record **Pass**, **Fail**, **Not tested** or **Not applicable** for each check, with any useful observations.

| Check | Expected behavior | Result |
| --- | --- | --- |
| Install | The app opens and shows version 1.0 in Help & about. | Not tested |
| USB permission | Denying access leaves the app usable. Retrying and granting access allows connection. | Not tested |
| Connect | A supported menu appears on the phone and matches the device. | Not tested |
| Navigate | The ring, SELECT, PREV, NEXT and EXIT perform the expected actions. Released buttons stop repeating. | Not tested |
| Diagnostics | Device details and reports are available. Back returns to the previous app screen. | Not tested |
| Device queries | Info, Memory and Storage work after any required confirmation. Record changes to the device's screen. | Not tested |
| Default theme | Menus are readable and navigation responds as expected. | Not tested |
| Custom theme | Supported images and text display correctly. GIF themes show a still preview. | Not tested |
| Megalodon | PREV/NEXT move, SELECT pauses or resumes, and EXIT returns to the menu. | Not tested |
| JavaScript game | A trusted script opens through Files → JS Script Run, responds to input and exits as expected. | Not tested |
| Switch apps | USB closes when Bruce Trigger leaves the foreground. Reconnection works on return. | Not tested |
| Interrupt input | Opening a system overlay while holding a control stops pending input. | Not tested |
| Disconnect | Normal disconnect, cable removal and reconnection recover correctly. Restart the LILYGO if prompted. | Not tested |
| Local settings | Clearing a saved device name removes the name and disconnects without resetting the board. | Not tested |
| Saved notes, if present | Reading, exporting and confirmed deletion work. | Not tested |
| Repeated use | Repeat connection, navigation, game launch and disconnect. Record duration and any failures. | Not tested |

## Report results

Attach the setup details and completed table to your test report or issue. Include screenshots or connection reports where useful, after removing private information.

Check [Known limitations](KNOWN_LIMITATIONS.md) when reviewing display differences.
