# Security policy

This policy covers Bruce Trigger's Android app and USB integration.

## Report a vulnerability

Use **Security → Report a vulnerability** if private reporting is available. Otherwise, open an issue asking for a private contact method. Keep vulnerability details and credentials out of public issues.

Include the affected app and firmware versions, phone and board models, steps to reproduce the issue, and its impact. Share only the information needed to investigate it.

For ordinary connection or display problems, use the [bug report form](https://github.com/TabGrinder/bruce-trigger/issues/new?template=bug_report.yml).

## Scripts and device access

Only connect devices and run scripts you trust. Scripts retain their normal Bruce privileges; the graphics bridge does not sandbox them. Mirroring can omit unsupported graphics, so keep the physical device accessible.

The app communicates over USB and declares no Internet, Bluetooth or location permissions. Check notes and diagnostic reports for private information before sharing them.

See [Validation](VALIDATION.md) for test coverage and the [use disclaimer](DISCLAIMER.md) for authorized use.
