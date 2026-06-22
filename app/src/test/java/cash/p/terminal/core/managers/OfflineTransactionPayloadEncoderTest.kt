package cash.p.terminal.core.managers

import android.util.Base64
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.OfflineSolanaRetryMetadata
import cash.p.terminal.entities.OfflineTonRetryMetadata
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Base58
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
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.zip.Deflater
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
        assertEquals("bitcoin|native", decoded.token.tokenQueryId)
        assertEquals("bitcoin", decoded.token.coinUid)
        assertEquals("BTC", decoded.token.coinCode)
        assertEquals("Bitcoin", decoded.token.coinName)
        assertEquals(8, decoded.token.decimals)
        assertEquals("100000", decoded.amountAtomic)
        assertEquals("bitcoin|native", decoded.fee?.tokenQueryId)
        assertEquals("1000", decoded.fee?.atomic)
        assertEquals(8, decoded.fee?.decimals)
        assertEquals("bc1qexampleaddrxyz", decoded.toAddress)
        assertEquals(CREATED_AT, decoded.createdAt)
    }

    @Test
    fun decode_eip20RoundTrip_usesTokenMetadataAndNativeFeeDecimals() {
        val payload = encoder.encode(
            draft(
                token = token(
                    blockchainType = BlockchainType.BinanceSmartChain,
                    blockchainName = "BNB Smart Chain",
                    coin = Coin(uid = "usd-coin", name = "USD Coin", code = "USDC"),
                    tokenType = TokenType.Eip20("0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d"),
                    decimals = 6,
                ),
                feeToken = token(
                    blockchainType = BlockchainType.BinanceSmartChain,
                    blockchainName = "BNB Smart Chain",
                    coin = Coin(uid = "binance-coin", name = "BNB", code = "BNB"),
                    decimals = 18,
                ),
                amount = BigDecimal("12.345678"),
                fee = BigDecimal("0.000000000000123456"),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("binance-smart-chain", decoded.blockchainUid)
        assertEquals(
            "binance-smart-chain|eip20:0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d",
            decoded.token.tokenQueryId,
        )
        assertEquals("USDC", decoded.token.coinCode)
        assertEquals(6, decoded.token.decimals)
        assertEquals("12345678", decoded.amountAtomic)
        assertEquals("binance-smart-chain|native", decoded.fee?.tokenQueryId)
        assertEquals(18, decoded.fee?.decimals)
        assertEquals("123456", decoded.fee?.atomic)
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
    fun decode_oldBodyWithoutToken_returnsNull() {
        val rawHex = "deadbeefdeadbeef"
        val oldBody = """
            {
              "version":1,
              "blockchainUid":"bitcoin",
              "encoding":"rawhex",
              "rawHex":"$rawHex",
              "txHash":"$TX_HASH",
              "amountAtomic":"100000",
              "feeAtomic":"1000",
              "toAddress":"bc1qexampleaddrxyz",
              "createdAt":$CREATED_AT,
              "inputOutpoints":[],
              "checksum":"${checksum(rawHex)}"
            }
        """.trimIndent()
        val payload = "pcash:tx:v1:bitcoin:${compressedBase64(oldBody)}"

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_malformedTokenQueryId_returnsNull() {
        val payload = payloadFromBody(validBody(tokenQueryId = "not-a-token-query"))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_tokenBlockchainMismatch_returnsNull() {
        val payload = payloadFromBody(validBody(tokenQueryId = "ethereum|native"))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_feeBlockchainMismatch_returnsNull() {
        val payload = payloadFromBody(validBody(feeTokenQueryId = "ethereum|native"))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_solanaRoundTrip_returnsRetryMetadataAndPreservesSignatureCase() {
        val payload = encoder.encode(
            draft(
                txHash = SOLANA_SIGNATURE,
                token = solanaToken(),
                feeToken = solanaToken(),
                solanaRetryMetadata = solanaRetryMetadata(),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("solana", decoded.blockchainUid)
        assertEquals(SOLANA_SIGNATURE, decoded.txHash)
        assertEquals("solana|native", decoded.token.tokenQueryId)
        assertEquals("block-hash", decoded.solanaRetryMetadata?.blockHash)
        assertEquals(123L, decoded.solanaRetryMetadata?.lastValidBlockHeight)
    }

    @Test
    fun decode_solanaPayloadWithoutRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                txHash = SOLANA_SIGNATURE,
                token = solanaToken(),
                feeToken = solanaToken(),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_nonSolanaPayloadWithSolanaRetryMetadata_returnsNull() {
        val payload = encoder.encode(draft(solanaRetryMetadata = solanaRetryMetadata()))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_solanaInvalidSignature_returnsNull() {
        val payload = encoder.encode(
            draft(
                txHash = "0123456789abcdef",
                token = solanaToken(),
                feeToken = solanaToken(),
                solanaRetryMetadata = solanaRetryMetadata(),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_tonRoundTrip_returnsRetryMetadataAndHexHash() {
        val payload = encoder.encode(
            draft(
                token = tonToken(),
                feeToken = tonToken(),
                tonRetryMetadata = tonRetryMetadata(),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("the-open-network", decoded.blockchainUid)
        assertEquals(TX_HASH, decoded.txHash)
        assertEquals("the-open-network|native", decoded.token.tokenQueryId)
        assertEquals(1_700_000_300L, decoded.tonRetryMetadata?.validUntil)
        assertEquals("EQSender", decoded.tonRetryMetadata?.senderAddress)
        assertEquals(7, decoded.tonRetryMetadata?.seqno)
    }

    @Test
    fun decode_tonPayloadWithoutRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = tonToken(),
                feeToken = tonToken(),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_nonTonPayloadWithTonRetryMetadata_returnsNull() {
        val payload = encoder.encode(draft(tonRetryMetadata = tonRetryMetadata()))

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_tonInvalidRetryMetadata_returnsNull() {
        val payload = encoder.encode(
            draft(
                token = tonToken(),
                feeToken = tonToken(),
                tonRetryMetadata = tonRetryMetadata(validUntil = 0),
            )
        )

        assertNull(encoder.decode(payload))
    }

    @Test
    fun decode_tonJettonRoundTrip_preservesDisplayTokenAndNativeFee() {
        val payload = encoder.encode(
            draft(
                token = jettonToken(),
                feeToken = tonToken(),
                amount = BigDecimal("12.345678"),
                fee = BigDecimal("0.000000001"),
                tonRetryMetadata = tonRetryMetadata(),
            )
        )

        val decoded = requireNotNull(encoder.decode(payload))

        assertEquals("the-open-network|the-open-network:EQJetton", decoded.token.tokenQueryId)
        assertEquals("JET", decoded.token.coinCode)
        assertEquals(6, decoded.token.decimals)
        assertEquals("12345678", decoded.amountAtomic)
        assertEquals("the-open-network|native", decoded.fee?.tokenQueryId)
        assertEquals(9, decoded.fee?.decimals)
        assertEquals("1", decoded.fee?.atomic)
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
        fee: BigDecimal = BigDecimal("0.00001"),
        token: Token = token(
            blockchainType = BlockchainType.Bitcoin,
            blockchainName = "Bitcoin",
            coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
            decimals = 8,
        ),
        feeToken: Token? = null,
        solanaRetryMetadata: OfflineSolanaRetryMetadata? = null,
        tonRetryMetadata: OfflineTonRetryMetadata? = null,
    ): OfflineSignedTransactionDraft {
        val wallet = mockk<Wallet>(relaxed = true) {
            every { this@mockk.token } returns token
        }
        return OfflineSignedTransactionDraft(
            wallet = wallet,
            amount = amount,
            fee = fee,
            toAddress = toAddress,
            rawHex = rawHex,
            txHash = txHash,
            inputOutpoints = emptyList(),
            createdAt = CREATED_AT,
            feeToken = feeToken,
            solanaRetryMetadata = solanaRetryMetadata,
            tonRetryMetadata = tonRetryMetadata,
        )
    }

    private fun token(
        blockchainType: BlockchainType,
        blockchainName: String,
        coin: Coin,
        tokenType: TokenType = TokenType.Native,
        decimals: Int,
    ) = Token(
        coin = coin,
        blockchain = Blockchain(blockchainType, blockchainName, null),
        type = tokenType,
        decimals = decimals,
    )

    private fun solanaToken() = token(
        blockchainType = BlockchainType.Solana,
        blockchainName = "Solana",
        coin = Coin(uid = "solana", name = "Solana", code = "SOL"),
        decimals = 9,
    )

    private fun solanaRetryMetadata() = OfflineSolanaRetryMetadata(
        blockHash = "block-hash",
        lastValidBlockHeight = 123L,
    )

    private fun tonToken() = token(
        blockchainType = BlockchainType.Ton,
        blockchainName = "TON",
        coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
        decimals = 9,
    )

    private fun jettonToken() = token(
        blockchainType = BlockchainType.Ton,
        blockchainName = "TON",
        coin = Coin(uid = "jetton", name = "Jetton", code = "JET"),
        tokenType = TokenType.Jetton("EQJetton"),
        decimals = 6,
    )

    private fun tonRetryMetadata(
        validUntil: Long = 1_700_000_300L,
        senderAddress: String = "EQSender",
        seqno: Int = 7,
    ) = OfflineTonRetryMetadata(
        validUntil = validUntil,
        senderAddress = senderAddress,
        seqno = seqno,
    )

    private fun compressedBase64(json: String): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        return try {
            deflater.setInput(json.encodeToByteArray())
            deflater.finish()
            val output = ByteArrayOutputStream(json.length)
            val buffer = ByteArray(512)
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            JavaBase64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
        } finally {
            deflater.end()
        }
    }

    private fun payloadFromBody(
        body: String,
        blockchainUid: String = "bitcoin",
    ) = "pcash:tx:v1:$blockchainUid:${compressedBase64(body)}"

    private fun validBody(
        tokenQueryId: String = "bitcoin|native",
        feeTokenQueryId: String = "bitcoin|native",
        blockchainUid: String = "bitcoin",
    ): String {
        val rawHex = "deadbeefdeadbeef"
        return """
            {
              "version":1,
              "blockchainUid":"$blockchainUid",
              "encoding":"rawhex",
              "rawHex":"$rawHex",
              "txHash":"$TX_HASH",
              "token":{
                "tokenQueryId":"$tokenQueryId",
                "coinUid":"bitcoin",
                "coinCode":"BTC",
                "coinName":"Bitcoin",
                "decimals":8
              },
              "amountAtomic":"100000",
              "fee":{
                "tokenQueryId":"$feeTokenQueryId",
                "atomic":"1000",
                "decimals":8
              },
              "toAddress":"bc1qexampleaddrxyz",
              "createdAt":$CREATED_AT,
              "inputOutpoints":[],
              "checksum":"${checksum(rawHex)}"
            }
        """.trimIndent()
    }

    private fun checksum(rawHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawHex.lowercase().encodeToByteArray())
        return JavaBase64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(8))
    }

    private companion object {
        const val TX_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val SOLANA_SIGNATURE = Base58.encode(ByteArray(64) { (it + 1).toByte() })
        const val CREATED_AT = 1_700_000_000_000L
    }
}
