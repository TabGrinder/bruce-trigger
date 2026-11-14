# Device acceptance guide

Use this guide to record checks on the exact APK, phone and LILYGO you intend to support. It is a test plan, not a completed test report. The historical 1.0 results are in [VALIDATION.md](../VALIDATION.md); the 1.0.1 source test results are in [BRUCE_DYNAMIC_COMPATIBILITY.md](BRUCE_DYNAMIC_COMPATIBILITY.md).

## Record the setup

Record the date, tester, APK filename and SHA-256, app version, phone model, Android version, board model, Bruce version or commit, active theme, and cable / OTG adapter. Check the APK against [SHA256SUMS](../SHA256SUMS) using the commands in the [user guide](USAGE.md#verify-the-download).

Use a device and environment you own or are authorized to assess. Start with Bruce's default theme, BLE API off and other scripts stopped. Keep the physical screen visible. Use menus and a reviewed local game for these checks; radio transmission or interaction with another system is not required.

## Check the user flows

For each row, record **Pass**, **Fail**, **Not tested** or **Not applicable**, plus observations. Do not treat a blank result as a pass.

| Check | Procedure and expected result | Result / notes |
| --- | --- | --- |
| Installation | Install the matching APK and open it. Confirm version 1.0.1 in Android app information / Help & about. | Not tested |
| USB permission | Connect over USB. Deny access once, then retry and grant access. The app should explain the state and remain usable. | Not tested |
| Connection | Connect with the intended board and firmware. Wait for preparation, then compare a supported menu with the physical screen. | Maintainer reports pass on CC1101 Plus / Bruce 1.17; details not recorded |
| Bruce 1.16.1 regression | Connect to Bruce 1.16.1 and verify mirror, navigation, theme, reconnect and disconnect behave as before. | Not tested |
| Bruce 1.17 acceptance | Connect to Bruce 1.17 and confirm 1.0.1 does not reject by version, mirrors supported graphics, and displays a usable screen. | Maintainer reports pass on CC1101 Plus; details not recorded |
| Protocol incompatibility | When testing a custom/test firmware missing expected APIs, confirm safe navigation-only behavior and no capture or theme upload. | Not tested |
| Navigation | Exercise the ring, SELECT, PREV, NEXT and EXIT. Confirm expected menu actions and that released controls stop repeating. | Not tested |
| Device and Diagnostics | Check device identity. Open Diagnostics from Device and from Settings; Back should return to the originating screen. Copy a report and check its contents. | Not tested |
| Device queries | From an idle menu, use Info, Memory and Storage. Follow capture-stop confirmations and record any firmware screen changes. | Not tested |
| Default theme | Navigate several supported menus. Record readability, visible omissions and responsiveness without inventing FPS or latency measurements. | Maintainer reports smoother behavior than custom themes; no measurements |
| Custom theme | Select a known custom theme through Bruce and reconnect. Check supported images and text. A GIF's first-frame preview is expected. | Maintainer reports slower/less consistent behavior; specific themes not recorded |
| Megalodon | Launch Others → Megalodon; check PREV/NEXT, SELECT pause/resume and EXIT. Repeat launch and return. | Not tested |
| JavaScript | Launch a reviewed `.js` or `.bjs` file through Files → JS Script Run. Check supported drawing output, input and the script's exit behavior. | Not tested |
| Foreground lifecycle | Leave the app, then return. USB should close when the app leaves the foreground; reconnect explicitly. | Not tested |
| Focus loss | Open a system overlay while using a held control. Confirm pending input stops and does not resume unexpectedly after dismissal. | Not tested |
| Disconnect and recovery | Disconnect normally, reconnect, then test cable removal from an idle menu. Record recovery; restart Bruce if the app cannot confirm capture stopped. | Not tested |
| Local settings | If a saved device name exists, clear it. Confirm the local name clears and the session disconnects without resetting the board. | Not tested |
| Existing notes | If existing records are available, check read, export and confirmed deletion. On a fresh install, Saved notes should remain hidden. | Not tested |
| Repeated use | Repeat connect, navigate, launch, exit and disconnect. Record duration, repetitions, failures and any visible degradation. | Not tested |

## Report the result

Attach the completed setup and result table to a test report or issue. Link any relevant screenshots or redacted connection reports. Identify known rendering limits separately from unexpected failures. If testing a different APK, record its hash rather than reusing the released artifact's validation result.

**Bruce Trigger works with custom themes, but the default theme provides smoother performance. For the most responsive experience, use Bruce's default theme.**
