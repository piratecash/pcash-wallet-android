package cash.p.terminal.modules.send.offline

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.OfflineTransactionStatusAdapter
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.entities.OfflineSignedTransactionStatus
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.transactions.TransactionsRateRepository
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.meta
import cash.p.terminal.wallet.tokenQueryId
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSignedTransactionsViewModelTest {

    private interface TestStatusAdapter : IAdapter, OfflineTransactionStatusAdapter

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val transactionAdapterManager = mockk<TransactionAdapterManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val rateRepository = mockk<TransactionsRateRepository>(relaxed = true)

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { marketKit.token(any()) } returns null
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns emptyList()
        every { transactionAdapterManager.adaptersReadyFlow } returns MutableStateFlow(emptyMap())
        every { rateRepository.getHistoricalRate(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun init_tokenQueryNotResolved_rendersStoredTokenMetadata() = runTest(dispatcher) {
        setupState(entities = listOf(usdcEntity()), wallets = emptyList())

        val viewModel = viewModel(this)
        advanceUntilIdle()

        val item = viewModel.uiState.items.single()
        assertEquals("USDC", item.transactionItem.record.token.coin.code)
        assertEquals("USD Coin", item.transactionItem.record.token.coin.name)
        assertEquals("usd-coin", item.transactionItem.record.token.coin.uid)
        assertEquals(6, item.transactionItem.record.token.decimals)
        assertEquals(BlockchainType.BinanceSmartChain, item.transactionItem.record.token.blockchainType)
        assertEquals("1.234567", item.transactionItem.record.mainValue?.decimalValue?.abs()?.toPlainString())
    }

    @Test
    fun init_zcashSignedRecord_displaysAmountWithFee() = runTest(dispatcher) {
        val zcashWallet = wallet(zcashToken)
        setupState(entities = listOf(zcashEntity()), wallets = listOf(zcashWallet))

        val viewModel = viewModel(this)
        advanceUntilIdle()

        val displayAmount = viewModel.uiState.items.single()
            .transactionItem.record.mainValue?.decimalValue?.abs()

        assertEquals("0.001311", displayAmount?.stripTrailingZeros()?.toPlainString())
    }

    @Test
    fun init_nonZcashNativeSignedRecordWithSameFeeToken_displaysAmountWithoutFee() = runTest(dispatcher) {
        val bnbWallet = wallet(bnbToken)
        setupState(entities = listOf(nativeBnbEntity()), wallets = listOf(bnbWallet))

        val viewModel = viewModel(this)
        advanceUntilIdle()

        val displayAmount = viewModel.uiState.items.single()
            .transactionItem.record.mainValue?.decimalValue?.abs()

        assertEquals("1.5", displayAmount?.stripTrailingZeros()?.toPlainString())
    }

    @Test
    fun init_displayTokenResolvedWithNativeSource_rendersDisplayTokenAndUsesSourceWallet() = runTest(dispatcher) {
        val bnbWallet = wallet(bnbToken)
        every { marketKit.token(requireNotNull(TokenQuery.fromId(USDC_QUERY_ID))) } returns usdcToken
        setupState(entities = listOf(usdcEntity()), wallets = listOf(bnbWallet))

        val viewModel = viewModel(this)
        advanceUntilIdle()

        val item = viewModel.uiState.items.single()
        assertEquals("USDC", item.transactionItem.record.token.coin.code)
        assertEquals(bnbWallet.transactionSource, item.transactionItem.record.source)
        assertEquals(bnbWallet.tokenQueryId, item.transactionItem.walletUid)
    }

    @Test
    fun init_pendingRecordConfirmedBySourceAdapter_marksBroadcasted() = runTest(dispatcher) {
        val bnbWallet = wallet(bnbToken)
        val adapter = mockk<ITransactionsAdapter>(relaxed = true) {
            every { getTransactionRecordsFlow(null, any(), null) } returns flowOf(
                listOf(record(transactionHash = "0x$TX_HASH", token = bnbToken))
            )
        }
        every { transactionAdapterManager.adaptersReadyFlow } returns MutableStateFlow(
            mapOf(bnbWallet.transactionSource to adapter)
        )
        every { marketKit.token(requireNotNull(TokenQuery.fromId(USDC_QUERY_ID))) } returns usdcToken
        setupState(entities = listOf(usdcEntity()), wallets = listOf(bnbWallet))

        viewModel(this)
        advanceUntilIdle()

        coVerify {
            repository.markBroadcasted(
                accountId = account.id,
                txHash = TX_HASH,
                confirmedTxHash = TX_HASH,
            )
        }
    }

    @Test
    fun init_pendingSolanaRecordConfirmedBySourceAdapter_preservesSignatureCase() = runTest(dispatcher) {
        val solanaWallet = wallet(solanaToken)
        val adapter = mockk<ITransactionsAdapter>(relaxed = true) {
            every { getTransactionRecordsFlow(null, any(), null) } returns flowOf(
                listOf(record(transactionHash = SOLANA_SIGNATURE, token = solanaToken))
            )
        }
        every { transactionAdapterManager.adaptersReadyFlow } returns MutableStateFlow(
            mapOf(solanaWallet.transactionSource to adapter)
        )
        every { marketKit.token(requireNotNull(TokenQuery.fromId(SOLANA_QUERY_ID))) } returns solanaToken
        setupState(entities = listOf(solanaEntity()), wallets = listOf(solanaWallet))

        viewModel(this)
        advanceUntilIdle()

        coVerify {
            repository.markBroadcasted(
                accountId = account.id,
                txHash = SOLANA_SIGNATURE,
                confirmedTxHash = SOLANA_SIGNATURE,
            )
        }
    }

    @Test
    fun init_pendingTonRecordConfirmedByStatusAdapter_marksBroadcasted() = runTest(dispatcher) {
        val tonWallet = wallet(tonToken)
        val transactionsAdapter = mockk<ITransactionsAdapter>(relaxed = true) {
            every { getTransactionRecordsFlow(null, any(), null) } returns flowOf(emptyList())
        }
        val statusAdapter = mockk<TestStatusAdapter>(relaxed = true)
        coEvery { statusAdapter.transactionExists(TON_MESSAGE_HASH) } returns true
        every { adapterManager.getAdapterForWalletOld(tonWallet) } returns statusAdapter
        every { transactionAdapterManager.adaptersReadyFlow } returns MutableStateFlow(
            mapOf(tonWallet.transactionSource to transactionsAdapter)
        )
        every { marketKit.token(requireNotNull(TokenQuery.fromId(TON_QUERY_ID))) } returns tonToken
        setupState(entities = listOf(tonEntity()), wallets = listOf(tonWallet))

        viewModel(this)
        advanceUntilIdle()

        coVerify {
            repository.markBroadcasted(
                accountId = account.id,
                txHash = TON_MESSAGE_HASH,
                confirmedTxHash = TON_MESSAGE_HASH,
            )
        }
    }

    @Test
    fun init_pendingStellarRecordConfirmedByStatusAdapter_marksBroadcasted() = runTest(dispatcher) {
        val stellarWallet = wallet(stellarToken)
        val transactionsAdapter = mockk<ITransactionsAdapter>(relaxed = true) {
            every { getTransactionRecordsFlow(null, any(), null) } returns flowOf(emptyList())
        }
        val statusAdapter = mockk<TestStatusAdapter>(relaxed = true)
        coEvery { statusAdapter.transactionExists(STELLAR_TX_HASH) } returns true
        every { adapterManager.getAdapterForWalletOld(stellarWallet) } returns statusAdapter
        every { transactionAdapterManager.adaptersReadyFlow } returns MutableStateFlow(
            mapOf(stellarWallet.transactionSource to transactionsAdapter)
        )
        every { marketKit.token(requireNotNull(TokenQuery.fromId(STELLAR_QUERY_ID))) } returns stellarToken
        setupState(entities = listOf(stellarEntity()), wallets = listOf(stellarWallet))

        viewModel(this)
        advanceUntilIdle()

        coVerify {
            repository.markBroadcasted(
                accountId = account.id,
                txHash = STELLAR_TX_HASH,
                confirmedTxHash = STELLAR_TX_HASH,
            )
        }
    }

    @Test
    fun init_legacyEntity_resolvesNativeTokenByStoredCoinFields() = runTest(dispatcher) {
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(bnbToken)
        setupState(entities = listOf(legacyBnbEntity()), wallets = emptyList())

        val viewModel = viewModel(this)
        advanceUntilIdle()

        val item = viewModel.uiState.items.single()
        assertEquals("BNB", item.transactionItem.record.token.coin.code)
        assertEquals("1.5", item.transactionItem.record.mainValue?.decimalValue?.abs()?.toPlainString())
    }

    @Test
    fun init_plainRawEntity_rendersSourceTokenWithUnknownMetadata() = runTest(dispatcher) {
        val bnbWallet = wallet(bnbToken)
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(bnbToken)
        setupState(entities = listOf(rawBnbEntity()), wallets = listOf(bnbWallet))

        val viewModel = viewModel(this)
        advanceUntilIdle()

        val item = viewModel.uiState.items.single()
        assertEquals("BNB", item.transactionItem.record.token.coin.code)
        assertEquals(BigDecimal.ZERO, item.transactionItem.record.mainValue?.decimalValue?.abs())
        assertEquals(true, item.metadataUnknown)
    }

    private fun viewModel(scope: CoroutineScope) = OfflineSignedTransactionsViewModel(
        repository = repository,
        accountManager = accountManager,
        walletManager = walletManager,
        adapterManager = adapterManager,
        transactionAdapterManager = transactionAdapterManager,
        marketKit = marketKit,
        rateRepository = rateRepository,
        dispatcherProvider = TestDispatcherProvider(dispatcher, scope),
    )

    private fun setupState(
        entities: List<OfflineSignedTransactionEntity>,
        wallets: List<Wallet>,
    ) {
        every { accountManager.activeAccountStateFlow } returns MutableStateFlow(
            ActiveAccountState.ActiveAccount(account)
        )
        every { walletManager.activeWalletsFlow } returns MutableStateFlow(wallets)
        every { repository.observe(account.id) } returns flowOf(entities)
    }

    private fun usdcEntity() = OfflineSignedTransactionEntity(
        accountId = account.id,
        txHash = TX_HASH,
        blockchainTypeUid = "binance-smart-chain",
        tokenQueryId = USDC_QUERY_ID,
        sourceTokenQueryId = "binance-smart-chain|native",
        coinUid = "usd-coin",
        coinCode = "USDC",
        coinName = "USD Coin",
        tokenDecimals = 6,
        amount = "1.234567",
        feeTokenQueryId = "binance-smart-chain|native",
        feeAtomic = "1000000000000000",
        solanaBlockHash = null,
        solanaLastValidBlockHeight = null,
        tonValidUntil = null,
        tonSenderAddress = null,
        tonSeqno = null,
        tronExpiration = null,
        stellarSourceAccountId = null,
        stellarSequenceNumber = null,
        stellarValidUntil = null,
        toAddress = "0xReceiver",
        rawHex = "deadbeef",
        pcashPayload = "pcash:tx:v1:binance-smart-chain:body",
        createdAt = 1_700_000_000_000L,
        status = OfflineSignedTransactionStatus.Pending.value,
        broadcastAttempts = 0,
        lastBroadcastAt = null,
        broadcastedAt = null,
        lastError = null,
    )

    private fun legacyBnbEntity() = usdcEntity().copy(
        tokenQueryId = "",
        sourceTokenQueryId = "",
        coinUid = "binance-coin",
        coinCode = "BNB",
        coinName = "BNB",
        tokenDecimals = 18,
        amount = "1.5",
        feeTokenQueryId = null,
        feeAtomic = null,
    )

    private fun nativeBnbEntity() = usdcEntity().copy(
        tokenQueryId = BNB_QUERY_ID,
        sourceTokenQueryId = BNB_QUERY_ID,
        coinUid = "binance-coin",
        coinCode = "BNB",
        coinName = "BNB",
        tokenDecimals = 18,
        amount = "1.5",
        feeTokenQueryId = BNB_QUERY_ID,
        feeAtomic = "1000000000000000",
    )

    private fun rawBnbEntity() = legacyBnbEntity().copy(
        sourceTokenQueryId = "binance-smart-chain|native",
        amount = "",
        toAddress = "",
        pcashPayload = "",
    )

    private fun solanaEntity() = usdcEntity().copy(
        txHash = SOLANA_SIGNATURE,
        blockchainTypeUid = "solana",
        tokenQueryId = SOLANA_QUERY_ID,
        sourceTokenQueryId = SOLANA_QUERY_ID,
        coinUid = "solana",
        coinCode = "SOL",
        coinName = "Solana",
        tokenDecimals = 9,
        amount = "1.5",
        feeTokenQueryId = SOLANA_QUERY_ID,
        feeAtomic = "5000",
        toAddress = "solana-address",
        rawHex = "deadbeef",
        pcashPayload = "pcash:tx:v1:solana:body",
        solanaBlockHash = "block-hash",
        solanaLastValidBlockHeight = 123L,
    )

    private fun tonEntity() = usdcEntity().copy(
        txHash = TON_MESSAGE_HASH,
        blockchainTypeUid = "the-open-network",
        tokenQueryId = TON_QUERY_ID,
        sourceTokenQueryId = TON_QUERY_ID,
        coinUid = "toncoin",
        coinCode = "TON",
        coinName = "Toncoin",
        tokenDecimals = 9,
        amount = "1.2",
        feeTokenQueryId = TON_QUERY_ID,
        feeAtomic = "10000000",
        toAddress = "EQReceiver",
        pcashPayload = "pcash:tx:v1:ton:body",
        tonValidUntil = 1_700_000_300L,
        tonSenderAddress = "EQSender",
        tonSeqno = 7,
    )

    private fun stellarEntity() = usdcEntity().copy(
        txHash = STELLAR_TX_HASH,
        blockchainTypeUid = "stellar",
        tokenQueryId = STELLAR_QUERY_ID,
        sourceTokenQueryId = STELLAR_QUERY_ID,
        coinUid = "stellar",
        coinCode = "XLM",
        coinName = "Stellar",
        tokenDecimals = 7,
        amount = "1.2",
        feeTokenQueryId = STELLAR_QUERY_ID,
        feeAtomic = "100",
        toAddress = "GReceiver",
        pcashPayload = "pcash:tx:v1:stellar:body",
        stellarSourceAccountId = "GSource",
        stellarSequenceNumber = 123_456_789L,
        stellarValidUntil = 1_700_000_180L,
    )

    private fun zcashEntity() = usdcEntity().copy(
        txHash = ZCASH_TX_HASH,
        blockchainTypeUid = "zcash",
        tokenQueryId = ZCASH_QUERY_ID,
        sourceTokenQueryId = ZCASH_QUERY_ID,
        coinUid = "zcash",
        coinCode = "ZEC",
        coinName = "Zcash",
        tokenDecimals = 8,
        amount = "0.001211",
        feeTokenQueryId = ZCASH_QUERY_ID,
        feeAtomic = "10000",
        toAddress = "u1recipient",
        pcashPayload = "pcash:tx:v1:zcash:body",
    )

    private fun record(
        transactionHash: String,
        token: Token,
    ): TransactionRecord =
        PendingTransactionRecord(
            uid = "record:$transactionHash",
            transactionHash = transactionHash,
            timestamp = 1_700_000_000L,
            source = TransactionSource(token.blockchain, account, token.type.meta),
            token = token,
            amount = BigDecimal.ONE,
            toAddress = "0xReceiver",
            fromAddress = "",
            expiresAt = Long.MAX_VALUE,
            memo = null,
        )

    private fun wallet(token: Token): Wallet {
        val testAccount = account
        return mockk(relaxed = true) {
            every { this@mockk.token } returns token
            every { this@mockk.account } returns testAccount
            every { this@mockk.hardwarePublicKey } returns null
        }
    }

    private val bsc = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", null)

    private val bnbToken = Token(
        coin = Coin(uid = "binance-coin", name = "BNB", code = "BNB"),
        blockchain = bsc,
        type = TokenType.Native,
        decimals = 18,
    )

    private val usdcToken = Token(
        coin = Coin(uid = "usd-coin", name = "USD Coin", code = "USDC"),
        blockchain = bsc,
        type = TokenType.Eip20(USDC_CONTRACT),
        decimals = 6,
    )

    private val solana = Blockchain(BlockchainType.Solana, "Solana", null)

    private val solanaToken = Token(
        coin = Coin(uid = "solana", name = "Solana", code = "SOL"),
        blockchain = solana,
        type = TokenType.Native,
        decimals = 9,
    )
    private val ton = Blockchain(BlockchainType.Ton, "TON", null)
    private val tonToken = Token(
        coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
        blockchain = ton,
        type = TokenType.Native,
        decimals = 9,
    )
    private val stellar = Blockchain(BlockchainType.Stellar, "Stellar", null)
    private val stellarToken = Token(
        coin = Coin(uid = "stellar", name = "Stellar", code = "XLM"),
        blockchain = stellar,
        type = TokenType.Native,
        decimals = 7,
    )
    private val zcash = Blockchain(BlockchainType.Zcash, "Zcash", null)
    private val zcashToken = Token(
        coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
        blockchain = zcash,
        type = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified),
        decimals = 8,
    )

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    private companion object {
        const val TX_HASH = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        const val USDC_CONTRACT = "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d"
        const val BNB_QUERY_ID = "binance-smart-chain|native"
        const val USDC_QUERY_ID = "binance-smart-chain|eip20:$USDC_CONTRACT"
        const val SOLANA_QUERY_ID = "solana|native"
        const val TON_QUERY_ID = "the-open-network|native"
        const val STELLAR_QUERY_ID = "stellar|native"
        const val ZCASH_QUERY_ID = "zcash|address_spec_type:unified"
        const val TON_MESSAGE_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val STELLAR_TX_HASH = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        const val ZCASH_TX_HASH = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
        const val SOLANA_SIGNATURE =
            "7jMAQMhBNsY4eqqGVRYP9ddHbR1vrMvF5qWZbGzMbfyqGzHGmhrxXfQnk74T9JbX8FD9Fyi7Jw1pB8HgZCkP1KKL"
    }
}
