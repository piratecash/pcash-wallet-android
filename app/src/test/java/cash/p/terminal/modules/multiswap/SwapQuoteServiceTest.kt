package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.usecase.FetchSwapQuotesUseCase
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import java.math.BigDecimal
import cash.p.terminal.modules.multiswap.providers.StonFiProvider
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRegistry
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRepository
import cash.p.terminal.wallet.Token
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class SwapQuoteServiceTest {

    private val stonFiProvider = mockk<StonFiProvider>(relaxed = true)
    private val mainDispatcher = UnconfinedTestDispatcher()

    private val tokenIn = mockk<Token>()
    private val tokenOut = mockk<Token>()

    private fun mockProvider(
        providerId: String,
        quoteAmountOut: BigDecimal = BigDecimal.ONE,
        supports: Boolean = true,
    ): IMultiSwapProvider {
        val expectedTokenIn = tokenIn
        val expectedTokenOut = tokenOut

        return mockk(relaxed = true) {
            every { id } returns providerId
            coEvery { supports(expectedTokenIn, expectedTokenOut) } returns supports
            coEvery { fetchQuote(expectedTokenIn, expectedTokenOut, BigDecimal.ONE, any()) } returns mockk(relaxed = true) {
                every { amountOut } returns quoteAmountOut
                every { tokenIn } returns expectedTokenIn
                every { tokenOut } returns expectedTokenOut
                every { amountIn } returns BigDecimal.ONE
            }
        }
    }

    @Before
    fun setUp() {
        // Set Main dispatcher to absorb any leaked exceptions from other test classes
        Dispatchers.setMain(mainDispatcher)
        startKoin {
            modules(module { single { stonFiProvider } })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun setTokens_allProvidersSlow_noSupportedSwapProviderError() = runTest {
        val slowProvider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "slow"
            coEvery { supports(tokenIn, tokenOut) } coAnswers {
                delay(6000)
                true
            }
        }

        val service = createService(listOf(slowProvider), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmount(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertTrue(state.error is NoSupportedSwapProvider)
    }

    @Test
    fun setTokens_fastProvider_noError() = runTest {
        val fastProvider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "fast"
            coEvery { supports(tokenIn, tokenOut) } returns true
        }

        val service = createService(listOf(fastProvider), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertNull(state.error)
    }

    @Test
    fun setTokens_slowProviderSkipped_fastProviderKept() = runTest {
        val fastProvider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "fast"
            coEvery { supports(tokenIn, tokenOut) } returns true
        }
        val slowProvider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "slow"
            coEvery { supports(tokenIn, tokenOut) } coAnswers {
                delay(6000)
                true
            }
        }

        val service = createService(listOf(fastProvider, slowProvider), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertNull(state.error)
    }

    @Test
    fun setTokens_providerThrows_excluded() = runTest {
        val failingProvider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "failing"
            coEvery { supports(tokenIn, tokenOut) } throws RuntimeException("network error")
        }

        val service = createService(listOf(failingProvider), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmount(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertTrue(state.error is NoSupportedSwapProvider)
    }

    @Test
    fun setTokens_unsupportedProvider_noSupportedError() = runTest {
        val unsupported = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "unsupported"
            coEvery { supports(tokenIn, tokenOut) } returns false
        }

        val service = createService(listOf(unsupported), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmount(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertTrue(state.error is NoSupportedSwapProvider)
    }

    @Test
    fun start_callsStartOnAllProviders() = runTest {
        val provider1 = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "provider1"
        }
        val provider2 = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "provider2"
        }

        val service = createService(listOf(provider1, provider2), testScheduler)
        service.start()
        advanceUntilIdle()

        coVerify(exactly = 1) { provider1.start() }
        coVerify(exactly = 1) { provider2.start() }
    }

    @Test
    fun start_providerThrows_continuesWithOthers() = runTest {
        val failingProvider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "failing"
            coEvery { start() } throws RuntimeException("init failed")
        }
        val healthyProvider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "healthy"
        }

        val service = createService(listOf(failingProvider, healthyProvider), testScheduler)
        service.start()
        advanceUntilIdle()

        coVerify(exactly = 1) { failingProvider.start() }
        coVerify(exactly = 1) { healthyProvider.start() }
    }

    @Test
    fun setAmount_quotesAvailable_autoSelectsHighestAmountOut() = runTest {
        val lowerQuoteProvider = mockProvider(providerId = "lower", quoteAmountOut = BigDecimal("5"))
        val higherQuoteProvider = mockProvider(providerId = "higher", quoteAmountOut = BigDecimal("10"))

        val service = createService(listOf(lowerQuoteProvider, higherQuoteProvider), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmount(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertEquals(listOf("higher", "lower"), state.quotes.map { it.provider.id })
        assertEquals("higher", state.quote?.provider?.id)
    }

    @Test
    fun setAmount_disabledProviderHasQuote_visibleInQuotesButNotAutoSelected() = runTest {
        val enabledProvider = mockProvider(providerId = "enabled", quoteAmountOut = BigDecimal("5"))
        val disabledProvider = mockProvider(providerId = "disabled", quoteAmountOut = BigDecimal("10"))

        val service = createService(
            providers = listOf(enabledProvider, disabledProvider),
            scheduler = testScheduler,
            disabledIds = setOf("disabled"),
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmount(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        val quoteIds = state.quotes.map { it.provider.id }
        assertTrue(
            "Disabled provider must remain visible in quotes when it has swap data",
            "disabled" in quoteIds,
        )
        assertTrue("Enabled provider must be in quotes", "enabled" in quoteIds)
        assertEquals(
            "Auto-selected quote must be from an enabled provider",
            "enabled",
            state.quote?.provider?.id,
        )
    }

    @Test
    fun disabledIdsChange_currentlySelectedDisabled_immediatelySwitchesToBestEnabled() = runTest {
        val higher = mockProvider(providerId = "higher", quoteAmountOut = BigDecimal("10"))
        val lower = mockProvider(providerId = "lower", quoteAmountOut = BigDecimal("5"))

        val disabledIdsFlow = MutableStateFlow(emptySet<String>())
        val service = createService(
            providers = listOf(higher, lower),
            scheduler = testScheduler,
            disabledIdsFlow = disabledIdsFlow,
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmount(BigDecimal.ONE)
        advanceUntilIdle()

        assertEquals("higher", service.stateFlow.value.quote?.provider?.id)

        disabledIdsFlow.value = setOf("higher")
        advanceUntilIdle()

        assertEquals(
            "Selecting must immediately move to next-best enabled provider",
            "lower",
            service.stateFlow.value.quote?.provider?.id,
        )
    }

    @Test
    fun disabledIdsChange_reEnablePreviouslyDisabled_currentSelectionStays() = runTest {
        val higher = mockProvider(providerId = "higher", quoteAmountOut = BigDecimal("10"))
        val lower = mockProvider(providerId = "lower", quoteAmountOut = BigDecimal("5"))

        val disabledIdsFlow = MutableStateFlow(emptySet<String>())
        val service = createService(
            providers = listOf(higher, lower),
            scheduler = testScheduler,
            disabledIdsFlow = disabledIdsFlow,
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmount(BigDecimal.ONE)
        advanceUntilIdle()

        disabledIdsFlow.value = setOf("higher")
        advanceUntilIdle()
        assertEquals("lower", service.stateFlow.value.quote?.provider?.id)

        disabledIdsFlow.value = emptySet()
        advanceUntilIdle()

        assertEquals(
            "Re-enabling a provider must NOT change the current selection",
            "lower",
            service.stateFlow.value.quote?.provider?.id,
        )
    }

    private fun createService(
        providers: List<IMultiSwapProvider>,
        scheduler: TestCoroutineScheduler,
        disabledIds: Set<String> = emptySet(),
    ): SwapQuoteService = createService(
        providers = providers,
        scheduler = scheduler,
        disabledIdsFlow = MutableStateFlow(disabledIds),
    )

    private fun createService(
        providers: List<IMultiSwapProvider>,
        scheduler: TestCoroutineScheduler,
        disabledIdsFlow: MutableStateFlow<Set<String>>,
    ): SwapQuoteService {
        val dispatcher = StandardTestDispatcher(scheduler)
        val routeResolver = mockk<MultiSwapRouteResolver>(relaxed = true) {
            coEvery { findRoute(any(), any(), any(), any(), any()) } returns null
        }
        val repository = mockk<SwapProvidersRepository>(relaxed = true) {
            every { this@mockk.disabledIds } returns disabledIdsFlow
            every { isDisabled(any()) } answers {
                firstArg<String>() in disabledIdsFlow.value
            }
        }
        val registry = mockk<SwapProvidersRegistry>(relaxed = true) {
            every { this@mockk.providers } returns providers
            every { findById(any()) } answers {
                val id = firstArg<String>()
                providers.firstOrNull { it.id == id }
            }
        }
        return SwapQuoteService(
            routeResolver,
            FetchSwapQuotesUseCase(),
            repository,
            registry,
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )
    }
}
