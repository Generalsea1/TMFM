# TMFM stage report

| Phase | Hypothesis | Implemented | Evidence | Status |
|---|---|---|---|---|
| M0 | Target phone exposes FM control to TMFM | Device facts recorded; no tuner callback/ADB evidence available here | docs/hardware-evidence/redmi-10.md | NOT VERIFIED |
| M1 | Frequency evidence and stream evidence are separate | Model + classification + SQL migration | RadioStation.kt, RadioAvailability.kt, docs/supabase/m1_radio_stations_migration.sql | CODE READY / DB MIGRATION PENDING |
| M2 | Streams can be validated outside the APK | Scheduled verifier + ffprobe/ffmpeg + Supabase update | .github/workflows/m2-stream-verification.yml, tools/verify_streams.py | BLOCKED until GitHub Secrets and M1 migration |
| M3 | Internet playback recovers safely | Media3 focus/noisy/bounded retry | PlaybackService.kt | CODE READY / DEVICE NOT VERIFIED |
| M4 | Recording copies the source stream | MP3/AAC direct connection + ICY stripping + HLS segment recording | InternetStreamRecorder.kt, RecordingService.kt | CODE READY / DEVICE NOT VERIFIED |
| M5 | UI shows only truthful actions | state-aware cards + OEM FM launcher | RadioUi.kt, MainActivity.kt | CODE READY / DEVICE NOT VERIFIED |
| M6 | Egyptian catalog has explicit evidence states | existing frequency records classified; no stream invented | BundledCatalog.kt | PARTIAL |
| M7 | USB SDR | not started | user approval required | NOT STARTED |
| M8 | Release gate | CI + physical matrix + recording evidence | build workflow + device test plans | NOT VERIFIED |

## Open blockers
1. M0 physical evidence.
2. Manual Supabase M1 SQL execution.
3. GitHub Secrets for M2: SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY.
4. Physical Android tests for M3/M4/M5/M8.
5. M7 is opt-in only.
