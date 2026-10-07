# TMFM — Radio

Android radio application built in Kotlin + Jetpack Compose + Media3.

## Implemented now

- Internet Radio playback using Media3/ExoPlayer.
- Background playback through MediaSessionService.
- Android media notification and lock-screen controls supplied by Media3.
- Favorites persisted locally.
- Search by station, country, city, frequency, language and category.
- Country filtering.
- Sleep timer.
- Supabase-hosted station catalog with RLS.
- Device-side Broadcast Radio capability probe.
- GitHub Actions build pipeline producing a real APK artifact.

## Hardware FM/AM truth

Android's Broadcast Radio stack only works when the device/OEM exposes a compatible tuner stack. The system APIs are protected/system-oriented on many consumer phones. DEBA Radio therefore probes actual system availability and never fakes frequency scanning, signal strength or reception.

A successful application build does NOT prove FM reception on a physical phone. Real FM verification requires a compatible physical device that actually exposes an accessible tuner.

## Station-data policy

The app reads only records where is_verified=true and is_online=true. The catalog is designed to be expanded and corrected without rebuilding the APK.

The current seed contains a small verified set. It does not claim all Egyptian stations or global completeness. Unknown, broken, or unverified stations are intentionally excluded from the public app response.

## Supabase

Project: sjutgrwdfozrlyiaebai

Table: public.radio_stations

RLS is enabled. Anonymous clients can only SELECT records that are both verified and online. Anonymous clients have SELECT access only; INSERT, UPDATE and DELETE are revoked.

Client code uses the publishable Supabase key only.

## Build stack

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Kotlin/Compose compiler plugin 2.4.10
- Compose UI 1.10.5
- Material 3 1.4.0
- Media3 1.9.4
- AndroidX Core 1.17.0
- Activity Compose 1.12.4
- Lifecycle 2.9.4
- minSdk 26
- compileSdk 36
- targetSdk 36

Versions are pinned; the build does not use dynamic dependency versions.

## CI deliverable

.github/workflows/build.yml builds app-debug.apk and uploads it as the deba-radio-debug-apk GitHub Actions artifact while printing SHA-256.

The CI build verifies compilation and unit tests. It cannot perform real FM hardware verification on a physical phone.
