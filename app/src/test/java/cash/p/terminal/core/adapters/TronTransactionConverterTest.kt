package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.TronKitWrapper
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.decoration.NativeTransactionDecoration
import io.horizontalsystems.tronkit.decoration.TokenInfo
import io.horizontalsystems.tronkit.decoration.trc20.OutgoingTrc20Decoration
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.models.FullTransaction
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.TransferAssetContract
import io.horizontalsystems.tronkit.models.TransferContract
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class TronTransactionConverterTest {

    private val incomingTrc10Transfers = listOf(
        "1005114" to BigInteger("8888"),
        "1005157" to BigInteger("8888888"),
        "1005185" to BigInteger("4444444444"),
    )
    private val walletAddress = Address.fromRawWithoutPrefix(ByteArray(20) { 1 })
    private val externalAddress = Address.fromRawWithoutPrefix(ByteArray(20) { 2 })
    private val blockchain = Blockchain(BlockchainType.Tron, "TRON", null)
    private val baseToken = Token(
        coin = Coin(uid = "tron", name = "TRON", code = "TRX"),
        blockchain = blockchain,
        type = TokenType.Native,
        decimals = 6,
    )
    private val source = TransactionSource(
        blockchain = blockchain,
        account = mockk<Account>(relaxed = true),
        meta = null,
    )

    private val converter = TronTransactionConverter(
        coinManager = mockk<ICoinManager> {
            every { getToken(any()) } returns null
        },
        tronKitWrapper = TronKitWrapper(
            tronKit = mockk<TronKit> {
                every { address } returns walletAddress
            },
            signer = null,
        ),
        source = source,
        baseToken = baseToken,
        evmLabelManager = mockk<EvmLabelManager>(relaxed = true),
    )

    @Test
    fun transactionRecord_incomingTrc10_returnsIncomingRawValue() {
        incomingTrc10Transfers.forEach { (assetId, amount) ->
            val record = converter.transactionRecord(
                fullTransaction(
                    TransferAssetContract(
                        amount = amount,
                        assetName = assetId,
                        ownerAddress = externalAddress,
                        toAddress = walletAddress,
                    )
                )
            )

            assertEquals(TransactionRecordType.TRON_INCOMING, record.transactionRecordType)
            assertEquals(externalAddress.base58, record.from)
            assertEquals(listOf(walletAddress.base58), record.to)
            assertEquals(
                TransactionValue.RawValue(amount, TokenQuery.trc10(assetId)),
                record.value,
            )
            assertEquals(record.value, record.mainValue)
            assertFalse(record.spam)
        }
    }

    @Test
    fun transactionRecord_outgoingTrc10_returnsNegativeOutgoingRawValue() {
        val amount = BigInteger("8888888")

        val record = converter.transactionRecord(
            fullTransaction(
                TransferAssetContract(
                    amount = amount,
                    assetName = "2000001",
                    ownerAddress = walletAddress,
                    toAddress = externalAddress,
                )
            )
        )

        assertEquals(TransactionRecordType.TRON_OUTGOING, record.transactionRecordType)
        assertEquals(listOf(externalAddress.base58), record.to)
        assertEquals(
            TransactionValue.RawValue(amount.negate(), TokenQuery.trc10("2000001")),
            record.value,
        )
        assertEquals(
            TransactionValue.RawValue(amount, TokenQuery.trc10("2000001")),
            record.value?.abs,
        )
        assertFalse(record.sentToSelf)
        assertFalse(record.spam)
    }

    @Test
    fun transactionRecord_selfTrc10_marksSentToSelf() {
        val record = converter.transactionRecord(
            fullTransaction(
                TransferAssetContract(
                    amount = BigInteger.ONE,
                    assetName = "2000002",
                    ownerAddress = walletAddress,
                    toAddress = walletAddress,
                )
            )
        )

        assertEquals(TransactionRecordType.TRON_OUTGOING, record.transactionRecordType)
        assertTrue(record.sentToSelf)
    }

    @Test
    fun transactionRecord_incomingTrx_keepsCoinValueBehavior() {
        val record = converter.transactionRecord(
            fullTransaction(
                TransferContract(
                    amount = BigInteger("1500000"),
                    ownerAddress = externalAddress,
                    toAddress = walletAddress,
                )
            )
        )

        assertEquals(TransactionRecordType.TRON_INCOMING, record.transactionRecordType)
        assertEquals(TransactionValue.CoinValue(baseToken, BigDecimal("1.5")), record.value)
        assertFalse(record.spam)
    }

    @Test
    fun transactionRecord_incomingZeroTrx_keepsSpamBehavior() {
        val record = converter.transactionRecord(
            fullTransaction(
                TransferContract(
                    amount = BigInteger.ZERO,
                    ownerAddress = externalAddress,
                    toAddress = walletAddress,
                )
            )
        )

        assertEquals(TransactionRecordType.TRON_INCOMING, record.transactionRecordType)
        assertTrue(record.spam)
    }

    @Test
    fun transactionRecord_outgoingSelfTrx_returnsNegativeValueAndMarksSentToSelf() {
        val record = converter.transactionRecord(
            fullTransaction(
                TransferContract(
                    amount = BigInteger("1500000"),
                    ownerAddress = walletAddress,
                    toAddress = walletAddress,
                )
            )
        )

        assertEquals(TransactionRecordType.TRON_OUTGOING, record.transactionRecordType)
        assertEquals(TransactionValue.CoinValue(baseToken, BigDecimal("-1.5")), record.value)
        assertTrue(record.sentToSelf)
    }

    @Test
    fun transactionRecord_outgoingTrc20_keepsTokenValueBehavior() {
        val record = converter.transactionRecord(
            FullTransaction(
                transaction = transaction(),
                decoration = OutgoingTrc20Decoration(
                    contractAddress = Address.fromRawWithoutPrefix(ByteArray(20) { 3 }),
                    to = externalAddress,
                    value = BigInteger("1500000"),
                    sentToSelf = false,
                    tokenInfo = TokenInfo(
                        tokenName = "Tether",
                        tokenSymbol = "USDT",
                        tokenDecimal = 6,
                    ),
                ),
            )
        )

        val value = record.value as TransactionValue.TokenValue
        assertEquals(TransactionRecordType.TRON_OUTGOING, record.transactionRecordType)
        assertEquals(BigDecimal("-1.5"), value.value)
        assertEquals("USDT", value.tokenCode)
    }

    private fun fullTransaction(contract: Contract) =
        FullTransaction(
            transaction = transaction(),
            decoration = NativeTransactionDecoration(contract),
        )

    private fun transaction() = Transaction(
        hash = ByteArray(32) { 1 },
        timestamp = 1_700_000_000_000,
        confirmed = true,
    )
}
