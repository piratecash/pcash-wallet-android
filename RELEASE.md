# P.CASH Terminal-Wallet Release

This document describes the release process for `P.CASH` app.

### 1. Prepare dependent libraries

#### 1.1. Update Checkpoints

* `BitcoinKit`
* `BitcoinCashKit`
* `LitecoinKit`
* `DashKit`

#### 1.2. Update coins dump in `MarketKit`

Initial coins dump `json` file should be updated to latest state of backend.

### 2. Update URL for Guides and FAQ

* In case there are changes in Guides and FAQ repositories, update their URL's by new tags.

### 3. Update README file

* Check and update 'Supported Android Versions' section if needed

### 4. Prepare Release Version

* Make sure everything intended for the release is merged into `master` via feature-branch pull requests.
* Increment version code.
* Increase version name.

### 5. Set repository tag

* Create tag for current version.

### 6. Build apk file

Build the release APK from the tagged commit:

```
./gradlew clean :app:assembleRelease
```

For F-Droid reproducible builds (including building in F-Droid's exact environment) see [FDROID.md](FDROID.md).

### 7. Upload Build to Google Play

* Upload apk to `Google Play Console`.

### 8. Create Release in GitHub Repository

* Create new `Release`, add changelog and upload apk file. Make note in changelog if the 'Supported Android Versions' was changed
* Sign the APK artifacts and attach the checksum and signature files (`p.cash-<version>.apk.sha256`, `.asc`) under the 'Assets' section — see [RELEASE_SIGNING_AND_VERIFICATION.md](RELEASE_SIGNING_AND_VERIFICATION.md) and `tools/sign-release-apk.sh`.

### 9. Make sure P.CASH Wallet is 'Reproducible'

* After apk is uploaded to Google Play make sure that new version of P.CASH Terminal-Wallet is 'Reproducible' in WalletScrutiny.

### 10. F-Droid Release

* See [FDROID.md](FDROID.md) for F-Droid reproducible build instructions and metadata configuration.