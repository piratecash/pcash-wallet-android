package cash.p.terminal.premium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import cash.p.terminal.premium.domain.usecase.PremiumType

/**
 * Persisted per-account premium result so the wallet-list screens can show the last known premium type
 * immediately on a cold start, before the background re-scan finishes.
 *
 * @param checkedAtEpochMillis wall-clock ([System.currentTimeMillis]) of the last confirmation. Wall-clock
 * (not [android.os.SystemClock.elapsedRealtimeNanos]) is required because this value must stay comparable
 * across process restarts and device reboots.
 */
@Entity(tableName = "account_premium_cache")
internal data class AccountPremiumCacheEntity(
    @PrimaryKey
    val accountId: String,
    val premiumType: PremiumType,
    val checkedAtEpochMillis: Long
)
