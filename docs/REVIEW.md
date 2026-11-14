# Bruce Trigger 1.0 — Release Review

This report describes the behavior and verification scope of release 1.0: input cancellation, serial framing, USB ownership, local data and distribution consistency. It is not a claim that all defects have been eliminated.

The exact delivered APK hash, signing certificate, build-input digest and test status are recorded in [VALIDATION.md](../VALIDATION.md).

## Reviewed behavior

| Area | Release behavior | Evidence |
| --- | --- | --- |
| Serial line boundaries | The parser reports one overflow and discards text until the next newline. Binary packets remain independently framed. Clearing a prompt fragment does not release the rejected line. | `LineBoundaryTest`: 61 assertions. The USB recovery suite verifies that an oversized line cannot manufacture boot evidence or clear the recovery fence. |
| Interrupted or obscured input | The activity cancels pending widget events, the game input queue and navigation that has not reached the USB worker. Focus loss uses the same cancellation path. | 19 controller assertions cover queued navigation, in-flight writes, metadata queries and interpreter uploads. Android tests exercise the Activity dispatch path for both obscured-touch flags; those tests compile but have not run. |
| Unreadable saved notes | A read failure retains the record and offers explicit deletion with a second confirmation. Other notes remain untouched. | Android tests cover the read error, retention before confirmation and confirmed deletion. They compile but have not run. |
| Validation reporting | The limitations page points to the exact validation record and distinguishes completed checks from pending runtime checks. | Documentation and archive consistency checks. |

Input cancellation cannot undo a command already delivered to the device. It does not replay a write or cancel the metadata query or interpreter upload needed to manage the current connection.

## Areas inspected

- Android lifecycle and interaction: foreground transitions, focus loss, obscured events, navigation, asynchronous note callbacks and document export.
- USB transport: permission callbacks, device selection, stale connection ownership, single-packet reads, bounded receive queues, partial writes and capture cleanup.
- Protocol and rendering: line/frame boundaries, command allowlists, menu snapshots, theme paths, allocation limits, drawing validation, recovery checksums and game input freshness.
- Stored data: retained note format, bounded reads, AtomicFile recovery, path containment, explicit deletion and bounded export work.
- Distribution: version 1.0 / code 100, original signing identity, source-to-APK correspondence, Android manifest, exclusions for signing material, credits and release documentation.

Modeled regression tests cover capture, theme transfer, the game bridge and paced writes. They passed; no physical performance measurement is inferred from those tests.

## Verification completed

- Release APK compiled with JDK 17, Android SDK Platform 35 and Build Tools 35.0.0.
- `tools/test.py` passed, including serial overflow boundaries, protocol fragmentation, malformed frames, rendering records, rate limits and input tests.
- `tools/test_usb.py` passed, including cancellation and recovery cases, modeled USB loss, theme transfers, game framing, corruption, handover, script selection and input freshness.
- APK ZIP integrity, manifest policy, signature schemes v2/v3 and embedded build-input inventory passed.
- Android instrumentation compiled and signed, including interaction and unreadable-note scenarios.
- All 27 Python build and audit scripts passed syntax parsing.
- Public archive contents were checked against the embedded source inventory and for excluded keys, credentials, build output and Git history.

The test logs accompanying the source are in `docs/validation/`. Build and package records describe this artifact, not an independent reproducible-build attestation.

## Pending execution

Android runtime and visual checks remain pending because the isolated emulator could not start. No physical phone or LILYGO was attached. The JavaScript engine harness was not executed in this review; no MicroQuickJS executable was available in the environment.

Before publication, install the delivered APK on the intended phone and LILYGO and verify:

1. USB permission, CONNECT, DISCONNECT, reconnect and recovery after cable removal.
2. Remote and Device navigation, Diagnostics, Back navigation and activity recreation.
3. Rotary and game controls, held-button release, interruptions and returning from another app.
4. Default and custom themes, supported Files scripts and repeated script entry/exit.
5. Saved-note reading, export and confirmed deletion, including an unreadable fixture on a disposable test installation.

No measured FPS, physical input-latency, long-duration hardware stability or completed Android visual review is claimed.

## Platform reference

The input cleanup uses Android's [View.cancelPendingInputEvents](https://developer.android.com/reference/android/view/View#cancelPendingInputEvents()) API. Custom widgets implement `onCancelPendingInputEvents`; the activity invokes cancellation before rejecting covered touch events.
