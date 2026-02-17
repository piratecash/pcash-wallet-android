# Blockchain Status Screen — Design Document

**Jira:** MOBILE-512
**Date:** 2026-02-17
**Scope:** BTC first, generic module for future blockchains

## Context

The BTC blockchain settings screen currently shows blockchain status (sync state, peers, block height) inline at the bottom. This design moves it to a dedicated screen with copy/share functionality, per-blockchain log filtering, kit version display, and peer deduplication.

## Architecture

### New Module: `modules/blockchainstatus/`

```
modules/blockchainstatus/
├── BlockchainStatusProvider.kt        # Interface + BTC implementation
├── BlockchainStatusViewModel.kt       # Generic ViewModel (copy/share/logs)
└── BlockchainStatusScreen.kt          # Generic UI
```

### Navigation

`BtcBlockchainSettingsFragment` gets an internal NavHost (same pattern as `AboutFragment`):

- `"settings"` (start) — existing BtcBlockchainSettingsScreen, inline status replaced with "Blockchain Status" button
- `"status"` (composablePage) — new generic BlockchainStatusScreen

### Provider Interface

```kotlin
interface BlockchainStatusProvider {
    val blockchainName: String       // e.g., "Bitcoin"
    val kitVersion: String           // e.g., "d62eff5" from BuildConfig
    val logFilterTag: String         // e.g., "BTC" for AppLog filtering

    fun getStatusSections(): List<StatusSection>  // Per-address-type status
    fun getSharedSection(): StatusSection?         // Shared peers (not duplicated)
}

data class StatusSection(
    val title: String,
    val items: List<StatusItem>
)

sealed class StatusItem {
    data class KeyValue(val key: String, val value: String) : StatusItem()
    data class Nested(val title: String, val items: List<KeyValue>) : StatusItem()
}
```

### BTC Implementation

`BtcBlockchainStatusProvider` uses `BtcBlockchainSettingsService.getStatusInfo()` to get `List<Pair<String, Map<String, Any>>>` (one per active wallet/address type).

For each wallet:
- Non-map values (Synced Until, Sync State, Last Block Height, Derivation, etc.) → `StatusSection` with wallet badge as title
- Map values (Peer 1, Peer 2...) → collected only from first wallet into `getSharedSection()` titled "Bitcoin Peers"

This solves the peer dedup requirement: address-type-specific info appears per section, peers appear once.

### ViewModel

`BlockchainStatusViewModel` takes a `BlockchainStatusProvider`:
- Builds `uiState` with kit version, status sections, shared section, filtered logs
- Copy: formats everything as plain text string
- Share: writes text to `context.cacheDir/blockchain_status_report.txt`, returns FileProvider URI
- Uses `AppLog.getLog(tag)` for per-blockchain log filtering

### Screen Layout

1. AppBar: "{Blockchain} Status" + back button
2. Copy + Share buttons (same layout as AppStatusScreen)
3. "Kit Version: {hash}" block
4. Per-address-type status sections (BIP84, BIP49, etc.)
5. Shared peers section ("Bitcoin Peers")
6. Filtered app log section

## Log Filtering

### LogsDao

```kotlin
@Query("SELECT * FROM LogEntry WHERE actionId LIKE '%' || :tag || '%' ORDER BY id")
fun getByTag(tag: String): List<LogEntry>
```

### AppLog

```kotlin
fun getLog(tag: String): Map<String, Any> {
    // Same structure as getLog() but filters by tag
}
```

### Log Tag Mapping

Extension property on `BlockchainType`:
```kotlin
val BlockchainType.logTag: String
    get() = when (this) {
        BlockchainType.Bitcoin -> "BTC"
        BlockchainType.Litecoin -> "LTC"
        // ... extend as blockchains are added
        else -> name
    }
```

Matches existing logger names: `AppLogger("Send-BTC")`, `AppLogger("TokenBalanceViewModel-BTC")`.

## BuildConfig Kit Version

In `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "BITCOIN_KIT_VERSION", "\"${libs.versions.piratecash.bitcoin.get()}\"")
```

Reads `d62eff5` from `libs.versions.toml` at build time.

## Modified Existing Files

1. **`BtcBlockchainSettingsFragment.kt`** — add internal NavHost with `"settings"` and `"status"` routes
2. **`BtcBlockchainSettingsScreen.kt`** — remove `BlockchainStatusSection`, add "Blockchain Status" button (clickable cell with arrow icon)
3. **`BtcBlockchainSettingsViewModel.kt`** — remove `statusItems` from UI state (moved to new screen)
4. **`LogsDao.kt`** — add `getByTag(tag)` query
5. **`AppLog.kt`** — add `getLog(tag)` method
6. **`app/build.gradle.kts`** — add `buildConfigField` for kit version
7. **`ViewModelModule.kt`** — register `BlockchainStatusViewModel`
8. **String resources** (14 locales) — new strings

## New String Resources

| Key | English |
|-----|---------|
| `blockchain_status_title` | Blockchain Status |
| `blockchain_status_kit_version` | Kit Version |
| `blockchain_status_peers` | {name} Peers |

Reused: `Button_Copy`, `Button_Share`, `Hud_Text_Copied`

## DI Registration

```kotlin
// ViewModelModule.kt
viewModel { params -> BlockchainStatusViewModel(params.get(), get()) }
```

The provider is created in the composable and passed as a parameter, since it depends on the runtime `Blockchain` value.

## Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Module structure | Generic `BlockchainStatusProvider` interface | Reusable for all blockchains (item 5) |
| Navigation | Internal NavHost in BtcBlockchainSettingsFragment | Same pattern as AboutFragment |
| Kit version | BuildConfig field from libs.versions.toml | Type-safe, auto-updates |
| Log filtering | LogsDao query by actionId LIKE tag | Matches existing logger naming |
| Peers dedup | Per-type status + single shared peers section | Ticket requirement #7 |
