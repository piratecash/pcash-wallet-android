package cash.p.terminal.modules.send.ton

import cash.p.terminal.R
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.TonOfflineAnchor
import cash.p.terminal.modules.pin.core.UptimeProvider
import io.horizontalsystems.tonkit.FriendlyAddress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.ton.block.AddrStd
import java.io.IOException
import java.math.BigDecimal

class TonOfflineAnchorServiceTest {

    private val adapter = mockk<ISendTonAdapter>()
    private val uptimeProvider = mockk<UptimeProvider>()
    private val service = TonOfflineAnchorService(adapter, uptimeProvider)

    private var uptime = FETCH_UPTIME

    private val amount = BigDecimal("1.5")
    private val address = FriendlyAddress.parse(TON_ADDRESS)
    private val quote = TonFeeQuote(fee = BigDecimal("0.01"), amount = amount, address = address, memo = null)

    init {
        every { uptimeProvider.uptime } answers { uptime }
    }

    private suspend fun anchor(seqno: Int, unixTimeSeconds: Long = ANCHOR_TIME) {
        coEvery { adapter.fetchOfflineAnchor() } returns TonOfflineAnchor(seqno, unixTimeSeconds)
        service.refreshAnchor { true }
    }

    private fun buildRequest() = service.buildSignRequest(amount, address, null, quote)

    private fun consume(seqno: Int) = service.consumeAnchor(seqno, service.ownershipGeneration)

    private fun assertFailsWithText(expectedRes: Int, block: () -> Unit) {
        try {
            block()
            fail("Expected LocalizedException")
        } catch (e: LocalizedException) {
            assertEquals(expectedRes, e.errorTextRes)
        }
    }

    // --- buildSignRequest ---

    @Test
    fun buildSignRequest_noAnchor_throwsAnchorRequired() {
        assertFailsWithText(R.string.offline_transaction_anchor_required) { buildRequest() }
    }

    @Test
    fun buildSignRequest_anchoredSeqnoZero_throwsWalletNotDeployed() = runTest {
        anchor(seqno = 0)

        assertFailsWithText(R.string.offline_sign_error_wallet_not_deployed) { buildRequest() }
    }

    @Test
    fun buildSignRequest_freshAnchor_projectsValidUntilFromElapsedUptime() = runTest {
        anchor(seqno = 7)
        uptime = FETCH_UPTIME + 60_000L

        val request = buildRequest()

        assertEquals(7, request.seqno)
        assertEquals(ANCHOR_TIME + 60 + TTL_SECONDS, request.validUntil)
        assertEquals(quote.fee, request.fee)
    }

    @Test
    fun buildSignRequest_nullQuote_throwsFeeUnavailable() = runTest {
        anchor(seqno = 7)

        assertFailsWithText(R.string.send_error_fee_rate_unavailable) {
            service.buildSignRequest(amount, address, null, quote = null)
        }
    }

    @Test
    fun buildSignRequest_staleQuote_throwsFeeUnavailable() = runTest {
        anchor(seqno = 7)

        assertFailsWithText(R.string.send_error_fee_rate_unavailable) {
            service.buildSignRequest(amount + BigDecimal.ONE, address, null, quote)
        }
    }

    // --- consumeAnchor ---

    @Test
    fun consumeAnchor_signedSeqno_burnsAnchor() = runTest {
        anchor(seqno = 7)

        consume(7)

        assertFailsWithText(R.string.offline_transaction_anchor_required) { buildRequest() }
    }

    @Test
    fun consumeAnchor_differentSeqno_keepsAnchor() = runTest {
        anchor(seqno = 7)

        consume(5)

        assertEquals(7, buildRequest().seqno)
    }

    @Test
    fun consumeAnchor_staleOwnership_keepsAnchorForReplacementAttempt() = runTest {
        anchor(seqno = 7)
        val abandonedOwnership = service.ownershipGeneration

        service.invalidateOwnership()
        service.consumeAnchor(7, abandonedOwnership)

        // The abandoned attempt must not burn the anchor; the current owner still can.
        assertEquals(7, buildRequest().seqno)
        consume(7)
        assertFailsWithText(R.string.offline_transaction_anchor_required) { buildRequest() }
    }

    @Test
    fun refreshAnchor_consumedSeqnoRefetched_neverResurrects() = runTest {
        anchor(seqno = 7)
        consume(7)

        anchor(seqno = 7)

        assertFailsWithText(R.string.offline_transaction_anchor_required) { buildRequest() }
    }

    @Test
    fun refreshAnchor_newSeqnoAfterConsume_installsIt() = runTest {
        anchor(seqno = 7)
        consume(7)

        anchor(seqno = 8)

        assertEquals(8, buildRequest().seqno)
    }

    // --- refreshAnchor / install ---

    @Test
    fun refreshAnchor_strictlyNewerSeqno_replacesAnchorAndTimeBase() = runTest {
        anchor(seqno = 7)
        uptime = FETCH_UPTIME + 60_000L
        anchor(seqno = 8, unixTimeSeconds = ANCHOR_TIME + 300)

        val request = buildRequest()

        assertEquals(8, request.seqno)
        assertEquals(ANCHOR_TIME + 300 + TTL_SECONDS, request.validUntil)
    }

    @Test
    fun refreshAnchor_sameOrOlderSeqno_keepsExistingAnchor() = runTest {
        anchor(seqno = 7)
        uptime = FETCH_UPTIME + 60_000L

        anchor(seqno = 7, unixTimeSeconds = ANCHOR_TIME + 300)
        anchor(seqno = 6, unixTimeSeconds = ANCHOR_TIME + 600)

        // validUntil is still projected from the ORIGINAL fetch's time base.
        assertEquals(ANCHOR_TIME + 60 + TTL_SECONDS, buildRequest().validUntil)
    }

    @Test
    fun refreshAnchor_fetchFails_keepsExistingAnchor() = runTest {
        anchor(seqno = 7)

        coEvery { adapter.fetchOfflineAnchor() } throws IOException("offline")
        service.refreshAnchor { true }

        assertEquals(7, buildRequest().seqno)
    }

    @Test
    fun refreshAnchor_transportFailureThenSuccess_retriesUntilAnchored() = runTest {
        coEvery { adapter.fetchOfflineAnchor() }
            .throws(IOException("connection reset"))
            .andThen(TonOfflineAnchor(seqno = 7, unixTimeSeconds = ANCHOR_TIME))

        service.refreshAnchor { true }

        assertEquals(7, buildRequest().seqno)
        coVerify(exactly = 2) { adapter.fetchOfflineAnchor() }
    }

    @Test
    fun refreshAnchor_permanentFailure_stopsWithoutRetry() = runTest {
        coEvery { adapter.fetchOfflineAnchor() } throws RuntimeException("bad response")

        service.refreshAnchor { true }

        coVerify(exactly = 1) { adapter.fetchOfflineAnchor() }
        assertFailsWithText(R.string.offline_transaction_anchor_required) { buildRequest() }
    }

    @Test
    fun refreshAnchor_undeployedAnchor_doesNotSuppressRetry() = runTest {
        anchor(seqno = 0)

        coEvery { adapter.fetchOfflineAnchor() }
            .throws(IOException("connection reset"))
            .andThen(TonOfflineAnchor(seqno = 1, unixTimeSeconds = ANCHOR_TIME))
        service.refreshAnchor { true }

        assertEquals(1, buildRequest().seqno)
    }

    @Test
    fun refreshAnchor_disconnected_neverFetches() = runTest {
        service.refreshAnchor { false }

        coVerify(exactly = 0) { adapter.fetchOfflineAnchor() }
    }

    @Test
    fun refreshAnchor_fetchCancelled_rethrows() = runTest {
        coEvery { adapter.fetchOfflineAnchor() } throws CancellationException("cancelled")

        try {
            service.refreshAnchor { true }
            fail("Expected CancellationException")
        } catch (expected: CancellationException) {
        }
    }

    // --- TonFeeQuote.matches ---

    @Test
    fun tonFeeQuoteMatches_equivalentAddressInstance_returnsTrue() {
        assertTrue(quote.matches(amount, FriendlyAddress.parse(TON_ADDRESS), null))
    }

    @Test
    fun tonFeeQuoteMatches_sameAmountDifferentScale_returnsTrue() {
        assertTrue(quote.matches(BigDecimal("1.50"), address, null))
    }

    @Test
    fun tonFeeQuoteMatches_bounceabilityDiffers_returnsFalse() {
        assertFalse(quote.matches(amount, FriendlyAddress.parse(TON_ADDRESS, bounceable = true), null))
    }

    @Test
    fun tonFeeQuoteMatches_differentAddress_returnsFalse() {
        val other = FriendlyAddress(AddrStd(0, ByteArray(32)), isBounceable = false)

        assertFalse(quote.matches(amount, other, null))
    }

    @Test
    fun tonFeeQuoteMatches_differentMemo_returnsFalse() {
        assertFalse(quote.matches(amount, address, "memo"))
    }

    companion object {
        private const val TON_ADDRESS = SendTonViewModelTest.VALID_TON_ADDRESS
        private const val ANCHOR_TIME = 1_700_000_000L
        private const val FETCH_UPTIME = 100_000L
        private const val TTL_SECONDS = 6 * 60 * 60L
    }
}
