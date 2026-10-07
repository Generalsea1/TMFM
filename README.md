# TMFM Radio

Production-oriented Android hybrid radio application built in Kotlin + Jetpack Compose + Media3.

## Current production architecture

- Internet Radio is the primary radio path for ordinary Android phones.
- Hardware FM/AM is optional and exposed only when Android reports an actually accessible Broadcast Radio tuner.
- Recording uses Android playback capture on API 29+ with explicit user consent and stores recordings in an indexed Room library.
- Station discovery combines verified TMFM/Supabase stations with Radio Browser for broad global discovery.
- Favorites are persisted locally and legacy DEBA favorites are read for continuity.
- Background playback is provided by Media3 MediaSessionService.

## Hardware FM/AM truth

TMFM never fakes FM/AM availability, frequency scans, signal strength, RDS or seek results.
`HardwareRadioProbe` returns a real capability state. On ordinary devices where the Broadcast Radio stack is unavailable or unauthorized, TMFM gracefully remains an Internet Radio application.

A successful APK build is not evidence of FM reception. Real hardware verification requires a physical device whose OEM/system image exposes an accessible tuner.

## Recording

On Android 10 (API 29)+, TMFM can request explicit MediaProjection consent and capture TMFM's own media playback through Android's playback-capture API.

Recordings are saved as:

`[StationName] — [YYYY-MM-DD] — [HH-mm-ss].m4a`

Each completed recording is indexed in Room with station, timestamp, duration, size, path and source metadata. The application provides Play, Share and Delete.

When Android or the user terminates the MediaProjection session, TMFM stops the recording cleanly.

## Station data

### Supabase

Supabase remains the curated source for verified stations, online stations and TMFM-controlled metadata.
The public client uses the Supabase publishable key only. The current anonymous policy exposes only rows where is_verified=true and is_online=true.

### Radio Browser

Radio Browser provides broad discovery without pretending that those stations are TMFM-verified.

TMFM dynamically discovers API mirrors, keeps HTTPS fallbacks, uses stationuuid as the stable Radio Browser identifier, filters broken stations, searches station name/language/tag/country, and reports a station click when playback starts.

Radio Browser entries are marked as radio_browser and are not displayed as TMFM-verified.

## Search and filters

The UI supports station search, country filtering, favorites, metadata matching on loaded results, and global Radio Browser discovery.

## Build stack

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Kotlin / Compose compiler plugin 2.4.10
- Compose UI 1.10.5
- Material 3 1.4.0
- Media3 1.9.4
- Room 2.8.5
- KSP 2.3.12
- AndroidX Core 1.17.0
- Activity Compose 1.12.4
- Lifecycle 2.9.4
- minSdk 26
- compileSdk 36
- targetSdk 36

Dependencies are pinned.

## Verification policy

Production acceptance is not based on a successful Gradle build alone.

Required evidence:

IMPLEMENTED → BUILT → INSTALLED → TESTED → VERIFIED → EVIDENCE

The CI pipeline verifies compilation, unit tests and lint and publishes the APK artifact. Physical-device validation is still required for runtime audio capture and any Hardware FM/AM capability.