> **Archive warning:** The results below document the **1.0** build and should not be used to claim any 1.0.1 APK has been compiled or tested. For the current 1.0.1 release see [docs/VALIDATION_v1.0.1.md](docs/VALIDATION_v1.0.1.md).

# Bruce Trigger 1.0 — Validation

## Delivered application

- Application ID: `com.tabgrinder.brucetrigger`.
- Android version name / code: **1.0 / 100**.
- APK SHA-256: `3d04a453b60c1875d029d2e3ed11d2bf304eb322532b0151db58abd3a7f0c53a`.
- Signing certificate SHA-256: `041ca9035b9e807025a0b12c3f01229800448267d1f17a46ec517cdf4514440b`.
- Build-input digest: `922bae32e60c02607fff7bf63f6b2c54bdd5691914df11d8fafed32d8aea7c63`.

## Checks completed for this artifact

| Check | Result | Scope |
| --- | --- | --- |
| Release compilation | PASS | JDK 17 and Android SDK Platform 35 / Build Tools 35.0.0 |
| Host regression suite (`tools/test.py`) | PASS | Parsing, serial overflow boundaries, drawing records, rate budgets, rotary input and transport ownership |
| USB regression suite (`tools/test_usb.py`) | PASS | USB/controller doubles, themes, scripts, lifecycle, scoped input cancellation, corruption and recovery |
| APK integrity and signatures | PASS | ZIP integrity and APK signature schemes v2 and v3 |
| Build-input correspondence | PASS | Packaged source hashes match the distributed build inputs |
| Manifest and distribution checks | PASS | Non-debuggable; no declared permissions; backup exclusions; one exported launcher; signing material excluded |
| Android instrumentation compilation | PASS | Integration, Activity-level obscured touches, held-input cancellation, unreadable-note deletion and navigation checks compile and sign |
| Android runtime and visual checks | NOT COMPLETED | Runtime execution remains pending; the test emulator failed during startup |
| Physical phone and LILYGO | NOT PERFORMED | No physical device attached to this environment |

The complete host and USB suites passed. Targeted regression probes were also run. Android compilation is not an Android runtime pass. Test results from another APK are not used as evidence for this artifact.

## Application review

The primary navigation contains Remote and Device. Diagnostics is reached from Settings or Device, with a return action to its originating screen. Device queries retain the capture-state guards and confirmation prompts. Device reports remain local views of recorded state.

Existing saved notes retain read, export and delete access under Settings when records exist. New-note creation is absent. Hardware controls that have no implementation appear only in compatibility guidance. Credits and license notices remain accessible from Help & about.

The Android checks cover these paths, Activity-level input rejection, unreadable-note deletion, saved-note recovery, delayed reads after navigation, capture guards and activity recreation. They compile, but their execution remains pending because of the emulator startup failure.

Production code has no note-creation API or wireless-discovery callback. Note creation exists only in the test fixtures. Saved-note dialogs discard asynchronous results when their originating screen is no longer active; storage queue saturation displays a retry message. List rendering reuses one date formatter per page, and diagnostics takes one output snapshot per render.

Oversized serial lines are discarded through the next newline so a rejected suffix cannot become a prompt or reboot message. Activity-level input cancellation reaches pending widget events and unsent navigation while preserving metadata queries and interpreter uploads. Unreadable notes remain stored until the user explicitly confirms deletion.

See [the release review](docs/REVIEW.md) for the reviewed behavior, regression evidence and remaining execution work. Tests cover capture, theme transfer, the game bridge and input pacing. These checks do not establish physical performance measurements.

## Remaining validation

Before describing this release as verified on hardware, install it on the target phone and LILYGO and check Remote/Device navigation, Settings → Diagnostics, Back navigation, custom/default themes, disconnect/reconnect, leaving the foreground and repeated script launches.

Use the [device acceptance guide](docs/DEVICE_ACCEPTANCE.md) to record the exact setup and results. The [screenshot gallery](docs/SCREENSHOTS.md) illustrates the interface; its images are not test evidence tied to this APK's checksum.

No phone FPS, physical input-latency or sustained hardware-stability claim is made. The mirror supports drawing operations rather than universal framebuffer capture. The default-theme recommendation records the maintainer's observed behavior.
