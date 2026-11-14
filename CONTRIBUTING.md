# Contributing to Bruce Trigger

Thank you for helping improve Bruce Trigger. Keep changes focused and document what they do on the intended Android, Bruce and LILYGO setup.

## Report a problem

Check the [user guide](docs/USAGE.md) and [known limitations](docs/KNOWN_LIMITATIONS.md), then use the [bug report form](https://github.com/TabGrinder/bruce-trigger/issues/new?template=bug_report.yml). Include the app version, phone and Android version, exact board, firmware version, active theme, USB setup and a minimal reproduction.

Describe expected and actual behavior separately. Review screenshots and diagnostic reports before attaching them; remove private device names, identifiers and unrelated content. Do not upload signing keys, passwords or private scripts. Sensitive reports follow [SECURITY.md](SECURITY.md).

## Propose and implement a change

1. Describe the problem and the expected behavior. For compatibility or protocol changes, link the relevant upstream source or documentation and identify its version or commit.
2. Keep the change limited to that problem. Preserve USB lifecycle handling, bounded parsing and command ordering unless the change explicitly addresses them.
3. Add a regression test when fixing behavior that can be tested meaningfully. Document checks that require a physical phone and LILYGO.
4. Update the English user documentation when controls, setup, compatibility or limits change.
5. Open a pull request with the result, validation performed and remaining limitations.

## Validate the change

Follow [BUILDING.md](docs/BUILDING.md) for toolchain setup and signing. For application changes, run the host and modeled USB suites:

```bash
python3 tools/test.py
python3 tools/test_usb.py
```

Build the APK and run the package check as documented there. Record physical results using [DEVICE_ACCEPTANCE.md](docs/DEVICE_ACCEPTANCE.md); modeled tests and instrumentation compilation do not establish hardware compatibility.

For documentation-only changes, check relative links, image paths, command examples and consistency with the app. A documentation edit alone does not require rebuilding the APK. Preserve the release APK checksum unless its bytes change.

## Attribution and release hygiene

Credit borrowed work and preserve component licenses, notices and provenance records. The combined distribution's [license](LICENSE), the original [MIT notice](LICENSE-MIT.txt) and [NOTICE.md](NOTICE.md) describe the current licensing; proposing a change does not remove those obligations.

Never commit `.signing/`, keystores, signing credentials, local SDK settings or generated build output. Source and release artifacts must remain traceable to the matching APK. The release version is **1.0**.

Treat contributors respectfully, use reproducible evidence when discussing technical claims and follow the [use disclaimer](DISCLAIMER.md).
