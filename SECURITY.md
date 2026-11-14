# Security policy

This policy covers Bruce Trigger's Android application and its USB integration. The current public release scope is **1.0**; this policy does not promise a response time or a maintenance period.

## Report a suspected vulnerability

If the repository's Security tab offers **Report a vulnerability**, use that private reporting route. If private reporting is unavailable, open a minimal issue asking the maintainer for a private contact method. Do not include sensitive details, credentials or exploit material in a public issue while arranging contact.

In the private report, include the affected app and firmware versions, phone and board models, relevant USB or script conditions, expected and actual behavior, and a minimal reproduction. Share only information needed to understand the issue and remove unrelated private data.

Ordinary compatibility or rendering problems belong in the [bug report form](https://github.com/TabGrinder/bruce-trigger/issues/new?template=bug_report.yml).

## Trust boundaries

- Android grants USB access to the connected device. The app sends controls and supported commands to that device; the connection is not a guarantee that the firmware or scripts are trustworthy.
- Scripts run with their normal Bruce privileges. The temporary graphics bridge does not sandbox them or certify their behavior.
- Mirroring covers supported drawing output. Missing or delayed graphics can leave the Android view incomplete; keep the physical device accessible.
- The supplied APK declares no Internet, Bluetooth or location permissions. Exporting notes or sharing copied reports moves that information outside the app's private storage at the user's direction.
- Host tests, modeled USB tests and package checks have a defined scope. See [VALIDATION.md](VALIDATION.md) before relying on a hardware or runtime claim.

Use the app within the [authorized-use scope](DISCLAIMER.md). Preserve attribution and avoid publishing someone else's private information when reporting or discussing a problem.
