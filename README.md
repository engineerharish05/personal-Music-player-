# ISAI Music

ISAI is an Android music-player app scaffold with a dark interface, local audio library scanning, Media3 playback, background playback through a MediaSessionService, and HTTPS direct-audio URL playback.

## Build

- JDK 17
- Gradle 8.13
- Android SDK platform 36

Run:

```sh
gradle assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Current scope

- Local audio discovery through MediaStore
- Queue-based local playback using AndroidX Media3
- Background media session and system media controls
- Direct HTTPS audio stream URL playback
- GitHub Actions debug APK artifact

## Important limitations

YouTube Music search/stream resolution, lyrics, equalizer UI, saved playlists, shuffle/repeat controls, and release signing are not implemented yet. Direct URL playback is not the same as YouTube Music integration. The existing repository contains SimpMusic core modules but no root Gradle project or app module; this initial app module is intentionally standalone so it can be built without modifying those core modules. Integration with the existing scraper/core requires a follow-up dependency and API compatibility pass.
