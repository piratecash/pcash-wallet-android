package cash.p.terminal.core.managers

import android.util.Base64
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.Base64 as JavaBase64

class OfflineTransactionPayloadEncoderTest {

    private lateinit var encoder: OfflineTransactionPayloadEncoder

    @Before
    fun setup() {
        // Route Android's Base64 through the JVM url-safe codec so encode/decode round-trip for real.
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            JavaBase64.getUrlEncoder().withoutPadding().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            JavaBase64.getUrlDecoder().decode(firstArg<String>())
        }
        encoder = OfflineTransactionPayloadEncoder()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun decode_validRoundTrip_returnsEquivalentFields() {
        val payload = encoder.encode(draft())

        val decoded = encoder.decode(payload)

        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals("bitcoin", decoded.blockchainUid)
        assertEquals("deadbeefdeadbeef", decoded.rawHex)
        assertEquals(TX_HASH, decoded.txHash)
        assertEquals("100000", decoded.amountAtomic)
        assertEquals("1000", decoded.feeAtomic)
        assertEquals("bc1qexampleaddrxyz", decoded.toAddress)
        assertEquals(CREATED_AT, decoded.createdAt)
    }

    @Test
    fun decode_uppercaseRawHex_isNormalisedToLowercase() {
        val payload = encoder.encode(draft(rawHex = "DEADBEEF"))

        assertEquals("deadbeef", encoder.decode(payload)?.rawHex)
    }

    @Test
    fun decode_pathBlockchainUidContradictsBody_returnsNull() {
        val payload = encoder.encode(draft())
        // Swap only the authoritative path segment; the body still claims "bitcoin".
        val parts = payload.split(":", limit = 5)
        val spoofed = listOf(parts[0], parts[1], parts[2], "litecoin", parts[4]).joinToString(":")

        assertNull(encoder.decode(spoofed))
    }

    @Test
    fun decode_unknownPrefixOrVersion_returnsNull() {
        assertNull(encoder.decode("not-a-payload"))
        assertNull(encoder.decode("pcash:tx:v2:bitcoin:body"))
        assertNull(encoder.decode("pcash:other:v1:bitcoin:body"))
    }

    @Test
    fun decode_malformedBase64Body_returnsNull() {
        assertNull(encoder.decode("pcash:tx:v1:bitcoin:@@@not-base64@@@"))
    }

    @Test
    fun decode_corruptedBody_returnsNull() {
        val payload = encoder.encode(draft())
        val parts = payload.split(":", limit = 5)
        val body = parts[4]
        // Flip the first body byte: the deflate stream no longer inflates / no longer checksums.
        val corruptedFirst = if (body[0] == 'A') 'B' else 'A'
        val corrupted = listOf(parts[0], parts[1], parts[2], parts[3], corruptedFirst + body.substring(1))
            .joinToString(":")

        assertNull(encoder.decode(corrupted))
    }

    @Test
    fun decode_truncatedBody_returnsNull() {
        val payload = encoder.encode(draft())
        val parts = payload.split(":", limit = 5)
        // Cut into the compressed data, not just the trailing checksum, so the stream cannot inflate.
        val truncated = listOf(parts[0], parts[1], parts[2], parts[3], parts[4].take(parts[4].length / 2))
            .joinToString(":")

        assertNull(encoder.decode(truncated))
    }

    @Test
    fun decode_txHashNotThirtyTwoBytes_returnsNull() {
        val payload = encoder.encode(draft(txHash = "abcd"))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_blankToAddress_returnsNull() {
        val payload = encoder.encode(draft(toAddress = ""))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_negativeAmount_returnsNull() {
        val payload = encoder.encode(draft(amount = BigDecimal("-0.001")))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun isOfflineTransactionPayload_matchesOnlyPcashPrefix() {
        assertTrue(OfflineTransactionPayloadEncoder.isOfflineTransactionPayload("pcash:tx:v1:bitcoin:body"))
        assertTrue(OfflineTransactionPayloadEncoder.isOfflineTransactionPayload("  pcash:tx:anything"))
        assertFalse(OfflineTransactionPayloadEncoder.isOfflineTransactionPayload("deadbeef"))
        assertFalse(OfflineTransactionPayloadEncoder.isOfflineTransactionPayload("bitcoin:tx:v1"))
    }

    @Test
    fun isRawTransactionHex_acceptsOnlyLongEvenHex() {
        assertTrue(OfflineTransactionPayloadEncoder.isRawTransactionHex("deadbeefdeadbeefdead"))
        assertTrue(OfflineTransactionPayloadEncoder.isRawTransactionHex("  DEADBEEFDEADBEEFDEAD  "))
        assertFalse(OfflineTransactionPayloadEncoder.isRawTransactionHex("deadbeef"))
        assertFalse(OfflineTransactionPayloadEncoder.isRawTransactionHex("deadbeefdeadbeefdea"))
        assertFalse(OfflineTransactionPayloadEncoder.isRawTransactionHex("deadbeefdeadbeefzzzz"))
        assertFalse(OfflineTransactionPayloadEncoder.isRawTransactionHex("pcash:tx:v1:bitcoin:body"))
    }

    private fun draft(
        rawHex: String = "deadbeefdeadbeef",
        txHash: String = TX_HASH,
        toAddress: String = "bc1qexampleaddrxyz",
        amount: BigDecimal = BigDecimal("0.001"),
    ): OfflineSignedTransactionDraft {
        val bitcoin = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null)
        val token = Token(
            coin = Coin(uid = bitcoin.type.uid, name = bitcoin.name, code = bitcoin.name),
            blockchain = bitcoin,
            type = TokenType.Native,
            decimals = 8,
        )
        val wallet = mockk<Wallet>(relaxed = true) {
            every { this@mockk.token } returns token
        }
        return OfflineSignedTransactionDraft(
            wallet = wallet,
            amount = amount,
            fee = BigDecimal("0.00001"),
            toAddress = toAddress,
            rawHex = rawHex,
            txHash = txHash,
            inputOutpoints = emptyList(),
            createdAt = CREATED_AT,
        )
    }

    private companion object {
        const val TX_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val CREATED_AT = 1_700_000_000_000L
    }
}
