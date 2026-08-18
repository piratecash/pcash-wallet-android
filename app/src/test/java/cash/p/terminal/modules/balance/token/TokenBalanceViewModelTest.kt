package cash.p.terminal.modules.balance.token

import cash.p.terminal.R
import cash.p.terminal.core.INativeBalanceProvider
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ISendMoneroAdapter
import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.core.managers.MarketFavoritesManager
import cash.p.terminal.core.managers.PriceManager
import cash.p.terminal.core.managers.StackingInfo
import cash.p.terminal.core.managers.StackingManager
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.balance.BalanceViewItem
import cash.p.terminal.modules.balance.BalanceViewItemFactory
import cash.p.terminal.modules.balance.SyncingProgress
import cash.p.terminal.modules.balance.TotalBalance
import cash.p.terminal.modules.balance.TotalService
import cash.p.terminal.modules.transactions.Filter
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.SearchScanState
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.TransactionViewItemFactory
import cash.p.terminal.premium.domain.PremiumSettings
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.wallet.balance.DeemedValue
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.managers.TransactionDisplayLevel
import cash.p.terminal.wallet.managers.TransactionHiddenState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.tokenQueryId
import cash.p.terminal.wallet.zcashTransparentWallet
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.toHexReversed
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import java.math.BigDecimal
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
 * Unit tests for TokenBalanceViewModel focusing on the auto-hide transactions feature.
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
    private val premiumSettings = mockk<PremiumSettings>()
    private val amlStatusManager = mockk<AmlStatusManager>()
    private val marketFavoritesManager = mockk<MarketFavoritesManager>(relaxed = true)
    private val stackingManager = mockk<StackingManager>(relaxed = true)
    private val priceManager = mockk<PriceManager>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val numberFormatter = mockk<io.horizontalsystems.core.IAppNumberFormatter>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)
    private val addressLabelsChangedFlow = MutableSharedFlow<Unit>()
    private val addressLabelManager = mockk<AddressLabelManager>(relaxed = true) {
        every { labelsChangedFlow } returns addressLabelsChangedFlow
    }

    // Controllable flows
    private lateinit var transactionHiddenFlow: MutableStateFlow<TransactionHiddenState>
    private lateinit var transactionItemsFlow: MutableStateFlow<List<TransactionItem>>
    private lateinit var balanceItemFlow: MutableStateFlow<BalanceItem?>
    private lateinit var walletBalanceHiddenFlow: MutableStateFlow<Boolean>
    private lateinit var anyTransactionVisibilityChangedFlow: MutableSharedFlow<Unit>
    private lateinit var amlStatusUpdates: MutableSharedFlow<AmlStatusManager.AmlStatusUpdate>
    private lateinit var amlEnabledStateFlow: MutableStateFlow<Boolean>
    private lateinit var syncingFlow: MutableStateFlow<Boolean>
    private lateinit var recordsLoadedFlow: MutableStateFlow<Boolean>
    private lateinit var searchScanStateFlow: MutableStateFlow<SearchScanState>
    private lateinit var nativeBalanceUpdatedFlow: MutableSharedFlow<Unit>
    private var nativeBalanceData = BalanceData(available = BigDecimal.ZERO)

    private lateinit var testWallet: Wallet

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single { mockk<UpdateSwapProviderTransactionsStatusUseCase>(relaxed = true) }
                single { adapterManager }
                single { locallyCreatedTransactionRepository }
                single {
                    mockk<SwapProviderTransactionsStorage>(relaxed = true) {
                        every { observeByToken(any(), any(), any()) } returns flowOf(emptyList())
                    }
                }
                single { mockk<cash.p.terminal.wallet.MarketKitWrapper>(relaxed = true) }
                single {
                    mockk<PoisonAddressManager>(relaxed = true) {
                        every { poisonDbChangedFlow } returns MutableSharedFlow()
                    }
                }
                single { addressLabelManager }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        CoreApp.instance = mockk(relaxed = true) {
            every { isSwapEnabled } returns false
            every { getString(any(), *anyVararg()) } returns ""
        }

        transactionHiddenFlow = MutableStateFlow(createHiddenState(hidden = false))
        transactionItemsFlow = MutableStateFlow(emptyList())
        balanceItemFlow = MutableStateFlow(null)
        walletBalanceHiddenFlow = MutableStateFlow(false)
        anyTransactionVisibilityChangedFlow = MutableSharedFlow()
        amlStatusUpdates = MutableSharedFlow()
        amlEnabledStateFlow = MutableStateFlow(false)
        nativeBalanceUpdatedFlow = MutableSharedFlow()
        nativeBalanceData = BalanceData(available = BigDecimal.ZERO)

        testWallet = createTestWallet()

        every { transactionHiddenManager.transactionHiddenFlow } returns transactionHiddenFlow
        every { transactionHiddenManager.showAllTransactions(any()) } returns Unit
        every { transactionsService.transactionItemsFlow } returns transactionItemsFlow
        syncingFlow = MutableStateFlow(true)
        recordsLoadedFlow = MutableStateFlow(false)
        every { transactionsService.syncingFlow } returns syncingFlow
        every { transactionsService.recordsLoadedFlow } returns recordsLoadedFlow
        searchScanStateFlow = MutableStateFlow(SearchScanState.Idle)
        every { transactionsService.searchScanStateFlow } returns searchScanStateFlow
        every { transactionsService.setSearchQuery(any()) } returns Unit
        every { transactionsService.refreshList() } returns Unit
        every { balanceService.balanceItemFlow } returns balanceItemFlow
        every { balanceService.balanceItem } returns null
        every { balanceHiddenManager.walletBalanceHiddenFlow(any()) } returns walletBalanceHiddenFlow
        every { balanceHiddenManager.isWalletBalanceHidden(any()) } returns false
        every { balanceHiddenManager.anyTransactionVisibilityChangedFlow } returns anyTransactionVisibilityChangedFlow
        every { contactsRepository.contactsFlow } returns MutableStateFlow(emptyList())
        every { amlStatusManager.statusUpdates } returns amlStatusUpdates
        every { amlStatusManager.enabledStateFlow } returns amlEnabledStateFlow
        every { amlStatusManager.isEnabled } returns false
        every { amlStatusManager.applyStatus(any()) } answers { firstArg() }
        every { premiumSettings.getAmlCheckShowAlert() } returns false
        every { totalBalance.stateFlow } returns MutableStateFlow(TotalService.State.Hidden)
        every { stackingManager.infoFlow(any()) } returns MutableStateFlow<StackingInfo?>(null)
        every { stackingManager.unpaidFlow(any()) } returns MutableStateFlow<BigDecimal?>(BigDecimal.ZERO)
        every { stackingManager.infoFor(any()) } returns null
        every { stackingManager.unpaidFor(any()) } returns BigDecimal.ZERO
        every { priceManager.displayPricePeriodFlow } returns MutableStateFlow(DisplayPricePeriod.ONE_DAY)
        every { priceManager.displayDiffOptionTypeFlow } returns MutableStateFlow(DisplayDiffOptionType.BOTH)
        every { localStorage.displayDiffPricePeriod } returns DisplayPricePeriod.ONE_DAY
        every { localStorage.displayDiffOptionType } returns DisplayDiffOptionType.BOTH
        every { localStorage.isRoundingAmountMainPage } returns false
        coEvery { transactionViewItemFactory.convertToViewItemCached(any(), any(), any()) } answers {
            createMockTransactionViewItem(firstArg<TransactionItem>().record.uid)
        }
        coEvery { adapterManager.awaitAdapterForWallet<IReceiveAdapter>(any(), any()) } returns null
        // The relaxed mock would return a bare Object, which fails the caller's unchecked cast.
        every { adapterManager.getAdapterForWallet<Any>(any()) } returns null
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
    fun labelsChangedFlow_emits_clearsTransactionViewItemCache() = runTest(dispatcher) {
        createViewModel()
        advanceUntilIdle()
        clearMocks(transactionViewItemFactory, answers = false)

        addressLabelsChangedFlow.emit(Unit)
        advanceUntilIdle()

        verify(exactly = 1) { transactionViewItemFactory.clearCache() }
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

    // region Sync Message Tests (MOBILE-583)

    @Test
    fun updateTransactions_emptyItemsDuringSyncing_transactionsStaysNull() = runTest(dispatcher) {
        syncingFlow.value = true
        recordsLoadedFlow.value = false

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Empty items arrive while syncing — guard should block
        transactionItemsFlow.value = emptyList()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.transactions)
        assertEquals(true, viewModel.uiState.syncing)
    }

    @Test
    fun updateTransactions_syncFinishesNoTransactions_showsEmptyNotNull() = runTest(dispatcher) {
        syncingFlow.value = true
        recordsLoadedFlow.value = false

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Records loaded, empty items emitted — guard blocks (still syncing)
        recordsLoadedFlow.value = true
        transactionItemsFlow.value = emptyList()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.transactions)

        // Sync finishes — re-trigger should set transactions to empty map
        syncingFlow.value = false
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.syncing)
        assertEquals(emptyMap<String, List<TransactionViewItem>>(), viewModel.uiState.transactions)
    }

    @Test
    fun updateTransactions_syncFinishesWithTransactions_showsTransactions() = runTest(dispatcher) {
        syncingFlow.value = true
        recordsLoadedFlow.value = false

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Non-empty items arrive — guard does not block
        recordsLoadedFlow.value = true
        transactionItemsFlow.value = listOf(createTransactionItem("tx-1"))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.transactions?.values?.flatten()?.size)

        // Sync finishes — transactions remain
        syncingFlow.value = false
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.syncing)
        assertEquals(1, viewModel.uiState.transactions?.values?.flatten()?.size)
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

    @Test
    fun balanceItemFlowEmits_swapEnabledAndRegularAccount_showsSwap() = runTest(dispatcher) {
        every { CoreApp.instance.isSwapEnabled } returns true
        testWallet = createTestWallet(account = createAccount())

        val balanceItem = createBalanceItem(wallet = testWallet)
        every { balanceService.balanceItem } returns balanceItem
        every { balanceViewItemFactory.viewItem(any(), any(), any(), any(), any(), any(), any()) } answers {
            createBalanceViewItem(swapVisible = args[5] as Boolean)
        }

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.balanceViewItem?.swapVisible)
    }

    @Test
    fun balanceItemFlowEmits_watchAccount_hidesSwap() = runTest(dispatcher) {
        every { CoreApp.instance.isSwapEnabled } returns true
        testWallet = createTestWallet(account = createAccount(isWatchAccount = true))

        val balanceItem = createBalanceItem(wallet = testWallet)
        every { balanceService.balanceItem } returns balanceItem
        every { balanceViewItemFactory.viewItem(any(), any(), any(), any(), any(), any(), any()) } answers {
            createBalanceViewItem(swapVisible = args[5] as Boolean)
        }

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.balanceViewItem?.swapVisible)
    }

    @Test
    fun balanceItemFlowEmits_nonBackedUpAccount_hidesSwap() = runTest(dispatcher) {
        every { CoreApp.instance.isSwapEnabled } returns true
        testWallet = createTestWallet(account = createAccount(hasAnyBackup = false))

        val balanceItem = createBalanceItem(wallet = testWallet)
        every { balanceService.balanceItem } returns balanceItem
        every { balanceViewItemFactory.viewItem(any(), any(), any(), any(), any(), any(), any()) } answers {
            createBalanceViewItem(swapVisible = args[5] as Boolean)
        }

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.balanceViewItem?.swapVisible)
    }

    @Test
    fun balanceItemFlowEmits_backupNotRequiredAccount_showsSwap() = runTest(dispatcher) {
        every { CoreApp.instance.isSwapEnabled } returns true
        testWallet = createTestWallet(
            account = createAccount(
                supportsBackup = false,
                hasAnyBackup = false
            )
        )

        val balanceItem = createBalanceItem(wallet = testWallet)
        every { balanceService.balanceItem } returns balanceItem
        every { balanceViewItemFactory.viewItem(any(), any(), any(), any(), any(), any(), any()) } answers {
            createBalanceViewItem(swapVisible = args[5] as Boolean)
        }

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.balanceViewItem?.swapVisible)
    }

    @Test
    fun isShowShieldFunds_transparentZcashPendingOnly_hidesShieldFunds() = runTest(dispatcher) {
        testWallet = zcashTransparentWallet()
        val balanceItem = createBalanceItem(
            wallet = testWallet,
            balanceData = BalanceData(
                available = BigDecimal.ZERO,
                pending = ZcashAdapter.MINERS_FEE + BigDecimal.ONE
            )
        )
        balanceItemFlow.value = balanceItem
        every { balanceService.balanceItem } answers { balanceItemFlow.value }
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.isShowShieldFunds)
    }

    @Test
    fun isShowShieldFunds_transparentZcashAvailableAboveFee_showsShieldFunds() = runTest(dispatcher) {
        testWallet = zcashTransparentWallet()
        val balanceItem = createBalanceItem(
            wallet = testWallet,
            balanceData = BalanceData(
                available = ZcashAdapter.MINERS_FEE + BigDecimal.ONE,
                pending = BigDecimal.ZERO
            )
        )
        balanceItemFlow.value = balanceItem
        every { balanceService.balanceItem } answers { balanceItemFlow.value }
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.isShowShieldFunds)
    }

    // endregion

    // region Secondary Value Tests (MOBILE-517)

    @Test
    fun secondaryValue_globalBalanceHiddenPerWalletRevealed_showsFiatValue() = runTest(dispatcher) {
        val expectedFiat = "$142.35"

        // Global balance hidden → TotalService emits State.Hidden
        val totalStateFlow = MutableStateFlow<TotalService.State>(TotalService.State.Hidden)
        every { totalBalance.stateFlow } returns totalStateFlow

        // balanceViewItemFactory returns a view item with real fiat value (visible)
        val balanceViewItem = createBalanceViewItem(
            secondaryValue = DeemedValue(value = expectedFiat, dimmed = false, visible = true)
        )
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns balanceViewItem
        every { balanceHiddenManager.isWalletBalanceHidden(any()) } returns false

        val testBalanceItem = createBalanceItem()
        every { balanceService.balanceItem } returns testBalanceItem

        // Create ViewModel, then emit balance data
        val viewModel = createViewModel()
        balanceItemFlow.value = testBalanceItem
        advanceUntilIdle()

        // Simulate per-wallet reveal (tap on token screen)
        walletBalanceHiddenFlow.value = false
        advanceUntilIdle()

        // Secondary value must show fiat, not be empty
        assertEquals(expectedFiat, viewModel.secondaryValue.value)
        assertEquals(true, viewModel.secondaryValue.visible)
    }

    // endregion

    // region Staking Status Tests (MOBILE-588)

    @Test
    fun stakingStatus_balanceAboveThreshold_showsActive() = runTest(dispatcher) {
        val pirateWallet = createPirateCashWallet()
        testWallet = pirateWallet

        val balanceItem = createBalanceItem(balance = BigDecimal("150"), wallet = pirateWallet)
        every { balanceService.balanceItem } returns balanceItem
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()

        val viewModel = createViewModel()
        advanceUntilIdle()

        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(TokenBalanceModule.StakingStatus.ACTIVE, viewModel.uiState.stakingStatus)
    }

    @Test
    fun stakingStatus_balanceBelowThreshold_showsInactive() = runTest(dispatcher) {
        val pirateWallet = createPirateCashWallet()
        testWallet = pirateWallet

        val balanceItem = createBalanceItem(balance = BigDecimal("1"), wallet = pirateWallet)
        every { balanceService.balanceItem } returns balanceItem
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(TokenBalanceModule.StakingStatus.INACTIVE, viewModel.uiState.stakingStatus)
    }

    @Test
    fun stakingStatus_balanceBelowThresholdWithUnpaidRewards_showsInactive() = runTest(dispatcher) {
        val pirateWallet = createPirateCashWallet()
        testWallet = pirateWallet

        // Simulate unpaid rewards from StackingManager
        val infoFlow = MutableStateFlow<StackingInfo?>(StackingInfo(unpaid = BigDecimal("0.7897")))
        every { stackingManager.infoFlow(any()) } returns infoFlow

        val balanceItem = createBalanceItem(balance = BigDecimal("1"), wallet = pirateWallet)
        every { balanceService.balanceItem } returns balanceItem
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        // MOBILE-588: Must show INACTIVE even when unpaid rewards exist
        assertEquals(TokenBalanceModule.StakingStatus.INACTIVE, viewModel.uiState.stakingStatus)
    }

    @Test
    fun stakingStatus_balanceExactlyAtThreshold_showsActive() = runTest(dispatcher) {
        val pirateWallet = createPirateCashWallet()
        testWallet = pirateWallet

        val balanceItem = createBalanceItem(balance = BigDecimal("100"), wallet = pirateWallet)
        every { balanceService.balanceItem } returns balanceItem
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(TokenBalanceModule.StakingStatus.ACTIVE, viewModel.uiState.stakingStatus)
    }

    // endregion

    // region Network Fee Warning Tests (MOBILE-526)

    @Test
    fun networkFeeWarning_nativeToken_noWarning() = runTest(dispatcher) {
        val balanceItem = createBalanceItem()
        every { balanceService.balanceItem } returns balanceItem
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.networkFeeWarning)
    }

    @Test
    fun networkFeeWarning_nonNativeTokenZeroBalance_showsWarning() = runTest(dispatcher) {
        val bep20Wallet = createBep20Wallet()
        testWallet = bep20Wallet
        setupFeeWarningMocks()

        val viewModel = createViewModel()
        val balanceItem = createBalanceItem(wallet = bep20Wallet)
        every { balanceService.balanceItem } returns balanceItem
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.networkFeeWarning != null)
    }

    @Test
    fun networkFeeWarning_balanceSyncing_doesNotShowWarning() = runTest(dispatcher) {
        val bep20Wallet = createBep20Wallet()
        testWallet = bep20Wallet
        setupFeeWarningMocks()

        val balanceItem = createBalanceItem(
            wallet = bep20Wallet,
            state = AdapterState.Syncing()
        )
        every { balanceService.balanceItem } returns balanceItem

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.networkFeeWarning)
    }

    @Test
    fun networkFeeWarning_balanceBecomesSyncedWithZeroNativeBalance_showsWarning() = runTest(dispatcher) {
        val bep20Wallet = createBep20Wallet()
        testWallet = bep20Wallet
        setupFeeWarningMocks()

        var balanceItem = createBalanceItem(
            wallet = bep20Wallet,
            state = AdapterState.Syncing()
        )
        every { balanceService.balanceItem } answers { balanceItem }

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.networkFeeWarning)

        balanceItem = balanceItem.copy(state = AdapterState.Synced)
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.networkFeeWarning != null)
    }

    @Test
    fun networkFeeWarning_resyncAfterFirstSynced_keepsWarningVisible() = runTest(dispatcher) {
        val bep20Wallet = createBep20Wallet()
        testWallet = bep20Wallet
        setupFeeWarningMocks()

        var balanceItem = createBalanceItem(
            wallet = bep20Wallet,
            state = AdapterState.Synced
        )
        every { balanceService.balanceItem } answers { balanceItem }

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.networkFeeWarning != null)

        balanceItem = balanceItem.copy(state = AdapterState.Syncing())
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.networkFeeWarning != null)
    }

    @Test
    fun networkFeeWarning_sufficientNativeBalance_noWarning() = runTest(dispatcher) {
        val bep20Wallet = createBep20Wallet()
        testWallet = bep20Wallet
        setupFeeWarningMocks()
        setupNativeBalanceMocks(nativeBalance = BigDecimal("0.1"))

        val balanceItem = createBalanceItem(wallet = bep20Wallet)
        every { balanceService.balanceItem } returns balanceItem
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.networkFeeWarning)
    }

    @Test
    fun networkFeeWarning_nativeBalanceBecomesSufficient_clearsWarning() = runTest(dispatcher) {
        val bep20Wallet = createBep20Wallet()
        testWallet = bep20Wallet
        setupFeeWarningMocks()
        setupNativeBalanceMocks(nativeBalance = BigDecimal.ZERO)

        val balanceItem = createBalanceItem(wallet = bep20Wallet)
        every { balanceService.balanceItem } returns balanceItem

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.networkFeeWarning != null)

        updateNativeBalance(BigDecimal("0.1"))
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.networkFeeWarning)
    }

    @Test
    fun networkFeeWarning_exactlyAtThreshold_noWarning() = runTest(dispatcher) {
        val tronWallet = createTrc20Wallet()
        testWallet = tronWallet
        setupNativeBalanceMocks(
            nativeBalance = BigDecimal("50"),
            nativeCoinCode = "TRX",
            blockchainType = BlockchainType.Tron,
            blockchainName = "TRON"
        )
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()

        val viewModel = createViewModel()
        val balanceItem = createBalanceItem(wallet = tronWallet)
        every { balanceService.balanceItem } returns balanceItem
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.networkFeeWarning)
    }

    @Test
    fun networkFeeWarning_justBelowThreshold_showsWarning() = runTest(dispatcher) {
        val tronWallet = createTrc20Wallet()
        testWallet = tronWallet
        setupNativeBalanceMocks(
            nativeBalance = BigDecimal("49.99"),
            nativeCoinCode = "TRX",
            blockchainType = BlockchainType.Tron,
            blockchainName = "TRON"
        )
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()

        val viewModel = createViewModel()
        val balanceItem = createBalanceItem(wallet = tronWallet)
        every { balanceService.balanceItem } returns balanceItem
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.networkFeeWarning != null)
    }

    @Test
    fun networkFeeWarning_noNativeWalletAdded_showsWarning() = runTest(dispatcher) {
        val bep20Wallet = createBep20Wallet()
        testWallet = bep20Wallet
        setupFeeWarningMocks()

        // getBalanceAdapterForWallet returns adapter that does NOT implement INativeBalanceProvider
        val mockAdapterManager = getKoin().get<cash.p.terminal.wallet.IAdapterManager>()
        every { mockAdapterManager.getBalanceAdapterForWallet(any()) } returns mockk(relaxed = true)

        val balanceItem = createBalanceItem(wallet = bep20Wallet)
        every { balanceService.balanceItem } returns balanceItem

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.networkFeeWarning != null)
    }

    @Test
    fun networkFeeWarning_dismissed_noWarning() = runTest(dispatcher) {
        val bep20Wallet = createBep20Wallet()
        testWallet = bep20Wallet
        setupFeeWarningMocks()
        every { localStorage.isNetworkFeeWarningDismissed(any()) } returns true

        val balanceItem = createBalanceItem(wallet = bep20Wallet)
        every { balanceService.balanceItem } returns balanceItem

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.networkFeeWarning)
    }

    @Test
    fun dismissNetworkFeeWarning_persistsAndClearsWarning() = runTest(dispatcher) {
        val bep20Wallet = createBep20Wallet()
        testWallet = bep20Wallet
        setupFeeWarningMocks()

        val balanceItem = createBalanceItem(wallet = bep20Wallet)
        every { balanceService.balanceItem } returns balanceItem

        val viewModel = createViewModel()
        balanceItemFlow.value = balanceItem
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.networkFeeWarning != null)

        viewModel.dismissNetworkFeeWarning()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.networkFeeWarning)
        verify { localStorage.dismissNetworkFeeWarning(BlockchainType.BinanceSmartChain.uid) }
    }

    @Test
    fun proposeShielding_success_marksLocallyCreatedTransaction() = runTest(dispatcher) {
        testWallet = createTestWallet(
            coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
            blockchainType = BlockchainType.Zcash,
            blockchainName = "Zcash",
            tokenType = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded),
        )
        val txId = FirstClassByteArray(ByteArray(32) { it.toByte() })
        val zcashAdapter = mockk<ZcashAdapter> {
            coEvery { proposeShielding() } returns txId
            every { ironwoodMigrationRequiredBalance } returns null
        }
        every { adapterManager.getAdapterForWallet<ZcashAdapter>(testWallet) } returns zcashAdapter

        val viewModel = createViewModel()

        viewModel.proposeShielding()
        advanceUntilIdle()

        coVerify {
            locallyCreatedTransactionRepository.markCreated(
                testWallet,
                txId.byteArray.toHexReversed()
            )
        }
    }

    private fun setupFeeWarningMocks() {
        val nativeToken = mockk<Token>(relaxed = true) {
            every { coin } returns Coin(uid = "bnb", name = "BNB", code = "BNB")
            every { decimals } returns 18
        }
        val mockMarketKit = getKoin().get<MarketKitWrapper>()
        every { mockMarketKit.token(any()) } returns nativeToken
        every { mockMarketKit.blockchain(any<String>()) } returns Blockchain(
            type = BlockchainType.BinanceSmartChain, name = "BNB Smart Chain", eip3091url = null
        )
        every { numberFormatter.formatCoinShort(any(), any(), any()) } returns "0"
        every {
            balanceViewItemFactory.viewItem(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns createBalanceViewItem()
    }

    private fun setupNativeBalanceMocks(
        nativeBalance: BigDecimal,
        nativeCoinCode: String = "BNB",
        blockchainType: BlockchainType = BlockchainType.BinanceSmartChain,
        blockchainName: String = "BNB Smart Chain"
    ) {
        val nativeToken = mockk<Token>(relaxed = true) {
            every { coin } returns Coin(uid = nativeCoinCode.lowercase(), name = nativeCoinCode, code = nativeCoinCode)
            every { decimals } returns 18
        }
        val mockMarketKit = getKoin().get<MarketKitWrapper>()
        every { mockMarketKit.token(any()) } returns nativeToken
        every { mockMarketKit.blockchain(any<String>()) } returns Blockchain(
            type = blockchainType, name = blockchainName, eip3091url = null
        )
        nativeBalanceData = BalanceData(available = nativeBalance)

        val mockAdapterManager = getKoin().get<IAdapterManager>()
        val balanceAdapter = object : IBalanceAdapter, INativeBalanceProvider {
            override val nativeBalanceData get() = this@TokenBalanceViewModelTest.nativeBalanceData
            override val nativeBalanceUpdatedFlow get() = this@TokenBalanceViewModelTest.nativeBalanceUpdatedFlow
            override val balanceData get() = BalanceData(available = BigDecimal.ZERO)
            override val balanceStateUpdatedFlow = MutableSharedFlow<Unit>()
            override val balanceState get() = AdapterState.Synced
            override val balanceUpdatedFlow = MutableSharedFlow<Unit>()
        }
        every { mockAdapterManager.getBalanceAdapterForWallet(any()) } returns balanceAdapter

        every { numberFormatter.formatCoinShort(any(), any(), any()) } answers {
            nativeBalanceData.total.toPlainString()
        }
        every { CoreApp.instance.getString(any(), *anyVararg()) } answers { "warning text" }
    }

    private suspend fun updateNativeBalance(nativeBalance: BigDecimal) {
        nativeBalanceData = BalanceData(available = nativeBalance)
        nativeBalanceUpdatedFlow.emit(Unit)
    }

    // region Address Poisoning View Mode Tests

    @Test
    fun refreshTransactionDisplaySettings_modeNotChanged_doesNotRefresh() = runTest(dispatcher) {
        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.STANDARD

        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(transactionViewItemFactory, answers = false)

        viewModel.refreshTransactionDisplaySettings()

        verify(exactly = 0) { transactionViewItemFactory.updateCache() }
    }

    @Test
    fun refreshTransactionDisplaySettings_modeChanged_refreshesCache() = runTest(dispatcher) {
        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.STANDARD

        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(transactionViewItemFactory, answers = false)

        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT

        viewModel.refreshTransactionDisplaySettings()

        verify(exactly = 1) { transactionViewItemFactory.updateCache() }
    }

    @Test
    fun refreshTransactionDisplaySettings_modeChangedBack_refreshesTwice() = runTest(dispatcher) {
        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.STANDARD

        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(transactionViewItemFactory, answers = false)

        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT
        viewModel.refreshTransactionDisplaySettings()

        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.STANDARD
        viewModel.refreshTransactionDisplaySettings()

        verify(exactly = 2) { transactionViewItemFactory.updateCache() }
    }

    @Test
    fun refreshTransactionDisplaySettings_calledTwiceWithoutChange_refreshesOnlyOnce() = runTest(dispatcher) {
        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.STANDARD

        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(transactionViewItemFactory, answers = false)

        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT
        viewModel.refreshTransactionDisplaySettings()
        viewModel.refreshTransactionDisplaySettings()

        verify(exactly = 1) { transactionViewItemFactory.updateCache() }
    }

    @Test
    fun refreshTransactionDisplaySettings_modeChanged_reprocessesTransactions() = runTest(dispatcher) {
        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.STANDARD

        transactionItemsFlow.value = listOf(
            createTransactionItem("tx-1"),
            createTransactionItem("tx-2")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT

        viewModel.refreshTransactionDisplaySettings()

        verify(exactly = 1) { transactionViewItemFactory.updateCache() }
        assertEquals(2, viewModel.uiState.transactions?.values?.flatten()?.size)
    }

    // endregion

    // region Transaction Filter Tests

    @Test
    fun setTransactionFiltersEnabled_true_emitsAllTypesWithAllSelected() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setTransactionFiltersEnabled(true)

        assertEquals(true, viewModel.uiState.transactionFiltersEnabled)
        assertEquals(
            FilterTransactionType.entries.size,
            viewModel.uiState.transactionFilterTypes.size
        )
        val selected = viewModel.uiState.transactionFilterTypes.single { it.selected }
        assertEquals(FilterTransactionType.All, selected.item)
        verify { localStorage.transactionFiltersEnabled = true }
    }

    @Test
    fun setTransactionFiltersEnabled_false_emitsEmptyTypes() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setTransactionFiltersEnabled(false)

        assertEquals(false, viewModel.uiState.transactionFiltersEnabled)
        assertEquals(
            emptyList<Filter<FilterTransactionType>>(),
            viewModel.uiState.transactionFilterTypes
        )
    }

    @Test
    fun setTransactionType_newType_callsServiceAndSelectsType() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setTransactionFiltersEnabled(true)

        viewModel.setTransactionType(FilterTransactionType.Incoming)

        verify(exactly = 1) { transactionsService.setTransactionType(FilterTransactionType.Incoming) }
        val selected = viewModel.uiState.transactionFilterTypes.single { it.selected }
        assertEquals(FilterTransactionType.Incoming, selected.item)
    }

    @Test
    fun transactionItems_incomingFilterProviderSwap_excludesSwap() = runTest(dispatcher) {
        coEvery {
            transactionViewItemFactory.convertToViewItemCached(any(), any(), any())
        } answers {
            val uid = firstArg<TransactionItem>().record.uid
            createMockTransactionViewItem(
                uid = uid,
                swapTransactionId = "swap-id".takeIf { uid == "swap" },
            )
        }
        val viewModel = createViewModel()
        viewModel.setTransactionType(FilterTransactionType.Incoming)

        transactionItemsFlow.value = listOf(
            createTransactionItem("swap"),
            createTransactionItem("receive"),
        )
        advanceUntilIdle()

        assertEquals(
            listOf("receive"),
            viewModel.uiState.transactions?.values?.flatten()?.map { it.uid },
        )
    }

    @Test
    fun transactionItems_outgoingFilterOnChainSwap_excludesSwap() = runTest(dispatcher) {
        coEvery {
            transactionViewItemFactory.convertToViewItemCached(any(), any(), any())
        } answers {
            val uid = firstArg<TransactionItem>().record.uid
            createMockTransactionViewItem(
                uid = uid,
                isSwap = uid == "pancake-swap",
            )
        }
        val viewModel = createViewModel()
        viewModel.setTransactionType(FilterTransactionType.Outgoing)

        transactionItemsFlow.value = listOf(
            createTransactionItem("pancake-swap"),
            createTransactionItem("send"),
        )
        advanceUntilIdle()

        assertEquals(
            listOf("send"),
            viewModel.uiState.transactions?.values?.flatten()?.map { it.uid },
        )
    }

    @Test
    fun setTransactionType_sameTypeAsCurrent_doesNotCallService() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Default selected type is All
        viewModel.setTransactionType(FilterTransactionType.All)

        verify(exactly = 0) { transactionsService.setTransactionType(any()) }
    }

    @Test
    fun setTransactionType_newType_resetsTransactionsToLoadingState() = runTest(dispatcher) {
        // Steady state: filter All loaded with one transaction, not syncing.
        recordsLoadedFlow.value = true
        syncingFlow.value = false
        transactionItemsFlow.value = listOf(createTransactionItem("a1"))

        val viewModel = createViewModel()
        viewModel.setTransactionFiltersEnabled(true)
        advanceUntilIdle()

        // Sanity: the previous filter's list is shown and we are not syncing.
        assertEquals(1, viewModel.uiState.transactions?.values?.flatten()?.size)
        assertEquals(false, viewModel.uiState.syncing)

        // Switching filter opens a loading window: the new filter's data is not ready yet.
        viewModel.setTransactionType(FilterTransactionType.Incoming)

        // It must show "please wait" (transactions == null, syncing == true), never flash
        // "you don't have" (emptyMap + !syncing) before the new list arrives.
        assertEquals(null, viewModel.uiState.transactions)
        assertEquals(true, viewModel.uiState.syncing)
    }

    @Test
    fun setTransactionFiltersEnabled_falseAfterFilterSelected_resetsToAll() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setTransactionFiltersEnabled(true)
        viewModel.setTransactionType(FilterTransactionType.Outgoing)
        clearMocks(transactionsService, answers = false)

        viewModel.setTransactionFiltersEnabled(false)

        // Disabling resets the active filter back to All through the service
        verify(exactly = 1) { transactionsService.setTransactionType(FilterTransactionType.All) }
        assertEquals(false, viewModel.uiState.transactionFiltersEnabled)
    }

    // endregion

    // region Search Tests

    @Test
    fun onSearchQueryChange_debounced_appliesQueryAndUpdatesUiState() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchClick()
        viewModel.onSearchQueryChange("needle")

        // Debounce has not elapsed yet: query is reflected immediately, service is not called yet.
        assertEquals(true, viewModel.uiState.searchActive)
        assertEquals("needle", viewModel.uiState.searchQuery)
        verify(exactly = 0) { transactionsService.setSearchQuery(any()) }

        advanceUntilIdle()

        verify(exactly = 1) { transactionsService.setSearchQuery("needle") }
    }

    @Test
    fun onSearchQueryChange_matchArrivesAfterScanning_showsResultOnlyWhenFinished() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchClick()
        viewModel.onSearchQueryChange("needle")
        advanceUntilIdle()
        verify(exactly = 1) { transactionsService.setSearchQuery("needle") }

        // The service starts (possibly deep-)scanning: nothing must be shown but the spinner,
        // even though the loading-window reset already made transactionItemsFlow empty.
        searchScanStateFlow.value = SearchScanState.Scanning
        advanceUntilIdle()

        assertEquals(emptyMap<String, List<TransactionViewItem>>(), viewModel.uiState.transactions)
        assertEquals(true, viewModel.uiState.searchScanning)
        assertEquals(false, viewModel.uiState.searchEmptyResult)

        // The scan finds a match buried deeper than the first batch and reports its single
        // terminal (Finished) emission - only then must the result be shown.
        transactionItemsFlow.value = listOf(createTransactionItem("match-1"))
        searchScanStateFlow.value = SearchScanState.Finished
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.transactions?.values?.flatten()?.size)
        assertEquals(false, viewModel.uiState.searchScanning)
        assertEquals(false, viewModel.uiState.searchEmptyResult)
    }

    @Test
    fun searchEmptyResult_whileScanning_staysFalseUntilScanFinishes() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchClick()
        viewModel.onSearchQueryChange("nothing-matches")
        advanceUntilIdle()

        searchScanStateFlow.value = SearchScanState.Scanning
        advanceUntilIdle()

        // Mid-scan the list is empty, but this must never be reported as a final "no results" -
        // the scan is still in progress and could still find a match on a deeper page.
        assertEquals(true, viewModel.uiState.searchScanning)
        assertEquals(false, viewModel.uiState.searchEmptyResult)

        // The scan is exhausted with nothing found - only now is the empty result final.
        searchScanStateFlow.value = SearchScanState.Finished
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.searchScanning)
        assertEquals(true, viewModel.uiState.searchEmptyResult)
    }

    @Test
    fun onSearchClose_afterActiveSearch_reopensLoadingWindowThenShowsFullList() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchClick()
        viewModel.onSearchQueryChange("needle")
        advanceUntilIdle()
        searchScanStateFlow.value = SearchScanState.Scanning
        advanceUntilIdle()
        transactionItemsFlow.value = listOf(createTransactionItem("match-1"))
        searchScanStateFlow.value = SearchScanState.Finished
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.transactions?.values?.flatten()?.size)

        viewModel.onSearchClose()
        advanceUntilIdle()

        // Closing the search must reopen the loading window rather than flashing the stale
        // search result or an empty list before the full reload lands.
        verify(exactly = 1) { transactionsService.setSearchQuery(null) }
        assertEquals(false, viewModel.uiState.searchActive)
        assertEquals("", viewModel.uiState.searchQuery)

        // The reload lands with the full (unfiltered) list restored, sync reported complete.
        searchScanStateFlow.value = SearchScanState.Idle
        recordsLoadedFlow.value = true
        transactionItemsFlow.value = listOf(createTransactionItem("full-1"), createTransactionItem("full-2"))
        syncingFlow.value = false
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.transactions?.values?.flatten()?.size)
        assertEquals(false, viewModel.uiState.syncing)
    }

    @Test
    fun setTransactionType_duringActiveSearch_preservesSearchUiStateAndOpensLoadingWindow() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setTransactionFiltersEnabled(true)

        viewModel.onSearchClick()
        viewModel.onSearchQueryChange("needle")
        advanceUntilIdle()
        verify(exactly = 1) { transactionsService.setSearchQuery("needle") }

        viewModel.setTransactionType(FilterTransactionType.Incoming)

        // The filter switch opens its own loading window...
        assertEquals(null, viewModel.uiState.transactions)
        assertEquals(true, viewModel.uiState.syncing)
        // ...without touching the still-active search.
        assertEquals(true, viewModel.uiState.searchActive)
        assertEquals("needle", viewModel.uiState.searchQuery)
        verify(exactly = 1) { transactionsService.setTransactionType(FilterTransactionType.Incoming) }
    }

    // endregion

    // region Swap Filter & Pagination (PR #438)

    @Test
    fun updateTransactions_allSwapsOnIncomingFilter_requestsNextPage() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        // Incoming/Outgoing hide swaps, so a page of only swaps yields no visible row.
        viewModel.setTransactionType(FilterTransactionType.Incoming)
        markSwapsByUidPrefix()
        clearMocks(transactionsService, answers = false)

        transactionItemsFlow.value = listOf(
            createTransactionItem("swap-1"),
            createTransactionItem("swap-2"),
            createTransactionItem("swap-3")
        )
        advanceUntilIdle()

        // No visible row can reach the list bottom, so paging must be requested to keep loading.
        verify(exactly = 1) { transactionsService.loadNext() }
        assertEquals(emptyMap<String, List<TransactionViewItem>>(), viewModel.uiState.transactions)
    }

    @Test
    fun updateTransactions_visibleTransferOnIncomingFilter_doesNotRequestNextPage() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setTransactionType(FilterTransactionType.Incoming)
        markSwapsByUidPrefix()
        clearMocks(transactionsService, answers = false)

        transactionItemsFlow.value = listOf(
            createTransactionItem("swap-1"),
            createTransactionItem("real-1")
        )
        advanceUntilIdle()

        // A visible transfer is present, so the list can page on its own - no forced load.
        verify(exactly = 0) { transactionsService.loadNext() }
        assertEquals(1, viewModel.uiState.transactions?.values?.flatten()?.size)
    }

    @Test
    fun updateTransactions_hiddenLimitWithSwaps_countsOnlyVisibleTransfers() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setTransactionType(FilterTransactionType.Incoming)
        markSwapsByUidPrefix()
        transactionHiddenFlow.value = createHiddenState(
            hidden = true,
            level = TransactionDisplayLevel.LAST_1_TRANSACTION
        )

        // Newest first: the leading swap must not consume the "last 1" quota.
        transactionItemsFlow.value = listOf(
            createTransactionItem("swap-1"),
            createTransactionItem("real-1"),
            createTransactionItem("real-2")
        )
        advanceUntilIdle()

        val shown = viewModel.uiState.transactions?.values?.flatten().orEmpty()
        assertEquals(1, shown.size)
        // The kept row is the newest visible transfer, not the filtered-out swap.
        assertEquals("real-1", shown.single().uid)
        // Two transfers are visible but only one is shown, so more are hidden.
        assertEquals(true, viewModel.uiState.hasHiddenTransactions)
    }

    // endregion

    // region Monero Trezor send preparation

    @Test
    fun moneroReadiness_keyImageSyncRequired_enablesSendEntry() = runTest(dispatcher) {
        setMoneroWallet()
        val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
        val adapter = mockMoneroAdapter(readiness)
        every {
            adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
        } returns adapter

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.moneroKeyImageSyncRequired)
        assertEquals(true, viewModel.uiState.sendEntryEnabled)
    }

    @Test
    fun syncMoneroKeyImages_success_emitsOpenSendEvent() = runTest(dispatcher) {
        setMoneroWallet()
        val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
        val adapter = mockMoneroAdapter(readiness)
        every {
            adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
        } returns adapter
        coEvery { adapter.refreshHardwareKeyImages() } answers {
            readiness.value = MoneroSpendReadiness.Ready
        }
        val viewModel = createViewModel()
        advanceUntilIdle()
        val event = async { viewModel.events.first() }

        viewModel.syncMoneroKeyImages()
        advanceUntilIdle()

        assertEquals(TokenBalanceModule.Event.OpenSend(testWallet), event.await())
        assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)
        coVerify(exactly = 1) { adapter.refreshHardwareKeyImages() }
    }

    @Test
    fun prepareMoneroSend_startupRefreshInProgress_opensOnceWhenReady() = runTest(dispatcher) {
        setMoneroWallet()
        val readiness = MutableStateFlow(MoneroSpendReadiness.Syncing)
        val adapter = mockMoneroAdapter(readiness)
        every {
            adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
        } returns adapter
        val viewModel = createViewModel()
        val event = async { viewModel.events.first() }
        advanceUntilIdle()

        viewModel.prepareMoneroSend()
        readiness.value = MoneroSpendReadiness.Ready

        assertEquals(TokenBalanceModule.Event.OpenSend(testWallet), event.await())
        coVerify(exactly = 0) { adapter.refreshHardwareKeyImages() }
    }

    @Test
    fun syncMoneroKeyImages_reconciliationStarted_stopsProgressAndEmitsOneOpenSendWhenReady() = runTest(dispatcher) {
        setMoneroWallet()
        val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
        val adapter = mockMoneroAdapter(readiness)
        every {
            adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
        } returns adapter
        coEvery { adapter.refreshHardwareKeyImages() } answers {
            readiness.value = MoneroSpendReadiness.ReconcilingSpentStatus
        }
        val viewModel = createViewModel()
        val events = mutableListOf<TokenBalanceModule.Event>()
        val eventsJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.syncMoneroKeyImages()
        advanceUntilIdle()

        assertEquals(emptyList<TokenBalanceModule.Event>(), events)
        assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)
        readiness.value = MoneroSpendReadiness.Ready
        advanceUntilIdle()

        assertEquals(listOf(TokenBalanceModule.Event.OpenSend(testWallet)), events)
        assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)

        readiness.value = MoneroSpendReadiness.Syncing
        advanceUntilIdle()
        readiness.value = MoneroSpendReadiness.Ready
        advanceUntilIdle()
        assertEquals(listOf(TokenBalanceModule.Event.OpenSend(testWallet)), events)
        eventsJob.cancel()
    }

    @Test
    fun syncMoneroKeyImages_reconciliationRequiresRetry_stopsProgressWithoutOpeningSend() = runTest(dispatcher) {
        setMoneroWallet()
        val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
        val adapter = mockMoneroAdapter(readiness)
        every {
            adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
        } returns adapter
        coEvery { adapter.refreshHardwareKeyImages() } answers {
            readiness.value = MoneroSpendReadiness.ReconcilingSpentStatus
        }
        val viewModel = createViewModel()
        val events = mutableListOf<TokenBalanceModule.Event>()
        val eventsJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.syncMoneroKeyImages()
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)

        readiness.value = MoneroSpendReadiness.NeedsKeyImageSync
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)
        assertEquals(true, viewModel.uiState.moneroKeyImageSyncRequired)
        assertEquals(emptyList<TokenBalanceModule.Event>(), events)
        eventsJob.cancel()
    }

    @Test
    fun syncMoneroKeyImages_reconciliationFailsAfterReturn_stopsWithErrorAndCanRetry() = runTest(dispatcher) {
        setMoneroWallet()
        val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
        val adapter = mockMoneroAdapter(readiness)
        every {
            adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
        } returns adapter
        var attempts = 0
        coEvery { adapter.refreshHardwareKeyImages() } answers {
            attempts += 1
            readiness.value = MoneroSpendReadiness.ReconcilingSpentStatus
        }
        val viewModel = createViewModel()
        val events = mutableListOf<TokenBalanceModule.Event>()
        val eventsJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.syncMoneroKeyImages()
        advanceUntilIdle()
        readiness.value = MoneroSpendReadiness.ReconciliationFailed
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)
        assertEquals(R.string.trezor_connect_failed, viewModel.uiState.moneroKeyImageSyncError)
        assertEquals(true, viewModel.uiState.moneroKeyImageSyncRequired)
        assertEquals(emptyList<TokenBalanceModule.Event>(), events)

        viewModel.syncMoneroKeyImages()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)
        readiness.value = MoneroSpendReadiness.Ready
        advanceUntilIdle()

        assertEquals(listOf(TokenBalanceModule.Event.OpenSend(testWallet)), events)
        coVerify(exactly = 2) { adapter.refreshHardwareKeyImages() }
        eventsJob.cancel()
    }

    @Test
    fun syncMoneroKeyImages_deviceNotInitialized_exposesSpecificError() = runTest(dispatcher) {
        setMoneroWallet()
        val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
        val adapter = mockMoneroAdapter(readiness)
        every {
            adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
        } returns adapter
        coEvery { adapter.refreshHardwareKeyImages() } throws HardwareWalletOperationException(
            HardwareWalletErrorCode.DeviceNotInitialized,
            null,
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.moneroKeyImageSyncRequired)
        viewModel.syncMoneroKeyImages()
        advanceUntilIdle()

        coVerify(exactly = 1) { adapter.refreshHardwareKeyImages() }
        assertEquals(
            R.string.trezor_not_initialized_description,
            viewModel.uiState.moneroKeyImageSyncError,
        )
        assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)
        assertEquals(false, viewModel.uiState.moneroFullWalletRecoveryAvailable)
    }

    @Test
    fun syncMoneroKeyImages_protocolFailure_offersAndRunsFullWalletRecovery() = runTest(dispatcher) {
        setMoneroWallet()
        val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
        val adapter = mockMoneroAdapter(readiness)
        every {
            adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
        } returns adapter
        coEvery { adapter.refreshHardwareKeyImages() } throws HardwareWalletOperationException(
            HardwareWalletErrorCode.Protocol,
            null,
        )
        coEvery { adapter.fullWalletRecovery() } answers { readiness.value = MoneroSpendReadiness.Ready }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncMoneroKeyImages()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.moneroFullWalletRecoveryAvailable)
        viewModel.fullMoneroWalletRecovery()
        advanceUntilIdle()

        coVerify(exactly = 1) { adapter.refreshHardwareKeyImages() }
        coVerify(exactly = 1) { adapter.fullWalletRecovery() }
    }

    @Test
    fun syncMoneroKeyImages_disconnected_doesNotOfferFullWalletRecovery() = runTest(dispatcher) {
        setMoneroWallet()
        val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
        val adapter = mockMoneroAdapter(readiness)
        every {
            adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
        } returns adapter
        coEvery { adapter.refreshHardwareKeyImages() } throws HardwareWalletOperationException(
            HardwareWalletErrorCode.Disconnected,
            null,
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncMoneroKeyImages()
        advanceUntilIdle()
        viewModel.fullMoneroWalletRecovery()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.moneroFullWalletRecoveryAvailable)
        coVerify(exactly = 0) { adapter.fullWalletRecovery() }
    }

    @Test
    fun syncMoneroKeyImages_connectionFailureThenBackgroundReady_clearsErrorAndContinuesSend() =
        runTest(dispatcher) {
            setMoneroWallet()
            val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
            val adapter = mockMoneroAdapter(readiness)
            every {
                adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
            } returns adapter
            coEvery { adapter.refreshHardwareKeyImages() } throws HardwareWalletOperationException(
                HardwareWalletErrorCode.Disconnected,
                null,
            )
            val viewModel = createViewModel()
            val events = mutableListOf<TokenBalanceModule.Event>()
            val eventsJob = launch { viewModel.events.collect { events.add(it) } }
            advanceUntilIdle()

            viewModel.syncMoneroKeyImages()
            advanceUntilIdle()

            assertEquals(R.string.trezor_connect_failed, viewModel.uiState.moneroKeyImageSyncError)
            assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)
            assertEquals(emptyList<TokenBalanceModule.Event>(), events)

            readiness.value = MoneroSpendReadiness.Ready
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.moneroKeyImageSyncError)
            assertEquals(listOf(TokenBalanceModule.Event.OpenSend(testWallet)), events)
            eventsJob.cancel()
        }

    @Test
    fun syncMoneroKeyImages_reconciliationFailureThenBackgroundReady_clearsErrorAndContinuesSend() =
        runTest(dispatcher) {
            setMoneroWallet()
            val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
            val adapter = mockMoneroAdapter(readiness)
            every {
                adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
            } returns adapter
            coEvery { adapter.refreshHardwareKeyImages() } answers {
                readiness.value = MoneroSpendReadiness.ReconcilingSpentStatus
            }
            val viewModel = createViewModel()
            val events = mutableListOf<TokenBalanceModule.Event>()
            val eventsJob = launch { viewModel.events.collect { events.add(it) } }
            advanceUntilIdle()

            viewModel.syncMoneroKeyImages()
            advanceUntilIdle()
            readiness.value = MoneroSpendReadiness.ReconciliationFailed
            advanceUntilIdle()

            assertEquals(R.string.trezor_connect_failed, viewModel.uiState.moneroKeyImageSyncError)
            assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)
            assertEquals(emptyList<TokenBalanceModule.Event>(), events)

            readiness.value = MoneroSpendReadiness.Ready
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.moneroKeyImageSyncError)
            assertEquals(listOf(TokenBalanceModule.Event.OpenSend(testWallet)), events)
            eventsJob.cancel()
        }

    @Test
    fun cancelMoneroKeyImageSync_operationInProgress_stopsLoading() = runTest(dispatcher) {
        setMoneroWallet()
        val readiness = MutableStateFlow(MoneroSpendReadiness.NeedsKeyImageSync)
        val adapter = mockMoneroAdapter(readiness)
        every {
            adapterManager.getAdapterForWallet<ISendMoneroAdapter>(testWallet)
        } returns adapter
        coEvery { adapter.refreshHardwareKeyImages() } coAnswers { awaitCancellation() }
        val viewModel = createViewModel()
        val events = mutableListOf<TokenBalanceModule.Event>()
        val eventsJob = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.syncMoneroKeyImages()

        assertEquals(true, viewModel.uiState.moneroKeyImageSyncInProgress)
        viewModel.cancelMoneroKeyImageSync()
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.moneroKeyImageSyncInProgress)

        readiness.value = MoneroSpendReadiness.Ready
        advanceUntilIdle()

        assertEquals(emptyList<TokenBalanceModule.Event>(), events)
        coVerify(exactly = 1) { adapter.refreshHardwareKeyImages() }
        eventsJob.cancel()
    }

    // endregion

    // region Helper Methods

    private fun markSwapsByUidPrefix() {
        coEvery { transactionViewItemFactory.convertToViewItemCached(any(), any(), any()) } answers {
            val uid = firstArg<TransactionItem>().record.uid
            createMockTransactionViewItem(uid = uid, isSwap = uid.startsWith("swap-"))
        }
    }

    private fun mockMoneroAdapter(
        readiness: MutableStateFlow<MoneroSpendReadiness>,
    ) = mockk<ISendMoneroAdapter>(relaxed = true) {
        every { hardwareWallet } returns true
        every { spendReadiness } returns readiness
    }

    private fun setMoneroWallet() {
        val token = Token(
            coin = Coin(uid = "monero", name = "Monero", code = "XMR"),
            blockchain = Blockchain(BlockchainType.Monero, "Monero", null),
            type = TokenType.Native,
            decimals = 12,
        )
        val account = Account(
            id = "monero-account",
            name = "Monero",
            type = AccountType.Mnemonic(emptyList(), ""),
            origin = AccountOrigin.Created,
            level = 0,
        )
        testWallet = checkNotNull(
            WalletFactory(mockk(relaxed = true)).create(token, account, null)
        )
    }

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
        premiumSettings = premiumSettings,
        amlStatusManager = amlStatusManager,
        marketFavoritesManager = marketFavoritesManager,
        stackingManager = stackingManager,
        priceManager = priceManager,
        localStorage = localStorage,
        numberFormatter = numberFormatter,
        contactsRepository = contactsRepository,
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

    private fun createTestWallet(
        account: Account = createAccount(),
        coin: Coin = Coin(uid = "test-coin", name = "Test Coin", code = "TEST"),
        blockchainType: BlockchainType = BlockchainType.Bitcoin,
        blockchainName: String = "Bitcoin",
        tokenType: TokenType = TokenType.Native,
    ): Wallet {
        val testToken = Token(
            coin = coin,
            blockchain = Blockchain(
                type = blockchainType,
                name = blockchainName,
                eip3091url = null
            ),
            type = tokenType,
            decimals = 8
        )
        return mockk<Wallet>(relaxed = true) {
            every { this@mockk.token } returns testToken
            every { this@mockk.coin } returns coin
            every { this@mockk.account } returns account
            every { tokenQueryId } returns testToken.tokenQuery.id
        }
    }

    private fun createAccount(
        isWatchAccount: Boolean = false,
        supportsBackup: Boolean = true,
        hasAnyBackup: Boolean = true,
    ) = mockk<Account>(relaxed = true) {
        every { this@mockk.isWatchAccount } returns isWatchAccount
        every { this@mockk.supportsBackup } returns supportsBackup
        every { this@mockk.hasAnyBackup } returns hasAnyBackup
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

    private fun createMockTransactionViewItem(
        uid: String,
        swapTransactionId: String? = null,
        isSwap: Boolean = swapTransactionId != null,
    ) = mockk<TransactionViewItem>(relaxed = true) {
        every { this@mockk.uid } returns uid
        every { formattedDate } returns "DATE"
        every { this@mockk.isSwap } returns isSwap
        every { changeNowTransactionId } returns swapTransactionId
    }

    private fun createBalanceViewItem(
        secondaryValue: DeemedValue<String> = DeemedValue("", dimmed = false, visible = true),
        swapVisible: Boolean = false,
    ) = BalanceViewItem(
        wallet = testWallet,
        primaryValue = DeemedValue("1.5 TEST", dimmed = false, visible = true),
        exchangeValue = DeemedValue("", dimmed = false, visible = false),
        secondaryValue = secondaryValue,
        lockedValues = emptyList(),
        sendEnabled = false,
        syncingProgress = SyncingProgress(null, null),
        syncingTextValue = null,
        syncedUntilTextValue = null,
        failedIconVisible = false,
        coinIconVisible = true,
        badge = null,
        swapVisible = swapVisible,
        swapEnabled = false,
        errorMessage = null,
        isWatchAccount = false,
        isSendDisabled = false,
        isShowShieldFunds = false,
        warning = null
    )

    private fun createPirateCashWallet(): Wallet {
        val pirateCoin = Coin(uid = "pirate-cash", name = "PirateCash", code = "PIRATE")
        val pirateToken = Token(
            coin = pirateCoin,
            blockchain = Blockchain(
                type = BlockchainType.BinanceSmartChain,
                name = "BNB Smart Chain",
                eip3091url = null
            ),
            type = TokenType.Eip20(cash.p.terminal.wallet.BuildConfig.PIRATE_CONTRACT),
            decimals = 18
        )
        val account = mockk<Account>(relaxed = true)
        val walletFactory = WalletFactory(mockk(relaxed = true))
        return checkNotNull(walletFactory.create(pirateToken, account, null))
    }

    private fun createBep20Wallet(): Wallet {
        val coin = Coin(uid = "test-bep20", name = "TestBep20", code = "TBEP")
        val token = Token(
            coin = coin,
            blockchain = Blockchain(
                type = BlockchainType.BinanceSmartChain,
                name = "BNB Smart Chain",
                eip3091url = null
            ),
            type = TokenType.Eip20("0x1234567890abcdef1234567890abcdef12345678"),
            decimals = 18
        )
        val account = mockk<Account>(relaxed = true)
        val walletFactory = WalletFactory(mockk(relaxed = true))
        return checkNotNull(walletFactory.create(token, account, null))
    }

    private fun createTrc20Wallet(): Wallet {
        val usdtCoin = Coin(uid = "tether", name = "Tether", code = "USDT")
        val usdtToken = Token(
            coin = usdtCoin,
            blockchain = Blockchain(
                type = BlockchainType.Tron,
                name = "TRON",
                eip3091url = null
            ),
            type = TokenType.Eip20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"),
            decimals = 6
        )
        val account = mockk<Account>(relaxed = true)
        val walletFactory = WalletFactory(mockk(relaxed = true))
        return checkNotNull(walletFactory.create(usdtToken, account, null))
    }

    private fun createBalanceItem(
        balance: BigDecimal = BigDecimal("1.5"),
        wallet: Wallet = testWallet,
        state: AdapterState = AdapterState.Synced,
        balanceData: BalanceData = BalanceData(available = balance)
    ) = BalanceItem(
        wallet = wallet,
        balanceData = balanceData,
        state = state,
        sendAllowed = true,
        coinPrice = null
    )

    // endregion
}
