# F-Droid Reproducible Builds

This document explains how to build P.CASH Wallet for F-Droid with reproducible builds enabled.

## Overview

F-Droid requires [reproducible builds](https://f-droid.org/docs/Reproducible_Builds/) to verify that the published APK matches the source code. The main challenge is that baseline profiles (`assets/dexopt/baseline.prof`) are non-deterministic and differ between builds.

**Solution:** Pass `-Pfdroid=true` gradle property to disable baseline profile generation.

| Build Type | Baseline Profiles | Flag |
|------------|-------------------|------|
| Play Store | Enabled (better startup performance) | None |
| F-Droid | Disabled (reproducible) | `-Pfdroid=true` |

## F-Droid Metadata Configuration

The F-Droid build metadata is maintained in the [fdroiddata repository](https://gitlab.com/fdroid/fdroiddata).

**File:** `metadata/cash.p.terminal.yml`

### Option 1: Using `gradleProps` (Recommended)

```yaml
Builds:
  - versionName: 0.51.4
    versionCode: 207
    commit: 0.51.4
    subdir: app
    submodules: true
    gradle:
      - yes
    gradleProps:
      - fdroid=true
```

### Option 2: Using Custom Build Command

```yaml
Builds:
  - versionName: 0.51.4
    versionCode: 207
    commit: 0.51.4
    subdir: app
    submodules: true
    build: ../gradlew assembleRelease -Pfdroid=true
```

## Building Locally

### Method 1: Docker Scripts (Recommended)

The docker scripts in this repository automatically pass `-Pfdroid=true`:

```bash
# Navigate to docker folder
cd docker

# Build APK
./build-apk.sh [VERSION_TAG] [KEYSTORE_PATH] [KEYSTORE_PASSWORD]

# Example
./build-apk.sh 0.51.4 ~/keys/release.keystore mypassword

# Verify reproducibility against downloaded APK
./test.sh /path/to/downloaded.apk
```

### Method 2: F-Droid's Docker Image

Replicates F-Droid's exact build environment:

```bash
# Clone repository at specific version
git clone --branch 0.51.4 --depth 1 https://github.com/piratecash/pcash-wallet-android
cd pcash-wallet-android

# Build using F-Droid's buildserver image
docker run --rm \
    -v "$PWD":/repo \
    -w /repo \
    registry.gitlab.com/fdroid/fdroidserver:buildserver \
    bash -c './gradlew clean :app:assembleRelease -Pfdroid=true'

# APK output location
ls -la app/build/outputs/apk/release/
```

### Method 3: Manual Build

```bash
# Clone and checkout version
git clone https://github.com/piratecash/pcash-wallet-android
cd pcash-wallet-android
git checkout 0.51.4

# Build with F-Droid flag
./gradlew clean :app:assembleRelease -Pfdroid=true

# APK output
ls -la app/build/outputs/apk/release/
```

## Verification

### Verify Baseline Profile is Disabled

```bash
# Check APK contents
unzip -l app/build/outputs/apk/release/*.apk | grep baseline

# Expected results:
# With -Pfdroid=true    → No output (disabled)
# Without flag          → Shows assets/dexopt/baseline.prof
```

### Compare APKs

Using the test script:

```bash
cd docker
./test.sh /path/to/fdroid-downloaded.apk
```

Manual comparison with apktool:

```bash
# Extract both APKs
apktool d -o /tmp/local-build local-build.apk
apktool d -o /tmp/fdroid-build fdroid-build.apk

# Compare
diff -r /tmp/local-build /tmp/fdroid-build

# Visual comparison (requires meld)
meld /tmp/local-build /tmp/fdroid-build
```

**Expected:** No differences, or only signature-related differences.

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| `baseline.prof` in diff | Flag not passed | Add `-Pfdroid=true` to gradle command |
| `classes*.dex` differs | Baseline profile included | Verify `-Pfdroid=true` is set |
| Resource ordering differs | Non-deterministic processing | Use F-Droid's docker image |
| JDK version mismatch | Different Java versions | Match F-Droid's JDK (currently 21) |
| Gradle version mismatch | Wrapper not used | Always use `./gradlew` |

## How It Works

The `app/build.gradle` contains:

```gradle
// Disable baseline profile generation for F-Droid reproducible builds
// https://f-droid.org/docs/Reproducible_Builds/
// F-Droid builds should pass -Pfdroid=true to disable baseline profiles
if (project.hasProperty('fdroid')) {
    tasks.whenTaskAdded { task ->
        if (task.name.contains("ArtProfile")) {
            task.enabled = false
        }
    }
}
```

When `-Pfdroid=true` is passed, all tasks containing "ArtProfile" in their name are disabled, preventing baseline profile generation.

## References

- [F-Droid Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/)
- [fdroiddata Repository](https://gitlab.com/fdroid/fdroiddata)
- [F-Droid Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/)
- [WalletScrutiny](https://walletscrutiny.com/) - Wallet verification service
