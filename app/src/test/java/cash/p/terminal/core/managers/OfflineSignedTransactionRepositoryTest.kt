package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.OfflineSignedTransactionDao
import cash.p.terminal.entities.DecodedOfflineTransaction
import cash.p.terminal.entities.OfflineFeeMetadata
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.entities.OfflineSolanaRetryMetadata
import cash.p.terminal.entities.OfflineTonRetryMetadata
import cash.p.terminal.entities.OfflineTokenMetadata
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSignedTransactionRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dao = mockk<OfflineSignedTransactionDao>()

    @Test
    fun saveImported_eip20PayloadWithNativeRelay_savesDisplayTokenAndSourceTokenSeparately() =
        runTest(dispatcher) {
            val repository = repository()
            val entitySlot = slot<OfflineSignedTransactionEntity>()
            coEvery { dao.insertIfAbsent(capture(entitySlot)) } returns Unit
            val relayWallet = wallet(nativeBnbToken())

            repository.saveImported(
                wallet = relayWallet,
                decoded = decodedUsdcTransaction(),
                pcashPayload = "pcash:tx:v1:binance-smart-chain:body",
            )

            val entity = entitySlot.captured
            assertEquals("binance-smart-chain", entity.blockchainTypeUid)
            assertEquals(USDC_QUERY_ID, entity.tokenQueryId)
            assertEquals("binance-smart-chain|native", entity.sourceTokenQueryId)
            assertEquals("usd-coin", entity.coinUid)
            assertEquals("USDC", entity.coinCode)
            assertEquals("USD Coin", entity.coinName)
            assertEquals(6, entity.tokenDecimals)
            assertEquals("1.234567", entity.amount)
            assertEquals("binance-smart-chain|native", entity.feeTokenQueryId)
            assertEquals("1000000000000000", entity.feeAtomic)
            coVerify { dao.insertIfAbsent(any()) }
        }

    @Test
    fun save_localEip20Draft_savesDisplayTokenAndNativeFeeToken() = runTest(dispatcher) {
        val repository = repository()
        val entitySlot = slot<OfflineSignedTransactionEntity>()
        coEvery { dao.insertIfAbsent(capture(entitySlot)) } returns Unit
        val usdcToken = usdcToken()

        repository.save(
            draft = OfflineSignedTransactionDraft(
                wallet = wallet(usdcToken),
                amount = BigDecimal("1.234567"),
                fee = BigDecimal("0.001"),
                toAddress = "0xReceiver",
                rawHex = "deadbeef",
                txHash = TX_HASH,
                inputOutpoints = emptyList(),
                createdAt = CREATED_AT,
                feeToken = nativeBnbToken(),
            ),
            pcashPayload = "pcash:tx:v1:binance-smart-chain:body",
        )

        val entity = entitySlot.captured
        assertEquals(USDC_QUERY_ID, entity.tokenQueryId)
        assertEquals(USDC_QUERY_ID, entity.sourceTokenQueryId)
        assertEquals("USDC", entity.coinCode)
        assertEquals(6, entity.tokenDecimals)
        assertEquals("1.234567", entity.amount)
        assertEquals("binance-smart-chain|native", entity.feeTokenQueryId)
        assertEquals("1000000000000000", entity.feeAtomic)
    }

    @Test
    fun saveRawImported_plainRaw_savesSourceTokenWithoutDisplayTokenIdentity() = runTest(dispatcher) {
        val repository = repository()
        val entitySlot = slot<OfflineSignedTransactionEntity>()
        coEvery { dao.insertIfAbsent(capture(entitySlot)) } returns Unit

        repository.saveRawImported(
            wallet = wallet(nativeBnbToken()),
            rawHex = "deadbeef",
            txHash = TX_HASH,
        )

        val entity = entitySlot.captured
        assertEquals("", entity.tokenQueryId)
        assertEquals("binance-smart-chain|native", entity.sourceTokenQueryId)
        assertEquals("BNB", entity.coinCode)
        assertEquals("", entity.amount)
        assertEquals("", entity.toAddress)
        assertEquals("", entity.pcashPayload)
    }

    @Test
    fun save_localSolanaDraft_savesRetryMetadata() = runTest(dispatcher) {
        val repository = repository()
        val entitySlot = slot<OfflineSignedTransactionEntity>()
        coEvery { dao.insertIfAbsent(capture(entitySlot)) } returns Unit
        val retryMetadata = OfflineSolanaRetryMetadata(
            blockHash = "block-hash",
            lastValidBlockHeight = 123L,
        )

        repository.save(
            draft = OfflineSignedTransactionDraft(
                wallet = wallet(solanaToken()),
                amount = BigDecimal("1.2"),
                fee = BigDecimal("0.000005"),
                toAddress = "solana-address",
                rawHex = "deadbeef",
                txHash = "solana-signature",
                inputOutpoints = emptyList(),
                feeToken = solanaToken(),
                solanaRetryMetadata = retryMetadata,
            ),
            pcashPayload = "pcash:tx:v1:solana:body",
        )

        val entity = entitySlot.captured
        assertEquals("block-hash", entity.solanaBlockHash)
        assertEquals(123L, entity.solanaLastValidBlockHeight)
    }

    @Test
    fun save_localTonDraft_savesRetryMetadata() = runTest(dispatcher) {
        val repository = repository()
        val entitySlot = slot<OfflineSignedTransactionEntity>()
        coEvery { dao.insertIfAbsent(capture(entitySlot)) } returns Unit
        val retryMetadata = tonRetryMetadata()

        repository.save(
            draft = OfflineSignedTransactionDraft(
                wallet = wallet(tonToken()),
                amount = BigDecimal("1.2"),
                fee = BigDecimal("0.01"),
                toAddress = "EQReceiver",
                rawHex = "deadbeef",
                txHash = TX_HASH,
                inputOutpoints = emptyList(),
                feeToken = tonToken(),
                tonRetryMetadata = retryMetadata,
            ),
            pcashPayload = "pcash:tx:v1:ton:body",
        )

        val entity = entitySlot.captured
        assertEquals(1_700_000_300L, entity.tonValidUntil)
        assertEquals("EQSender", entity.tonSenderAddress)
        assertEquals(7, entity.tonSeqno)
    }

    @Test
    fun saveImported_tonJettonPayload_savesDisplayTokenAndTonRetryMetadata() = runTest(dispatcher) {
        val repository = repository()
        val entitySlot = slot<OfflineSignedTransactionEntity>()
        coEvery { dao.insertIfAbsent(capture(entitySlot)) } returns Unit

        repository.saveImported(
            wallet = wallet(tonToken()),
            decoded = decodedJettonTransaction(),
            pcashPayload = "pcash:tx:v1:ton:body",
        )

        val entity = entitySlot.captured
        assertEquals("the-open-network", entity.blockchainTypeUid)
        assertEquals(JETTON_QUERY_ID, entity.tokenQueryId)
        assertEquals("the-open-network|native", entity.sourceTokenQueryId)
        assertEquals("JET", entity.coinCode)
        assertEquals(6, entity.tokenDecimals)
        assertEquals("12.345678", entity.amount)
        assertEquals("the-open-network|native", entity.feeTokenQueryId)
        assertEquals("1", entity.feeAtomic)
        assertEquals(1_700_000_300L, entity.tonValidUntil)
        assertEquals("EQSender", entity.tonSenderAddress)
        assertEquals(7, entity.tonSeqno)
    }

    private fun decodedUsdcTransaction() = DecodedOfflineTransaction(
        blockchainUid = "binance-smart-chain",
        rawHex = "deadbeef",
        txHash = TX_HASH,
        token = OfflineTokenMetadata(
            tokenQueryId = USDC_QUERY_ID,
            coinUid = "usd-coin",
            coinCode = "USDC",
            coinName = "USD Coin",
            decimals = 6,
        ),
        amountAtomic = "1234567",
        fee = OfflineFeeMetadata(
            tokenQueryId = "binance-smart-chain|native",
            atomic = "1000000000000000",
            decimals = 18,
        ),
        toAddress = "0xReceiver",
        createdAt = CREATED_AT,
        inputOutpoints = emptyList(),
    )

    private fun decodedJettonTransaction() = DecodedOfflineTransaction(
        blockchainUid = "the-open-network",
        rawHex = "deadbeef",
        txHash = TX_HASH,
        token = OfflineTokenMetadata(
            tokenQueryId = JETTON_QUERY_ID,
            coinUid = "jetton",
            coinCode = "JET",
            coinName = "Jetton",
            decimals = 6,
        ),
        amountAtomic = "12345678",
        fee = OfflineFeeMetadata(
            tokenQueryId = "the-open-network|native",
            atomic = "1",
            decimals = 9,
        ),
        toAddress = "EQReceiver",
        createdAt = CREATED_AT,
        inputOutpoints = emptyList(),
        tonRetryMetadata = tonRetryMetadata(),
    )

    private fun CoroutineScope.repository() = OfflineSignedTransactionRepository(
        dao = dao,
        dispatcherProvider = TestDispatcherProvider(dispatcher, this),
    )

    private fun wallet(token: Token): Wallet {
        val testAccount = account
        return mockk(relaxed = true) {
            every { this@mockk.token } returns token
            every { this@mockk.account } returns testAccount
            every { this@mockk.hardwarePublicKey } returns null
        }
    }

    private fun nativeBnbToken() = Token(
        coin = Coin(uid = "binance-coin", name = "BNB", code = "BNB"),
        blockchain = bsc,
        type = TokenType.Native,
        decimals = 18,
    )

    private fun usdcToken() = Token(
        coin = Coin(uid = "usd-coin", name = "USD Coin", code = "USDC"),
        blockchain = bsc,
        type = TokenType.Eip20(USDC_CONTRACT),
        decimals = 6,
    )

    private fun solanaToken() = Token(
        coin = Coin(uid = "solana", name = "Solana", code = "SOL"),
        blockchain = Blockchain(BlockchainType.Solana, "Solana", null),
        type = TokenType.Native,
        decimals = 9,
    )

    private fun tonToken() = Token(
        coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
        blockchain = ton,
        type = TokenType.Native,
        decimals = 9,
    )

    private fun tonRetryMetadata() = OfflineTonRetryMetadata(
        validUntil = 1_700_000_300L,
        senderAddress = "EQSender",
        seqno = 7,
    )

    private val bsc = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", null)
    private val ton = Blockchain(BlockchainType.Ton, "TON", null)

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    private companion object {
        const val TX_HASH = "hash"
        const val CREATED_AT = 1_700_000_000_000L
        const val USDC_CONTRACT = "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d"
        const val USDC_QUERY_ID = "binance-smart-chain|eip20:$USDC_CONTRACT"
        const val JETTON_QUERY_ID = "the-open-network|the-open-network:EQJetton"
    }
}
