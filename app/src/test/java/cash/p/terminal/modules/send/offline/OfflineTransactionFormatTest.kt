package cash.p.terminal.modules.send.offline

import android.util.Base64
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.Base64 as JavaBase64

class OfflineTransactionFormatTest {

    private lateinit var encoder: OfflineTransactionPayloadEncoder

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            JavaBase64.getUrlEncoder().withoutPadding().encodeToString(firstArg())
        }
        encoder = OfflineTransactionPayloadEncoder()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun canEncodeAsOfflineQr_smallContent_returnsTrue() {
        assertTrue("pcash:tx:v1:bitcoin:body".canEncodeAsOfflineQr())
    }

    @Test
    fun canEncodeAsOfflineQr_tooLargeContent_returnsFalse() {
        assertFalse("a".repeat(TOO_LARGE_QR_PAYLOAD_SIZE).canEncodeAsOfflineQr())
    }

    @Test
    fun preferredTransferFormat_pcashSmall_keepsPcash() {
        val transaction = transaction(pcashPayload = "pcash:tx:v1:bitcoin:body")

        assertEquals(
            OfflineTransactionFormat.Pcash,
            OfflineTransactionFormat.Pcash.preferredTransferFormat(transaction),
        )
    }

    @Test
    fun preferredTransferFormat_pcashTooLargeRawFits_returnsRaw() {
        val transaction = transaction(pcashPayload = "a".repeat(TOO_LARGE_QR_PAYLOAD_SIZE))

        assertEquals(
            OfflineTransactionFormat.Raw,
            OfflineTransactionFormat.Pcash.preferredTransferFormat(transaction),
        )
    }

    @Test
    fun preferredTransferFormat_realLargePayloadPcashTooLargeRawLarger_keepsPcash() {
        val rawHex = deterministicRawHex(LARGE_RAW_BYTES)
        val pcashPayload = encoder.encode(draft(rawHex))
        val transaction = transaction(rawHex = rawHex, pcashPayload = pcashPayload)

        assertFalse(pcashPayload.canEncodeAsOfflineQr())
        assertFalse(rawHex.canEncodeAsOfflineQr())
        assertTrue(rawHex.length > pcashPayload.length)
        assertEquals(
            OfflineTransactionFormat.Pcash,
            OfflineTransactionFormat.Pcash.preferredTransferFormat(transaction),
        )
    }

    private fun transaction(
        rawHex: String = "deadbeefdeadbeef",
        pcashPayload: String,
    ) = OfflineSignedTransaction(
        rawHex = rawHex,
        pcashPayload = pcashPayload,
        txHash = TX_HASH,
        createdAt = 1_700_000_000_000L,
    )

    private fun draft(rawHex: String) = OfflineSignedTransactionDraft(
        wallet = wallet(),
        amount = BigDecimal("0.001"),
        fee = BigDecimal("0.00001"),
        toAddress = "bc1qexampleaddrxyz",
        rawHex = rawHex,
        txHash = TX_HASH,
        inputOutpoints = emptyList(),
        createdAt = 1_700_000_000_000L,
    )

    private fun wallet(): Wallet = mockk {
        every { token } returns Token(
            coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
            blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
            type = TokenType.Native,
            decimals = 8,
        )
    }

    private fun deterministicRawHex(byteCount: Int): String = buildString(byteCount * 2) {
        var state = 0x12345678
        repeat(byteCount) {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            append((state and 0xff).toString(radix = 16).padStart(2, '0'))
        }
    }

    private companion object {
        const val TX_HASH = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        const val LARGE_RAW_BYTES = 5_000
        const val TOO_LARGE_QR_PAYLOAD_SIZE = 1_700
    }
}
