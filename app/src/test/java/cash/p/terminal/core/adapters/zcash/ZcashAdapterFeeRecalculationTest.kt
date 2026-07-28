package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * Tests for ZcashAdapter fee recalculation and unified balance after the Ironwood (NU6.3) switch.
 *
 * The harness is the shared [ZcashAdapterTestFixture]: the synchronizer's `status` and
 * `walletBalances` are plain `MutableStateFlow`s, so the "sync -> funds arrive" sequence plays
 * out deterministically on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterFeeRecalculationTest : ZcashAdapterTestFixture() {

    /** Amounts the adapter called `proposeTransfer` with — they expose every probe step. */
    private val proposeCalls = mutableListOf<Zatoshi>()

    /**
     * Fee `proposeTransfer` returns for the requested amount, or `null` — then the SDK "rejects"
     * the proposal the same way it does when the funds do not cover the fee.
     */
    private var feeFor: (Zatoshi) -> Long? = { MINERS_FEE_ZAT }

    /** What `proposeTransfer` does before returning — the test uses it to keep a probe in flight. */
    private var beforePropose: suspend (Zatoshi) -> Unit = { }

    override fun stubSynchronizer() {
        val sdkAccount = mockk<cash.z.ecc.android.sdk.model.Account>(relaxed = true) {
            every { accountUuid } returns this@ZcashAdapterFeeRecalculationTest.accountUuid
        }
        coEvery { mockSynchronizer.getAccounts() } returns listOf(sdkAccount)

        coEvery { mockSynchronizer.proposeTransfer(any(), any(), any(), any()) } coAnswers {
            val requested = thirdArg<Zatoshi>()
            proposeCalls += requested
            val fee = feeFor(requested)
                ?: throw TransactionEncoderException.ProposalFromParametersException(
                    IllegalStateException("Insufficient funds")
                )
            beforePropose(requested)
            mockk<Proposal> { every { totalFeeRequired() } returns Zatoshi(fee) }
        }
    }

    // region Unified balance

    @Test
    fun `unified balance sums orchard and ironwood pools`() = runTest(dispatcher) {
        adapter = createAdapter(AddressSpecType.Unified)
        walletBalancesFlow.value = mapOf(
            accountUuid to accountBalance(
                orchard = WalletBalance(Zatoshi(100_000_000), Zatoshi(1_000), Zatoshi(2_000)),
                ironwood = WalletBalance(Zatoshi(50_000_000), Zatoshi(3_000), Zatoshi(4_000))
            )
        )
        advanceUntilIdle()

        // 1.5 ZEC available, 0.0001 in flight — the sum of both pools, not Orchard alone.
        assertBigDecimalEquals("1.5", adapter.balanceData.available)
        assertBigDecimalEquals("0.0001", adapter.balanceData.pending)
    }

    @Test
    fun `unified balance ignores sapling and transparent pools`() = runTest(dispatcher) {
        adapter = createAdapter(AddressSpecType.Unified)
        walletBalancesFlow.value = mapOf(
            accountUuid to accountBalance(
                sapling = WalletBalance(Zatoshi(700_000_000), Zatoshi(0), Zatoshi(0)),
                orchard = WalletBalance(Zatoshi(100_000_000), Zatoshi(0), Zatoshi(0)),
                ironwood = WalletBalance(Zatoshi(50_000_000), Zatoshi(0), Zatoshi(0)),
                unshielded = Zatoshi(900_000_000)
            )
        )
        advanceUntilIdle()

        assertBigDecimalEquals("1.5", adapter.balanceData.available)
    }

    // endregion

    // region Fee recalculation

    @Test
    fun `fee is recalculated when ironwood funds arrive after sync`() = runTest(dispatcher) {
        feeFor = { MINERS_FEE_ZAT }
        startSyncedAdapter()

        emitBalance(orchard = 100_000_000)
        assertEquals(1, proposeCalls.size)
        assertEquals(Zatoshi(100_000_000), proposeCalls.last())

        // After NU6.3 activation change arrives in Ironwood: the available balance grew and the
        // fee must be recalculated, now across two bundles.
        feeFor = { 30_000L }
        emitBalance(orchard = 100_000_000, ironwood = 25_000_000)

        assertEquals(2, proposeCalls.size)
        assertEquals(Zatoshi(125_000_000), proposeCalls.last())
        assertFeeEquals(30_000L)
    }

    @Test
    fun `fee is not recalculated while account balance is unchanged`() = runTest(dispatcher) {
        feeFor = { 30_000L }
        startSyncedAdapter()
        emitBalance(orchard = 100_000_000)
        assertEquals(1, proposeCalls.size)

        // A repeated SYNCED without a balance change must not spawn another probe once a real
        // fee has been published.
        resync()

        assertEquals(1, proposeCalls.size)
    }

    @Test
    fun `fee is recalculated on resync while it is still the default miners fee`() =
        runTest(dispatcher) {
            feeFor = { MINERS_FEE_ZAT }
            startSyncedAdapter()
            emitBalance(orchard = 100_000_000)
            assertEquals(1, proposeCalls.size)

            // While the published fee is still the default one, the balance snapshot is not
            // enough to conclude the fee is current: ZIP-317 also depends on the proposal target
            // height, which changes at NU6.3 activation without touching any balance field.
            resync()

            assertEquals(2, proposeCalls.size)
        }

    @Test
    fun `fee is recalculated when funds move between pools at equal available`() =
        runTest(dispatcher) {
            feeFor = { MINERS_FEE_ZAT }
            startSyncedAdapter()
            emitBalance(orchard = 100_000_000)
            assertEquals(1, proposeCalls.size)

            // The turnstile moves funds from Orchard to Ironwood: the total is the same but the
            // pool composition differs, and under ZIP-317 the per-bundle fee changes.
            feeFor = { 20_000L }
            emitBalance(ironwood = 100_000_000)

            assertEquals(2, proposeCalls.size)
            assertEquals(Zatoshi(100_000_000), proposeCalls.last())
            assertFeeEquals(20_000L)
        }

    @Test
    fun `fee probe discovers fees above the former forty thousand zatoshi ceiling`() =
        runTest(dispatcher) {
            val available = 100_000_000L
            val requiredFee = 50_000L
            // The SDK rejects the proposal until the amount leaves room for the fee: the probe
            // has to step down 5 times by 10,000 zat, which the former 4-step ceiling forbade.
            feeFor = { requested ->
                requiredFee.takeIf { requested.value + requiredFee <= available }
            }

            startSyncedAdapter()
            emitBalance(orchard = available)

            assertEquals(6, proposeCalls.size)
            assertEquals(Zatoshi(available - requiredFee), proposeCalls.last())
            assertFeeEquals(requiredFee)
        }

    @Test
    fun `fully failed fee probe is retried on the next trigger`() = runTest(dispatcher) {
        feeFor = { null }
        startSyncedAdapter()
        emitBalance(orchard = 100_000_000)

        // 1 attempt plus FEE_PROBE_ATTEMPTS step-downs — and none of them succeeded.
        val probeLength = proposeCalls.size
        assertEquals(21, probeLength)
        assertBigDecimalEquals(ZcashAdapter.MINERS_FEE.toPlainString(), adapter.fee.value)

        // The balance marker is cleared after a failure, so the next trigger probes again
        // instead of treating the fee as already calculated.
        resync()

        assertEquals(probeLength * 2, proposeCalls.size)
    }

    @Test
    fun `superseded fee probe does not publish its stale result`() = runTest(dispatcher) {
        val stuck = CompletableDeferred<Unit>()
        feeFor = { 10_000L }
        startSyncedAdapter()

        // The probe for the old balance goes "to the network" and gets stuck past its last
        // cancellation point: on a real IO dispatcher the thread is preempted exactly here,
        // between proposeTransfer returning and the write to _fee, where cancellation no longer
        // stops it.
        beforePropose = { withContext(NonCancellable) { stuck.await() } }
        emitBalance(orchard = 100_000_000)
        assertEquals(1, proposeCalls.size)

        // Change arrived in Ironwood — this calculation supersedes the stuck one and publishes
        // its own fee.
        beforePropose = { }
        feeFor = { 50_000L }
        emitBalance(orchard = 100_000_000, ironwood = 25_000_000)
        assertFeeEquals(50_000L)

        // The stuck probe returns with the fee for a balance that is no longer current.
        stuck.complete(Unit)
        advanceUntilIdle()

        // It must not overwrite the fresh result: otherwise the fee stays understated until the
        // next balance change, since the marker already points at the new snapshot.
        assertFeeEquals(50_000L)
    }

    @Test
    fun `zero balance resets fee to the default miners fee`() = runTest(dispatcher) {
        feeFor = { 30_000L }
        startSyncedAdapter()
        emitBalance(orchard = 100_000_000)
        assertFeeEquals(30_000L)

        emitBalance(orchard = 0)

        // At a zero balance no probe is launched — the fee falls back to the base one.
        assertEquals(1, proposeCalls.size)
        assertBigDecimalEquals(ZcashAdapter.MINERS_FEE.toPlainString(), adapter.fee.value)
    }

    // endregion

    // region Harness

    /** Brings the adapter up to SYNCED — only then does a balance change move the fee. */
    private fun TestScope.startSyncedAdapter() {
        adapter = createAdapter(AddressSpecType.Unified)
        adapter.start()
        advanceUntilIdle()
        statusFlow.value = Synchronizer.Status.SYNCED
        advanceUntilIdle()
        proposeCalls.clear()
    }

    private fun TestScope.emitBalance(orchard: Long = 0, ironwood: Long = 0) {
        walletBalancesFlow.value = mapOf(
            accountUuid to accountBalance(
                orchard = WalletBalance(Zatoshi(orchard), Zatoshi(0), Zatoshi(0)),
                ironwood = WalletBalance(Zatoshi(ironwood), Zatoshi(0), Zatoshi(0))
            )
        )
        advanceUntilIdle()
    }

    /** Cycles the status SYNCING -> SYNCED without touching the balance. */
    private fun TestScope.resync() {
        statusFlow.value = Synchronizer.Status.SYNCING
        advanceUntilIdle()
        statusFlow.value = Synchronizer.Status.SYNCED
        advanceUntilIdle()
    }

    private fun accountBalance(
        sapling: WalletBalance = ZERO_BALANCE,
        orchard: WalletBalance = ZERO_BALANCE,
        ironwood: WalletBalance = ZERO_BALANCE,
        unshielded: Zatoshi = Zatoshi(0)
    ) = AccountBalance(
        sapling = sapling,
        orchard = orchard,
        ironwood = ironwood,
        unshielded = unshielded
    )

    private fun assertFeeEquals(zatoshi: Long) = assertBigDecimalEquals(
        Zatoshi(zatoshi).convertZatoshiToZec(DECIMAL_COUNT).toPlainString(),
        adapter.fee.value
    )

    private fun assertBigDecimalEquals(expected: String, actual: BigDecimal) {
        assertEquals(BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros())
    }

    private companion object {
        const val DECIMAL_COUNT = 8
        const val MINERS_FEE_ZAT = 10_000L
        val ZERO_BALANCE = WalletBalance(Zatoshi(0), Zatoshi(0), Zatoshi(0))
    }

    // endregion
}
