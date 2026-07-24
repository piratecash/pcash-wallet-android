package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import java.math.BigDecimal

/**
 * Pure, dependency-free diagnostic helpers for the Zcash "pending stuck" investigation.
 *
 * Everything here is `internal` and side-effect-free so the app module's unit tests can exercise
 * the coarsening directly. The only sensitive input is the balance, which never leaves this file
 * except as booleans and coarse magnitude buckets — raw Zatoshi and decimal amounts are never
 * rendered, so a logcat line can be shared safely.
 */

private const val DASH = "—"

/** Rendered when the SDK balance has not loaded yet — kept distinct from a genuine zero. */
private const val UNKNOWN = "unknown"

private val BUCKET_0_1 = BigDecimal("0.1")
private val BUCKET_1 = BigDecimal.ONE
private val BUCKET_10 = BigDecimal.TEN
private val BUCKET_100 = BigDecimal("100")

/** Three-state view of `ProcessorInfo.overallSyncRange` that never conflates `null` with empty. */
internal enum class SyncRangeState { Unknown, Empty, NonEmpty }

/**
 * Immutable diagnostic snapshot. Height/range/progress fields are nullable and copied as-is (no
 * rejection). The three balance amounts are also nullable — `null` means the SDK balance had not
 * loaded yet and is kept distinct from a genuine zero; they are only ever coarsened via [coarse].
 */
internal data class ZcashDiagSnapshot(
    val pool: String,
    val syncStateDiscriminator: String,
    val chainTipHeight: Long?,
    val fullyScannedHeight: Long?,
    val scanProgressPercent: Int?,
    val recoveryProgressPercent: Int?,
    val overallSyncRangeState: SyncRangeState,
    val overallSyncRangeStart: Long?,
    val overallSyncRangeEnd: Long?,
    val firstUnenhancedHeight: Long?,
    val available: BigDecimal?,
    val changePending: BigDecimal?,
    val valuePending: BigDecimal?,
) {
    val scanGap: Long?
        get() = if (chainTipHeight != null && fullyScannedHeight != null) {
            chainTipHeight - fullyScannedHeight
        } else {
            null
        }

    /** Single derivation of the coarse balance view reused by every field. */
    val coarse: CoarseBalance
        get() = coarseBalance(available, changePending, valuePending)
}

/**
 * Coarse, privacy-safe balance view — the single source of balance coarsening. Flags render
 * "true"/"false"/"unknown"; buckets render a magnitude bucket or "unknown".
 */
internal data class CoarseBalance(
    val available: String,
    val changePending: String,
    val valuePending: String,
    val valuePendingBucket: String,
    val changePendingBucket: String,
)

internal fun coarseBalance(
    available: BigDecimal?,
    changePending: BigDecimal?,
    valuePending: BigDecimal?,
): CoarseBalance = CoarseBalance(
    available = positiveFlag(available),
    changePending = positiveFlag(changePending),
    valuePending = positiveFlag(valuePending),
    valuePendingBucket = valuePending?.let(::magnitudeBucket) ?: UNKNOWN,
    changePendingBucket = changePending?.let(::magnitudeBucket) ?: UNKNOWN,
)

private fun positiveFlag(amount: BigDecimal?): String =
    amount?.let { (it.signum() > 0).toString() } ?: UNKNOWN

/**
 * Safe label for the exported "Sync State" line. [AdapterState.NotSynced]'s `toString()` embeds the
 * raw `Throwable.message`, which for a failed UFVK import contains the full viewing key — so a failed
 * state is rendered as its state plus exception class only, never the message or cause.
 */
internal fun safeSyncStateLabel(state: AdapterState): String = when (state) {
    is AdapterState.NotSynced -> "NotSynced ${state.error.javaClass.simpleName}"
    else -> state.toString()
}

/** Non-overlapping ZEC-amount bucketer; boundary values 0.1/1/10/100 fall into the upper interval. */
internal fun magnitudeBucket(amount: BigDecimal): String = when {
    amount.signum() <= 0 -> "0"
    amount < BUCKET_0_1 -> "<0.1"
    amount < BUCKET_1 -> "0.1-1"
    amount < BUCKET_10 -> "1-10"
    amount < BUCKET_100 -> "10-100"
    else -> ">=100"
}

/** Rendered key/values — the single source of coarsening and formatting for the diagnostic line. */
internal fun diagFields(s: ZcashDiagSnapshot): Map<String, String> {
    val coarse = s.coarse
    return linkedMapOf(
        "pool" to s.pool,
        "syncState" to s.syncStateDiscriminator,
        "chainTipHeight" to (s.chainTipHeight?.toString() ?: DASH),
        "fullyScannedHeight" to (s.fullyScannedHeight?.toString() ?: DASH),
        "scanGap" to (s.scanGap?.toString() ?: DASH),
        "scanProgress%" to (s.scanProgressPercent?.toString() ?: DASH),
        "recoveryProgress%" to (s.recoveryProgressPercent?.toString() ?: DASH),
        "overallSyncRange" to renderRange(s),
        "firstUnenhancedHeight" to (s.firstUnenhancedHeight?.toString() ?: DASH),
        "available>0" to coarse.available,
        "changePending>0" to coarse.changePending,
        "valuePending>0" to coarse.valuePending,
        "valuePendingBucket" to coarse.valuePendingBucket,
        "changePendingBucket" to coarse.changePendingBucket,
    )
}

/** Pool label shared by the adapter's `poolName` (DRY). */
internal fun poolLabel(spec: AddressSpecType?): String = when (spec) {
    AddressSpecType.Shielded -> "Shielded"
    AddressSpecType.Transparent -> "Transparent"
    AddressSpecType.Unified -> "Unified"
    null -> "Combined"
}

private fun renderRange(s: ZcashDiagSnapshot): String = when (s.overallSyncRangeState) {
    SyncRangeState.Unknown -> DASH
    SyncRangeState.Empty -> "empty"
    SyncRangeState.NonEmpty -> "${s.overallSyncRangeStart}..${s.overallSyncRangeEnd}"
}
