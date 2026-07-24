package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal

class ZcashDiagTest {

    // ---- magnitudeBucket ----

    @Test
    fun magnitudeBucket_boundaries_fallIntoUpperInterval() {
        assertEquals("0", magnitudeBucket(BigDecimal.ZERO))
        assertEquals("<0.1", magnitudeBucket(BigDecimal("0.05")))
        assertEquals("0.1-1", magnitudeBucket(BigDecimal("0.1")))
        assertEquals("0.1-1", magnitudeBucket(BigDecimal("0.5")))
        assertEquals("1-10", magnitudeBucket(BigDecimal.ONE))
        assertEquals("1-10", magnitudeBucket(BigDecimal("4.065")))
        assertEquals("10-100", magnitudeBucket(BigDecimal.TEN))
        assertEquals(">=100", magnitudeBucket(BigDecimal("100")))
        assertEquals(">=100", magnitudeBucket(BigDecimal("1000")))
    }

    @Test
    fun magnitudeBucket_scaleVariants_compareByValue() {
        assertEquals("0.1-1", magnitudeBucket(BigDecimal("0.10")))
        assertEquals("1-10", magnitudeBucket(BigDecimal("1.00")))
    }

    // ---- diagFields ----

    @Test
    fun diagFields_nullHeightsAndRange_renderDash() {
        val fields = diagFields(
            snapshot(
                chainTipHeight = null,
                fullyScannedHeight = null,
                recoveryProgressPercent = null,
                firstUnenhancedHeight = null,
                overallSyncRangeState = SyncRangeState.Unknown,
            )
        )
        assertEquals("—", fields["chainTipHeight"])
        assertEquals("—", fields["fullyScannedHeight"])
        assertEquals("—", fields["scanGap"])
        assertEquals("—", fields["recoveryProgress%"])
        assertEquals("—", fields["firstUnenhancedHeight"])
        assertEquals("—", fields["overallSyncRange"])
    }

    @Test
    fun diagFields_overallSyncRange_rendersThreeStates() {
        assertEquals("—", diagFields(snapshot(overallSyncRangeState = SyncRangeState.Unknown))["overallSyncRange"])
        assertEquals("empty", diagFields(snapshot(overallSyncRangeState = SyncRangeState.Empty))["overallSyncRange"])
        assertEquals(
            "2000000..2500000",
            diagFields(
                snapshot(
                    overallSyncRangeState = SyncRangeState.NonEmpty,
                    overallSyncRangeStart = 2_000_000,
                    overallSyncRangeEnd = 2_500_000,
                )
            )["overallSyncRange"]
        )
    }

    @Test
    fun diagFields_valuePendingBucket_reflectsValuePendingNotChangePending() {
        val fields = diagFields(
            snapshot(
                changePending = BigDecimal("250"),
                valuePending = BigDecimal("4.065"),
            )
        )
        assertEquals("1-10", fields["valuePendingBucket"])
        assertEquals(">=100", fields["changePendingBucket"])
        assertEquals("true", fields["valuePending>0"])
        assertEquals("true", fields["changePending>0"])
    }

    // ---- poolLabel ----

    @Test
    fun poolLabel_mapsEachSpecAndNull() {
        assertEquals("Shielded", poolLabel(AddressSpecType.Shielded))
        assertEquals("Transparent", poolLabel(AddressSpecType.Transparent))
        assertEquals("Unified", poolLabel(AddressSpecType.Unified))
        assertEquals("Combined", poolLabel(null))
    }

    // ---- privacy ----

    @Test
    fun diagFields_neverContainsExactAmount() {
        val exactValuePending = BigDecimal("4.06512345")
        val exactChangePending = BigDecimal("123.98765432")
        val exactAvailable = BigDecimal("7.55555555")
        val rendered = diagFields(
            snapshot(
                available = exactAvailable,
                changePending = exactChangePending,
                valuePending = exactValuePending,
            )
        ).toString()

        assertFalse(rendered.contains("4.06512345"))
        assertFalse(rendered.contains("123.98765432"))
        assertFalse(rendered.contains("7.55555555"))
        // Also not the raw Zatoshi integer forms.
        assertFalse(rendered.contains("406512345"))
        assertFalse(rendered.contains("12398765432"))
    }

    // ---- unknown balance (not-yet-loaded) is distinct from zero ----

    @Test
    fun diagFields_unknownBalance_rendersUnknownNotFalseOrZero() {
        val fields = diagFields(snapshot(available = null, changePending = null, valuePending = null))
        assertEquals("unknown", fields["available>0"])
        assertEquals("unknown", fields["changePending>0"])
        assertEquals("unknown", fields["valuePending>0"])
        assertEquals("unknown", fields["valuePendingBucket"])
        assertEquals("unknown", fields["changePendingBucket"])
    }

    @Test
    fun diagFields_zeroBalance_rendersFalseAndZeroBucket() {
        val fields = diagFields(
            snapshot(available = BigDecimal.ZERO, changePending = BigDecimal.ZERO, valuePending = BigDecimal.ZERO)
        )
        assertEquals("false", fields["available>0"])
        assertEquals("false", fields["valuePending>0"])
        assertEquals("0", fields["valuePendingBucket"])
        assertEquals("0", fields["changePendingBucket"])
    }

    // ---- safeSyncStateLabel (never exports a raw Throwable message) ----

    @Test
    fun safeSyncStateLabel_notSyncedWithSecretMessage_omitsMessageAndCause() {
        val secret = "uview1exampleviewingkeymaterialthatmustneverleak"
        val state = AdapterState.NotSynced(
            RuntimeException("Value \"$secret\" did not decode as a valid UFVK")
        )
        val label = safeSyncStateLabel(state)
        assertEquals("NotSynced RuntimeException", label)
        assertFalse(label.contains(secret))
    }

    @Test
    fun safeSyncStateLabel_nonFailedStates_renderPlainToString() {
        assertEquals("Synced", safeSyncStateLabel(AdapterState.Synced))
        assertEquals("Connecting", safeSyncStateLabel(AdapterState.Connecting))
    }

    @Suppress("LongParameterList")
    private fun snapshot(
        pool: String = "Unified",
        syncStateDiscriminator: String = "Syncing",
        chainTipHeight: Long? = 2_400_000,
        fullyScannedHeight: Long? = 2_399_900,
        scanProgressPercent: Int? = 90,
        recoveryProgressPercent: Int? = null,
        overallSyncRangeState: SyncRangeState = SyncRangeState.NonEmpty,
        overallSyncRangeStart: Long? = 2_000_000,
        overallSyncRangeEnd: Long? = 2_400_000,
        firstUnenhancedHeight: Long? = 2_399_950,
        available: BigDecimal? = BigDecimal("1.0"),
        changePending: BigDecimal? = BigDecimal.ZERO,
        valuePending: BigDecimal? = BigDecimal("4.065"),
    ): ZcashDiagSnapshot = ZcashDiagSnapshot(
        pool = pool,
        syncStateDiscriminator = syncStateDiscriminator,
        chainTipHeight = chainTipHeight,
        fullyScannedHeight = fullyScannedHeight,
        scanProgressPercent = scanProgressPercent,
        recoveryProgressPercent = recoveryProgressPercent,
        overallSyncRangeState = overallSyncRangeState,
        overallSyncRangeStart = overallSyncRangeStart,
        overallSyncRangeEnd = overallSyncRangeEnd,
        firstUnenhancedHeight = firstUnenhancedHeight,
        available = available,
        changePending = changePending,
        valuePending = valuePending,
    )
}
