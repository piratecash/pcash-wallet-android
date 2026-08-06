package cash.p.terminal.modules.multiswap

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.core.ServiceStateFlow
import cash.p.terminal.modules.paycore.PayCoreAssets
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

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
    private val timerService = mockk<TimerService>(relaxed = true) {
        every { stateFlow } returns serviceStateFlow(TimerService.State(null, false))
    }
    private val networkAvailabilityService = mockk<NetworkAvailabilityService>(relaxed = true) {
        every { stateFlow } returns serviceStateFlow(
            NetworkAvailabilityService.State(networkAvailable = true, error = null)
        )
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

    private companion object {
        fun <T> serviceStateFlow(value: T): ServiceStateFlow<T> {
            val flow = MutableSharedFlow<T>(replay = 1).also { it.tryEmit(value) }
            return ServiceStateFlow(flow.asSharedFlow())
        }
    }
}
