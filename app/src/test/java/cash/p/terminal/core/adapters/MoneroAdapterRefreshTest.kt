package cash.p.terminal.core.adapters

import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineMoneroSignRequest
import cash.p.terminal.core.OfflineSignRequest
import cash.p.terminal.core.managers.MoneroKitWrapper
import com.m2049r.xmrwallet.offline.RawMoneroBroadcastResult
import com.m2049r.xmrwallet.offline.SignedRawMoneroTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

class MoneroAdapterRefreshTest {

    @Test
    fun refresh_requested_delegatesToMoneroKitWrapper() = runTest {
        val moneroKitWrapper = mockk<MoneroKitWrapper>(relaxed = true)
        val adapter = MoneroAdapter(moneroKitWrapper)

        adapter.refresh()

        coVerify(exactly = 1) { moneroKitWrapper.refresh() }
    }

    @Test
    fun maxSpendableBalance_lockedFunds_usesUnlockedBalance() {
        val moneroKitWrapper = mockk<MoneroKitWrapper>(relaxed = true)
        every { moneroKitWrapper.getBalance() } returns 10_000_000_000_000L
        every { moneroKitWrapper.getUnlockedBalance() } returns 4_000_000_000_000L
        val adapter = MoneroAdapter(moneroKitWrapper)

        assertEquals(0, BigDecimal("10").compareTo(adapter.balanceData.available))
        assertEquals(0, BigDecimal("4").compareTo(adapter.maxSpendableBalance))
    }

    @Test
    fun signOffline_validRequest_returnsRawHexTxHashAndFee() = runTest {
        val moneroKitWrapper = mockk<MoneroKitWrapper>(relaxed = true)
        val adapter = MoneroAdapter(moneroKitWrapper)
        coEvery {
            moneroKitWrapper.createSignedRawTransaction(any(), any(), any())
        } returns SignedRawMoneroTransaction(
            raw = byteArrayOf(0, 1, 2, 3, 4, 5),
            txId = TX_HASH.uppercase(),
            fee = 12_345_678_901L,
            txCount = 1,
        )

        val signed = adapter.signOffline(
            OfflineMoneroSignRequest(
                amount = BigDecimal("1.2"),
                address = "xmr-address",
                memo = "memo",
            )
        )

        assertEquals("000102030405", signed.rawHex)
        assertEquals(TX_HASH, signed.txHash)
        assertEquals(BigDecimal("0.012345678901"), signed.fee)
        coVerify {
            moneroKitWrapper.createSignedRawTransaction(
                amount = BigDecimal("1.2"),
                address = "xmr-address",
                memo = "memo",
            )
        }
    }

    @Test
    fun signOffline_wrongRequest_throws() = runTest {
        val adapter = MoneroAdapter(mockk(relaxed = true))

        try {
            adapter.signOffline(object : OfflineSignRequest {})
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun broadcastRawTransaction_validEnvelope_returnsSubmitted() = runTest {
        val raw = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val moneroKitWrapper = mockk<MoneroKitWrapper>(relaxed = true)
        val adapter = MoneroAdapter(moneroKitWrapper)
        coEvery { moneroKitWrapper.submitSignedRawTransaction(any()) } returns RawMoneroBroadcastResult.Submitted(TX_HASH)

        val result = adapter.broadcastRawTransaction("00010203040506070809")

        assertEquals(TX_HASH, result.txHash)
        assertEquals(BroadcastRawTransactionStatus.Submitted, result.status)
        coVerify {
            moneroKitWrapper.submitSignedRawTransaction(match {
                assertArrayEquals(raw, it)
                true
            })
        }
    }

    @Test
    fun broadcastRawTransaction_invalidHex_throws() = runTest {
        val moneroKitWrapper = mockk<MoneroKitWrapper>(relaxed = true)
        val adapter = MoneroAdapter(moneroKitWrapper)

        try {
            adapter.broadcastRawTransaction("deadbeef")
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        coVerify(exactly = 0) { moneroKitWrapper.submitSignedRawTransaction(any()) }
    }

    @Test
    fun broadcastRawTransaction_metadataPresent_throws() = runTest {
        val moneroKitWrapper = mockk<MoneroKitWrapper>(relaxed = true)
        val adapter = MoneroAdapter(moneroKitWrapper)

        try {
            adapter.broadcastRawTransaction(
                rawTransactionHex = "00010203040506070809",
                metadata = OfflineBroadcastMetadata.Tron(expiration = 1L),
            )
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        coVerify(exactly = 0) { moneroKitWrapper.submitSignedRawTransaction(any()) }
    }

    private companion object {
        const val TX_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
