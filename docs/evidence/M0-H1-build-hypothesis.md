# TMFM Runtime Safety — Build Hypothesis Log

Date: 2026-10-07
Base: production/tmfm-radio-complete

## Hypothesis H1 — buildable Internet Radio baseline

- Reason: the current project is already Kotlin + Jetpack Compose + Media3 and contains a working playback service path.
- Expected result: after removing forbidden runtime mechanisms and correcting compile/runtime contracts, the debug APK should assemble, unit tests should execute, and lint should remain strict.
- Falsification: any Gradle compilation error, unit-test failure, lint failure, or APK identity failure in CI.
- Attempt budget: maximum 2.

## H1 changes in scope

1. Hardware FM remains honest and unverified unless a real tuner session is proven.
2. Remove reflection from Hardware FM probing.
3. Remove MediaProjection from Internet Radio recording; use a separate direct stream connection for supported MP3/AAC progressive streams.
4. Hide Radio Browser discovery records from the normal playable catalog until M2 verification exists.
5. Keep HLS recording disabled until segment assembly is implemented and verified.

No claim of physical FM support is made by this change.
