# TMFM

Professional hybrid radio application for Android.

## Product identity

The installed application name is **TMFM**. The launcher uses a vintage tabletop-radio mark with modern adaptive-icon support.

## Radio architecture

TMFM separates:
- hardware Broadcast Radio diagnostics/integration;
- Internet Radio playback through Media3;
- local recording library.

The application never fabricates FM frequencies, signal strength, scan results, RDS or tuner availability.

Android exposes Broadcast Radio tuner control as a system-oriented API protected by ACCESS_BROADCAST_RADIO. A normal third-party APK cannot grant itself this privilege. A real FM scan therefore requires a compatible physical device and legitimate OEM/system-level integration.

## Egyptian catalog

Egypt is the first catalog and is bundled for offline availability. The catalog distinguishes verified entries from frequency references and does not invent stream URLs.

The current baseline covers the public Egyptian FM frequencies that were found in current radio-directory references and official broadcaster pages. Frequencies can be region-specific and are labeled accordingly.

## Content policy

TMFM has a deterministic catalog policy that blocks Islamic/Quran religious stations from:
- the bundled catalog;
- Supabase catalog results;
- global Radio Browser discovery;
- search results;
- recommendations and favorites.

The physical RF spectrum itself cannot be altered by an application. The app does not identify or promote a station when metadata is unavailable.

## Recording

For supported Internet Radio playback on Android 10+, TMFM uses Android playback capture with explicit user consent and encodes AAC audio into M4A files. It does not substitute microphone audio for the stream.

Hardware-FM recording is not claimed unless the target OEM/device legitimately exposes the tuner audio source to the application.

## Verification policy

IMPLEMENTED -> BUILT -> INSTALLED -> TESTED -> VERIFIED -> EVIDENCE

A successful Gradle build does not prove physical FM capability.
