# Contributing

Bug reports, documentation fixes and focused improvements are welcome.

## Report a bug

Check the [user guide](docs/USAGE.md) and [known limitations](docs/KNOWN_LIMITATIONS.md), then [open a bug report](https://github.com/TabGrinder/bruce-trigger/issues/new?template=bug_report.yml). Include your app version, phone, Android version, LILYGO model, Bruce version, active theme and steps to reproduce the problem.

Remove private information from screenshots and reports. For vulnerabilities, follow the [security policy](SECURITY.md).

## Submit a change

1. Explain the problem and keep the change focused.
2. Add a regression test where practical, and update any affected documentation.
3. Follow the [build guide](docs/BUILDING.md) and run the relevant checks.
4. Open a pull request describing the change and how you tested it.

For compatibility changes, link the relevant firmware documentation or source revision. Documentation should be written in English and match the app's controls.

## Testing

For application changes, run:

```bash
python3 tools/test.py
python3 tools/test_usb.py
```

Build and check the APK as described in [Building from source](docs/BUILDING.md). Use the [hardware test checklist](docs/DEVICE_ACCEPTANCE.md) for changes that need a phone and LILYGO. State which tests you ran and which remain outstanding.

For documentation changes, check links, images and command examples. An APK rebuild is only needed when its build inputs change.

## Credits and licenses

Credit borrowed work and retain its license notices. See [LICENSE](LICENSE), [LICENSE-MIT.txt](LICENSE-MIT.txt) and [NOTICE.md](NOTICE.md).

Do not commit signing keys, credentials, local SDK settings or generated build files. Keep discussions respectful and follow the [use disclaimer](DISCLAIMER.md).
