# Release Signing & Verification (Android APK)

This document explains how PirateCash Android Wallet releases are named, signed with GPG and verified by users.

## Files published in a release

For each release we publish the following artifacts:

- `p.cash-<version>.apk` - release APK
- `p.cash-<version>.apk.asc` - detached GPG signature of the APK (ASCII armored)
- `p.cash-<version>.apk.sha256` - SHA-256 checksum file for the APK
- `p.cash-<version>.apk.sha256.asc` - detached GPG signature of the checksum file
- `piratecash-release-public-key.asc` - maintainer public GPG key (for verification)

Example (v0.55.0):

- `p.cash-0.55.0.apk`
- `p.cash-0.55.0.apk.asc`
- `p.cash-0.55.0.apk.sha256`
- `p.cash-0.55.0.apk.sha256.asc`

## Maintainer public key

**GPG Key ID:** `A6F0CB1BB25FFE99`  
**Fingerprint:** `8A47 C2AB ED28 39E6 71B5  0620 A6F0 CB1B B25F FE99`

### Export the public key (maintainers)

```bash
gpg --armor --export A6F0CB1BB25FFE99 > piratecash-release-public-key.asc
gpg --show-keys piratecash-release-public-key.asc
```

### Publish the public key on GitHub

Commit the file to the repository (recommended path):

- `security/piratecash-release-public-key.asc`

Or attach it to every GitHub Release as an asset.

> Note: Other contributors building the project do **not** need your GPG key.  
> Only the official release maintainer/CI signs the final release artifacts.

## APK naming (Gradle)

To ensure predictable file names (independent of signing), the release APK can be renamed during build:

```gradle
android {
    applicationVariants.all { variant ->
        if (variant.buildType.name == "release") {
            variant.outputs.all { output ->
                def versionName = variant.versionName
                def newApkName = "p.cash-${versionName}.apk"
                output.outputFileName = newApkName
            }
        }
    }
}
```

This only changes the output filename. It does not require GPG and is safe for other builders.

## Signing a release (maintainers)

> Signing is performed on the release machine or in CI where the maintainer GPG key is available.

Use [`tools/sign-release-apk.sh`](tools/sign-release-apk.sh) to generate all
release signing artifacts for the version currently defined in `app/build.gradle`.

### Script usage

Build the release APK first:

```bash
./gradlew :app:assembleRelease
```

Then run the signing script from the project root:

```bash
tools/sign-release-apk.sh
```

The script reads `versionName` from `app/build.gradle`, expects the release APK
at `app/build/outputs/apk/release/p.cash-<version>.apk`, then creates:

- `p.cash-<version>.apk.asc`
- `p.cash-<version>.apk.sha256`
- `p.cash-<version>.apk.sha256.asc`

For the current release, `versionName` is `0.55.0`, so the expected APK is:

```bash
app/build/outputs/apk/release/p.cash-0.55.0.apk
```

Useful options:

```bash
# Print the commands without creating files
tools/sign-release-apk.sh --dry-run

# Use a different GPG key
tools/sign-release-apk.sh --key-id A6F0CB1BB25FFE99
GPG_KEY_ID=A6F0CB1BB25FFE99 tools/sign-release-apk.sh

# Read the APK from a different directory
tools/sign-release-apk.sh --apk-dir /path/to/release

# Show script help
tools/sign-release-apk.sh --help
```

Manual equivalent for release `0.55.0`:

```bash
# 1) Sign APK (detached signature)
gpg --local-user A6F0CB1BB25FFE99 \
  --armor --detach-sign \
  --output app/build/outputs/apk/release/p.cash-0.55.0.apk.asc \
  app/build/outputs/apk/release/p.cash-0.55.0.apk

# 2) Create SHA-256 checksum file
cd app/build/outputs/apk/release
shasum -a 256 p.cash-0.55.0.apk > p.cash-0.55.0.apk.sha256

# 3) Sign checksum file (detached signature)
gpg --local-user A6F0CB1BB25FFE99 \
  --armor --detach-sign \
  --output p.cash-0.55.0.apk.sha256.asc \
  p.cash-0.55.0.apk.sha256
```

## How to verify a release (users)

### 1) Download artifacts

Download the APK and its signature files from the GitHub Release page:

- `p.cash-<version>.apk`
- `p.cash-<version>.apk.asc`
- `p.cash-<version>.apk.sha256`
- `p.cash-<version>.apk.sha256.asc`
- `piratecash-release-public-key.asc` (or get it from the repo path above)

### 2) Import the public key

```bash
gpg --import piratecash-release-public-key.asc
gpg --fingerprint A6F0CB1BB25FFE99
```

Expected fingerprint:

```
8A47 C2AB ED28 39E6 71B5  0620 A6F0 CB1B B25F FE99
```

### 3) Verify the APK signature

```bash
gpg --verify p.cash-0.55.0.apk.asc p.cash-0.55.0.apk
```

You should see a message like:

```
Good signature from "Dmitriy Korniychuk <dmitriy@korniychuk.org.ua>"
```

### 4) Verify the SHA-256 checksum (optional, recommended)

```bash
gpg --verify p.cash-0.55.0.apk.sha256.asc p.cash-0.55.0.apk.sha256
shasum -a 256 -c p.cash-0.55.0.apk.sha256
```

Expected output:

```
p.cash-0.55.0.apk: OK
```

If these checks pass, the APK is authentic and has not been tampered with.

## FAQ

### Do contributors need the maintainer GPG key?
No. Contributors can build the APK normally. Only the official release process signs artifacts.

### Why sign both the APK and the checksum?
Signing the checksum makes it easy to verify integrity even when the APK is mirrored or moved. Signing the APK provides a direct authenticity check.

### Windows users
If you verify on Windows, install Gpg4win and run the same `gpg --import` / `gpg --verify` commands in PowerShell.
