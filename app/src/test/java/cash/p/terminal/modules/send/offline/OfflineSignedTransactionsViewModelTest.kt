package cash.p.terminal.modules.send.offline

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.ITransactionsAdapter
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
import cash.p.terminal.wallet.IAccountManager
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

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val walletManager = mockk<IWalletManager>(relaxed = true)
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

    private fun rawBnbEntity() = legacyBnbEntity().copy(
        sourceTokenQueryId = "binance-smart-chain|native",
        amount = "",
        toAddress = "",
        pcashPayload = "",
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
        const val USDC_QUERY_ID = "binance-smart-chain|eip20:$USDC_CONTRACT"
    }
}
