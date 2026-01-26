# F-Droid Build Instructions

## Building for F-Droid

When building this app for F-Droid, you must pass the `fdroid` property to Gradle to ensure reproducible builds:

```bash
./gradlew assembleRelease -Pfdroid=true
```

This property disables baseline profile (ART profile) generation, which is necessary for reproducible builds on F-Droid as these profiles contain timestamps and other non-deterministic data.

## Why is this needed?

Baseline profiles improve app performance but are generated with build-time specific data that makes builds non-reproducible. F-Droid requires reproducible builds to verify that published APKs match the source code.

Regular builds (Play Store, direct APK distribution) should NOT use the `-Pfdroid=true` flag to maintain optimal performance.
