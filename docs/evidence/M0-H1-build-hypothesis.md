# TMFM Runtime Safety — Build Hypothesis Log

Date: 2026-10-07
Base: production/tmfm-radio-complete

## H1 — rejected after two build attempts

- Scope: hardware-safe probe, direct MP3/AAC recording, truthful catalog visibility.
- Attempt 1 failed because RadioUi still referenced removed hardware enum values.
- Attempt 2 failed because RadioRepository contained an invalid filter expression and RecordingService used startId outside the onStartCommand scope.
- Evidence: GitHub Actions Run #76 and Run #87 build logs.
- H1 budget is exhausted. No more H1 builds are allowed.

## H2 — targeted compile correction

- Reason: both reported failures are deterministic source-level errors with exact file/line evidence; no architectural uncertainty remains for these two compiler errors.
- Expected result: the corrected commit should pass the Gradle compile stage, then proceed to the existing unit-test, lint, APK identity, and artifact gates.
- Falsification: any compile error in the corrected files or any later gate failure.
- Attempt budget: maximum 2; this commit is the first H2 correction/build attempt.

## H2 changes

1. Move station visibility predicate into a valid expression after haystack construction.
2. Pass Android Service startId into startRecording so stopSelf(startId) is scoped correctly.
3. No new feature, dependency, permission, reflection, MediaProjection, or hardware claim is introduced.

## M0 boundary

Physical FM remains NOT VERIFIED. No tuner session or tune callback evidence exists in this environment.


## H3 — exhausted
- Attempt 1: Mix FM candidate rejected by HEAD=405 and the GET fallback timed out.
- Attempt 2: the same Mix FM endpoint again timed out after GET fallback.
- Do not retry the H3 endpoints.

## H4 — alternate broadcaster/CDN route
- Reason: current web catalogues list alternate current HTTPS routes for several Egyptian stations, including Nogoum FM, Nile FM, Nagham FM, ON Sport FM, and Sha3by FM.
- Expected result: these alternate endpoints answer and provide decodable MP3 audio for 8 seconds with non-silent audio evidence in GitHub Actions.
- Falsification: any candidate fails HTTPS response, ffprobe audio detection, or 8-second ffmpeg/volumedetect.
- Attempt budget: maximum 2.
- No candidate is marked PLAYABLE in the source catalog before this evidence exists.

## H4 — attempt 1 exhausted
- Nogoum NRPstream endpoint returned HTTP 403 from GitHub Actions.

## H4 — attempt 2 / final endpoint set
- Current alternate direct HTTPS routes are tested for Radio 9090, Radio Hits, Mega FM, Nagham FM, ON Sport FM and Sha3by FM.
- The verifier checks every candidate and records every error rather than stopping at the first failure.
- H4 is exhausted after this attempt; no third H4 attempt will be made.