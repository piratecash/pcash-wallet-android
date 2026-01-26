# F-Droid Build Instructions

## F-Droid Metadata Configuration

This app requires the `fdroid` gradle property to be set for reproducible builds. The F-Droid metadata file in the [fdroiddata repository](https://gitlab.com/fdroid/fdroiddata) needs to include this property.

### Required Metadata Changes

In the F-Droid metadata file (`metadata/cash.p.terminal.yml`), add the gradle property to each build entry:

```yaml
Builds:
  - versionCode: 207
    versionName: 0.51.4
    commit: <commit-hash>
    subdir: app
    gradle:
      - yes
    gradleprops:
      - fdroid=true
```

Alternatively, you can pass it directly in the gradle array:

```yaml
    gradle:
      - -Pfdroid=true
```

### Manual Building for F-Droid

If building manually, pass the `fdroid` property to Gradle:

```bash
./gradlew assembleRelease -Pfdroid=true
```

## Why is this needed?

Baseline profiles (ART profiles) improve app performance but are generated with build-time specific data that makes builds non-reproducible. F-Droid requires reproducible builds to verify that published APKs match the source code.

This gradle property conditionally disables baseline profile generation only for F-Droid builds, while regular builds (Play Store, direct APK distribution) keep baseline profiles enabled for optimal performance.
