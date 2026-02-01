package cash.p.terminal.modules.balance.token

import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.balance.BalanceViewItemFactory
import cash.p.terminal.modules.balance.TotalBalance
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.TransactionViewItemFactory
import cash.p.terminal.network.pirate.domain.useCase.GetChangeNowAssociatedCoinTickerUseCase
import cash.p.terminal.premium.domain.PremiumSettings
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.managers.TransactionDisplayLevel
import cash.p.terminal.wallet.managers.TransactionHiddenState
import cash.p.terminal.wallet.tokenQueryId
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule

/**
 * Unit tests for TokenBalanceViewModel focusing on the auto-hide transactions feature (MOBILE-469).
 *
 * These tests verify that when transactionHiddenFlow emits:
 * 1. The flow is collected (transactionsService.refreshList() is called)
 * 2. Cached transactions are re-processed (refreshTransactionsFromCache effect)
 * 3. The ViewModel properly delegates to TransactionHiddenManager
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenBalanceViewModelTest : KoinTest {

    private val dispatcher = UnconfinedTestDispatcher()

    // Mocks
    private val totalBalance = mockk<TotalBalance>(relaxed = true)
    private val balanceService = mockk<TokenBalanceService>(relaxed = true)
    private val balanceViewItemFactory = mockk<BalanceViewItemFactory>(relaxed = true)
    private val transactionsService = mockk<TokenTransactionsService>(relaxed = true)
    private val transactionViewItemFactory = mockk<TransactionViewItemFactory>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>()
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val transactionHiddenManager = mockk<TransactionHiddenManager>()
    private val getChangeNowAssociatedCoinTickerUseCase = mockk<GetChangeNowAssociatedCoinTickerUseCase>()
    private val premiumSettings = mockk<PremiumSettings>()
    private val amlStatusManager = mockk<AmlStatusManager>()

    // Controllable flows
    private lateinit var transactionHiddenFlow: MutableStateFlow<TransactionHiddenState>
    private lateinit var transactionItemsFlow: MutableStateFlow<List<TransactionItem>>
    private lateinit var balanceItemFlow: MutableStateFlow<BalanceItem?>
    private lateinit var walletBalanceHiddenFlow: MutableStateFlow<Boolean>
    private lateinit var anyTransactionVisibilityChangedFlow: MutableSharedFlow<Unit>
    private lateinit var amlStatusUpdates: MutableSharedFlow<AmlStatusManager.AmlStatusUpdate>
    private lateinit var amlEnabledStateFlow: MutableStateFlow<Boolean>

    private lateinit var testWallet: Wallet

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single { mockk<cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase>(relaxed = true) }
                single { mockk<cash.p.terminal.wallet.IAdapterManager>(relaxed = true) }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        transactionHiddenFlow = MutableStateFlow(createHiddenState(hidden = false))
        transactionItemsFlow = MutableStateFlow(emptyList())
        balanceItemFlow = MutableStateFlow(null)
        walletBalanceHiddenFlow = MutableStateFlow(false)
        anyTransactionVisibilityChangedFlow = MutableSharedFlow()
        amlStatusUpdates = MutableSharedFlow()
        amlEnabledStateFlow = MutableStateFlow(false)

        testWallet = createTestWallet()

        every { transactionHiddenManager.transactionHiddenFlow } returns transactionHiddenFlow
        every { transactionHiddenManager.showAllTransactions(any()) } returns Unit
        every { transactionsService.transactionItemsFlow } returns transactionItemsFlow
        every { transactionsService.refreshList() } returns Unit
        every { balanceService.balanceItemFlow } returns balanceItemFlow
        every { balanceService.balanceItem } returns null
        every { balanceHiddenManager.walletBalanceHiddenFlow(any()) } returns walletBalanceHiddenFlow
        every { balanceHiddenManager.anyTransactionVisibilityChangedFlow } returns anyTransactionVisibilityChangedFlow
        every { amlStatusManager.statusUpdates } returns amlStatusUpdates
        every { amlStatusManager.enabledStateFlow } returns amlEnabledStateFlow
        every { amlStatusManager.isEnabled } returns false
        every { amlStatusManager.applyStatus(any()) } answers { firstArg() }
        every { premiumSettings.getAmlCheckShowAlert() } returns false
        coEvery { getChangeNowAssociatedCoinTickerUseCase(any(), any()) } returns null
        every { transactionViewItemFactory.convertToViewItemCached(any(), any()) } answers {
            createMockTransactionViewItem(firstArg<TransactionItem>().record.uid)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    // region Core Fix Tests (MOBILE-469)

    @Test
    fun transactionHiddenFlowEmits_callsRefreshList() = runTest(dispatcher) {
        // Given: ViewModel initialized
        createViewModel()
        advanceUntilIdle()

        // Clear call counts from initialization
        clearMocks(transactionsService, answers = false)

        // When: transactionHiddenFlow emits new value
        transactionHiddenFlow.value = createHiddenState(hidden = true)
        advanceUntilIdle()

        // Then: refreshList() must be called (this is the core fix)
        verify(exactly = 1) { transactionsService.refreshList() }
    }

    @Test
    fun transactionHiddenFlowEmitsMultipleTimes_callsRefreshListEachTime() = runTest(dispatcher) {
        // Given: ViewModel initialized
        createViewModel()
        advanceUntilIdle()
        clearMocks(transactionsService, answers = false)

        // When: transactionHiddenFlow emits twice
        transactionHiddenFlow.value = createHiddenState(hidden = true)
        advanceUntilIdle()
        transactionHiddenFlow.value = createHiddenState(hidden = false)
        advanceUntilIdle()

        // Then: refreshList() must be called at least twice
        verify(exactly = 2) { transactionsService.refreshList() }
    }

    @Test
    fun transactionHiddenFlowEmits_updatesTransactionsFromCache() = runTest(dispatcher) {
        // Given: cached transactions are loaded before hidden state changes
        transactionItemsFlow.value = listOf(
            createTransactionItem("tx-1"),
            createTransactionItem("tx-2")
        )
        transactionHiddenFlow.value = createHiddenState(hidden = false)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.transactions?.values?.flatten()?.size)
        assertEquals(false, viewModel.uiState.hasHiddenTransactions)

        // When: transactionHiddenFlow emits (no new transaction items)
        transactionHiddenFlow.value = createHiddenState(
            hidden = true,
            level = TransactionDisplayLevel.LAST_1_TRANSACTION
        )
        advanceUntilIdle()

        // Then: cached transactions are re-processed using the new hidden state
        assertEquals(1, viewModel.uiState.transactions?.values?.flatten()?.size)
        assertEquals(true, viewModel.uiState.hasHiddenTransactions)
    }

    // endregion

    // region Delegation Tests

    @Test
    fun showAllTransactions_delegatesToManager() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAllTransactions(true)
        verify(exactly = 1) { transactionHiddenManager.showAllTransactions(true) }

        viewModel.showAllTransactions(false)
        verify(exactly = 1) { transactionHiddenManager.showAllTransactions(false) }
    }

    // endregion

    // region Helper Methods

    private fun createViewModel(): TokenBalanceViewModel = TokenBalanceViewModel(
        totalBalance = totalBalance,
        wallet = testWallet,
        balanceService = balanceService,
        balanceViewItemFactory = balanceViewItemFactory,
        transactionsService = transactionsService,
        transactionViewItem2Factory = transactionViewItemFactory,
        balanceHiddenManager = balanceHiddenManager,
        connectivityManager = connectivityManager,
        accountManager = accountManager,
        transactionHiddenManager = transactionHiddenManager,
        getChangeNowAssociatedCoinTickerUseCase = getChangeNowAssociatedCoinTickerUseCase,
        premiumSettings = premiumSettings,
        amlStatusManager = amlStatusManager
    )

    private fun createHiddenState(
        hidden: Boolean,
        level: TransactionDisplayLevel = TransactionDisplayLevel.LAST_2_TRANSACTIONS
    ) = TransactionHiddenState(
        transactionHidden = hidden,
        transactionHideEnabled = true,
        transactionDisplayLevel = level,
        transactionAutoHidePinExists = false
    )

    private fun createTestWallet(): Wallet {
        val testCoin = Coin(uid = "test-coin", name = "Test Coin", code = "TEST")
        val testToken = Token(
            coin = testCoin,
            blockchain = Blockchain(
                type = BlockchainType.Bitcoin,
                name = "Bitcoin",
                eip3091url = null
            ),
            type = TokenType.Native,
            decimals = 8
        )
        return mockk<Wallet>(relaxed = true) {
            every { token } returns testToken
            every { coin } returns testCoin
            every { tokenQueryId } returns testToken.tokenQuery.id
        }
    }

    private fun createTransactionItem(uid: String): TransactionItem {
        val record = mockk<TransactionRecord>(relaxed = true) {
            every { this@mockk.uid } returns uid
            every { timestamp } returns 0L
        }

        return TransactionItem(
            record = record,
            currencyValue = null,
            lastBlockInfo = null,
            nftMetadata = emptyMap()
        )
    }

    private fun createMockTransactionViewItem(uid: String) = mockk<TransactionViewItem>(relaxed = true) {
        every { this@mockk.uid } returns uid
        every { formattedDate } returns "DATE"
    }

    // endregion
}
