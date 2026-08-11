package cash.p.terminal.modules.multiswap

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.core.ServiceStateFlow
import cash.p.terminal.modules.paycore.PayCoreAssets
import cash.p.terminal.modules.multiswap.exchange.ButtonState
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.Currency
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class SwapViewModelFiatInputTest {

    private lateinit var dispatcher: TestDispatcher
    private val currency = Currency("USD", "$", 2, 0)
    private val tokenOut = PayCoreAssets.rubToken
    private val rateFlow = MutableStateFlow<BigDecimal?>(BigDecimal("0.25"))
    private val quoteStateFlow = MutableStateFlow(
        SwapQuoteService.State(
            amountIn = null,
            tokenIn = tokenOut,
            tokenOut = tokenOut,
            quoting = false,
            quotes = emptyList(),
            preferredProvider = null,
            quote = null,
            error = null,
            multiSwapRoute = null,
            direction = SwapAmountDirection.In,
            requestedAmountOut = null,
            amountInMax = null,
        )
    )
    private val quoteService = mockk<SwapQuoteService>(relaxed = true) {
        every { stateFlow } returns quoteStateFlow
        every { swapSettings } returns emptyMap()
    }
    private val balanceService = mockk<TokenBalanceService>(relaxed = true) {
        every { stateFlow } returns serviceStateFlow(
            TokenBalanceService.State(
                balance = null,
                displayBalance = null,
                error = null,
                fee = null,
                feeToken = null,
                feeCoinBalance = null,
                insufficientFeeBalance = false,
            )
        )
    }
    private val timerStateFlow = MutableSharedFlow<TimerService.State>(replay = 1).also {
        it.tryEmit(TimerService.State(remaining = null, timeout = false))
    }
    private val timerService = mockk<TimerService>(relaxed = true) {
        every { stateFlow } returns ServiceStateFlow(timerStateFlow.asSharedFlow())
    }
    private val networkStateFlow = MutableSharedFlow<NetworkAvailabilityService.State>(replay = 1).also {
        it.tryEmit(NetworkAvailabilityService.State(networkAvailable = true, error = null))
    }
    private val networkAvailabilityService = mockk<NetworkAvailabilityService>(relaxed = true) {
        every { stateFlow } returns ServiceStateFlow(networkStateFlow.asSharedFlow())
    }
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val assetFiatRateService = mockk<AssetFiatRateService> {
        every { rateFlow("swap", tokenOut, currency) } returns rateFlow
    }
    private val currencyManager = mockk<CurrencyManager> {
        every { baseCurrency } returns currency
    }
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true) {
        every { anyWalletVisibilityChangedFlow } returns MutableSharedFlow()
    }
    private val viewModelStore = ViewModelStore()

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        startKoin {
            modules(
                module {
                    single<IBalanceHiddenManager> { balanceHiddenManager }
                    single<WalletUseCase> { mockk(relaxed = true) }
                }
            )
        }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
    }

    @Test
    fun onEnterFiatAmountOut_rateChanges_updatesExactOutAmount() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(quoteService, answers = false, recordedCalls = true)

        viewModel.onEnterFiatAmountOut(BigDecimal.ONE)
        advanceUntilIdle()
        rateFlow.value = BigDecimal("0.20")
        advanceUntilIdle()

        verify(exactly = 1) { quoteService.setAmountOut(BigDecimal("4")) }
        verify(exactly = 1) { quoteService.setAmountOut(BigDecimal("5")) }
    }

    @Test
    fun onEnterFiatAmount_rateChanges_updatesExactInAmount() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(quoteService, answers = false, recordedCalls = true)

        viewModel.onEnterFiatAmount(BigDecimal.ONE)
        advanceUntilIdle()
        rateFlow.value = BigDecimal("0.20")
        advanceUntilIdle()

        verify(exactly = 1) { quoteService.setAmountIn(BigDecimal("4")) }
        verify(exactly = 1) { quoteService.setAmountIn(BigDecimal("5")) }
    }

    @Test
    fun onEnterFiatAmountOut_afterFiatAmountIn_rateChanges_updatesOnlyExactOutAmount() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEnterFiatAmount(BigDecimal.ONE)
            advanceUntilIdle()
            viewModel.onEnterFiatAmountOut(BigDecimal("2"))
            advanceUntilIdle()
            clearMocks(quoteService, answers = false, recordedCalls = true)

            rateFlow.value = BigDecimal("0.50")
            advanceUntilIdle()

            verify(exactly = 0) { quoteService.setAmountIn(any()) }
            verify(exactly = 1) { quoteService.setAmountOut(BigDecimal("4")) }
        }

    @Test
    fun onEnterAmountOut_afterFiatInput_rateChanges_doesNotUpdateExactOutAmount() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEnterFiatAmountOut(BigDecimal.ONE)
            advanceUntilIdle()
            viewModel.onEnterAmountOut(BigDecimal("7"))
            advanceUntilIdle()
            clearMocks(quoteService, answers = false, recordedCalls = true)

            rateFlow.value = BigDecimal("0.50")
            advanceUntilIdle()

            verify(exactly = 0) { quoteService.setAmountOut(any()) }
        }

    @Test
    fun onEnterAmount_afterFiatInput_rateChanges_doesNotUpdateExactInAmount() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEnterFiatAmount(BigDecimal.ONE)
            advanceUntilIdle()
            viewModel.onEnterAmount(BigDecimal("7"))
            advanceUntilIdle()
            clearMocks(quoteService, answers = false, recordedCalls = true)

            rateFlow.value = BigDecimal("0.50")
            advanceUntilIdle()

            verify(exactly = 0) { quoteService.setAmountIn(any()) }
        }

    @Test
    fun refreshExpiredMultiSwapRoute_expiredRoute_blocksAndShowsLoading() = runTest(dispatcher) {
        val viewModel = createViewModel()
        setExpiredMultiSwapRoute()
        clearMocks(quoteService, answers = false, recordedCalls = true)

        assertFalse(viewModel.canContinueMultiSwapRoute())
        viewModel.refreshExpiredMultiSwapRoute()

        assertEquals(ButtonState.Quoting, viewModel.multiSwapRouteInfoUiState(viewModel.uiState)?.buttonState)
        verify(exactly = 1) { quoteService.reQuote() }
    }

    @Test
    fun refreshMultiSwapRoute_failedRefresh_keepsRouteAndOffersRetry() = runTest(dispatcher) {
        val viewModel = createViewModel()
        failMultiSwapRouteRefresh(viewModel)

        val routeInfoState = viewModel.multiSwapRouteInfoUiState(viewModel.uiState)
        assertNotNull(routeInfoState)
        assertEquals(ButtonState.Refresh, routeInfoState?.buttonState)
        clearMocks(quoteService, answers = false, recordedCalls = true)

        viewModel.refreshMultiSwapRoute()

        assertEquals(ButtonState.Quoting, viewModel.multiSwapRouteInfoUiState(viewModel.uiState)?.buttonState)
        verify(exactly = 1) { quoteService.reQuote() }
    }

    @Test
    fun networkRecovery_failedRouteRefresh_restoresFreshRoute() = runTest(dispatcher) {
        val viewModel = createViewModel()
        failMultiSwapRouteRefresh(viewModel)
        clearMocks(quoteService, answers = false, recordedCalls = true)

        networkStateFlow.emit(NetworkAvailabilityService.State(networkAvailable = true, error = null))
        advanceUntilIdle()
        quoteStateFlow.value = quoteStateFlow.value.copy(quoting = true)
        advanceUntilIdle()

        assertEquals(ButtonState.Quoting, viewModel.multiSwapRouteInfoUiState(viewModel.uiState)?.buttonState)
        verify(exactly = 1) { quoteService.reQuote() }

        val freshRoute = multiSwapRouteFixture()
        quoteStateFlow.value = quoteStateFlow.value.copy(
            quoting = false,
            quote = freshRoute.selectedLeg1Quote,
            error = null,
            multiSwapRoute = freshRoute,
        )
        timerStateFlow.emit(TimerService.State(remaining = 20, timeout = false))
        advanceUntilIdle()

        assertEquals(ButtonState.Enabled, viewModel.multiSwapRouteInfoUiState(viewModel.uiState)?.buttonState)
    }

    @Test
    fun refreshMultiSwapRoute_successfulRouteWhileOffline_enablesContinue() = runTest(dispatcher) {
        val viewModel = createViewModel()
        setExpiredMultiSwapRoute()
        viewModel.refreshExpiredMultiSwapRoute()
        quoteStateFlow.value = quoteStateFlow.value.copy(quoting = true)
        networkStateFlow.emit(
            NetworkAvailabilityService.State(networkAvailable = false, error = UnknownHostException()),
        )
        advanceUntilIdle()

        val freshRoute = multiSwapRouteFixture()
        quoteStateFlow.value = quoteStateFlow.value.copy(
            quoting = false,
            quote = freshRoute.selectedLeg1Quote,
            error = null,
            multiSwapRoute = freshRoute,
        )
        timerStateFlow.emit(TimerService.State(remaining = 20, timeout = false))
        advanceUntilIdle()

        assertTrue(viewModel.canContinueMultiSwapRoute())
        assertEquals(ButtonState.Enabled, viewModel.multiSwapRouteInfoUiState(viewModel.uiState)?.buttonState)
    }

    @Test
    fun multiSwapRoute_networkUnavailableBeforeExpiry_allowsContinueAndProviderSelection() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val route = setMultiSwapRoute(timeout = false)
        networkStateFlow.emit(
            NetworkAvailabilityService.State(networkAvailable = false, error = UnknownHostException()),
        )
        advanceUntilIdle()
        clearMocks(quoteService, answers = false, recordedCalls = true)

        assertTrue(viewModel.canContinueMultiSwapRoute())
        assertEquals(ButtonState.Enabled, viewModel.multiSwapRouteInfoUiState(viewModel.uiState)?.buttonState)

        viewModel.onSelectLeg2Quote(route.selectedLeg2Quote)

        verify(exactly = 1) { quoteService.selectQuote(route.selectedLeg2Quote, SwapQuoteSelectionTarget.RouteLeg2) }
    }

    @Test
    fun refreshMultiSwapRoute_directQuoteAvailable_closesRouteInfo() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val route = setExpiredMultiSwapRoute()
        viewModel.refreshExpiredMultiSwapRoute()
        quoteStateFlow.value = quoteStateFlow.value.copy(quoting = true)
        networkStateFlow.emit(
            NetworkAvailabilityService.State(networkAvailable = false, error = UnknownHostException()),
        )
        advanceUntilIdle()

        quoteStateFlow.value = quoteStateFlow.value.copy(
            quoting = false,
            quote = route.selectedLeg1Quote,
            error = null,
            multiSwapRoute = null,
        )
        timerStateFlow.emit(TimerService.State(remaining = 20, timeout = false))
        advanceUntilIdle()

        assertNull(viewModel.multiSwapRouteInfoUiState(viewModel.uiState))
    }

    private fun createViewModel(): SwapViewModel {
        return SwapViewModel(
            quoteService = quoteService,
            balanceService = balanceService,
            priceImpactService = PriceImpactService(),
            currencyManager = currencyManager,
            fiatServiceIn = FiatService(assetFiatRateService, dispatcher),
            fiatServiceOut = FiatService(assetFiatRateService, dispatcher),
            timerService = timerService,
            networkAvailabilityService = networkAvailabilityService,
            marketKit = marketKit,
            tokenIn = null,
            tokenOut = null,
        ).also { viewModelStore.put("swap", it) }
    }

    private suspend fun TestScope.setExpiredMultiSwapRoute(): MultiSwapRoute = setMultiSwapRoute(timeout = true)

    private suspend fun TestScope.setMultiSwapRoute(timeout: Boolean): MultiSwapRoute {
        advanceUntilIdle()
        val route = multiSwapRouteFixture()
        quoteStateFlow.value = quoteStateFlow.value.copy(
            quote = route.selectedLeg1Quote,
            error = null,
            multiSwapRoute = route,
        )
        timerStateFlow.emit(TimerService.State(remaining = if (timeout) 0 else 20, timeout = timeout))
        advanceUntilIdle()
        return route
    }

    private suspend fun TestScope.failMultiSwapRouteRefresh(viewModel: SwapViewModel) {
        setExpiredMultiSwapRoute()
        viewModel.refreshExpiredMultiSwapRoute()
        quoteStateFlow.value = quoteStateFlow.value.copy(quoting = true)
        networkStateFlow.emit(
            NetworkAvailabilityService.State(networkAvailable = false, error = UnknownHostException()),
        )
        advanceUntilIdle()
        quoteStateFlow.value = quoteStateFlow.value.copy(
            quoting = false,
            quote = null,
            error = IllegalStateException("offline"),
            multiSwapRoute = null,
        )
        timerStateFlow.emit(TimerService.State(remaining = null, timeout = false))
        advanceUntilIdle()
    }

    private companion object {
        fun <T> serviceStateFlow(value: T): ServiceStateFlow<T> {
            val flow = MutableSharedFlow<T>(replay = 1).also { it.tryEmit(value) }
            return ServiceStateFlow(flow.asSharedFlow())
        }
    }
}
