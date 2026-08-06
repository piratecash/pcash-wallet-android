package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.usecase.FetchSwapQuotesUseCase
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRegistry
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRepository
import cash.p.terminal.wallet.Token
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SwapQuoteServiceTest : SwapQuoteServiceTestFixture() {
    private val leg1ProviderA = mockk<IMultiSwapProvider>(relaxed = true) {
        every { id } returns "leg1a"
    }
    private val leg1ProviderB = mockk<IMultiSwapProvider>(relaxed = true) {
        every { id } returns "leg1b"
    }
    private val leg1QuoteA = routeQuote(leg1ProviderA)
    private val leg1QuoteB = routeQuote(leg1ProviderB)

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
        service.setAmountIn(BigDecimal.ONE)
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
        service.setAmountIn(BigDecimal.ONE)
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
        service.setAmountIn(BigDecimal.ONE)
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
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertEquals(listOf("higher", "lower"), state.quotes.map { it.provider.id })
        assertEquals("higher", state.quote?.provider?.id)
    }

    @Test
    fun setAmount_disabledProviderSupportsPair_stillVisibleInQuotesButNotAutoSelected() = runTest {
        val enabledProvider = mockProvider(providerId = "enabled", quoteAmountOut = BigDecimal("5"))
        val disabledProvider = mockProvider(providerId = "disabled", quoteAmountOut = BigDecimal("10"))

        val service = createService(
            providers = listOf(enabledProvider, disabledProvider),
            scheduler = testScheduler,
            disabledIds = setOf("disabled"),
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        val quoteIds = state.quotes.map { it.provider.id }
        // Regression guard: a disabled provider that supports the pair MUST still be fetched and kept in
        // `quotes`. The provider-selection screen (SwapSelectProviderScreen) renders these greyed-out
        // (alpha 0.4) with an inline toggle so the user can see the rate they're missing and re-enable it.
        // Fetching only enabled providers would hide disabled providers from that screen entirely.
        assertTrue(
            "Disabled provider must remain visible in quotes so the selection screen can show it",
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
        service.setAmountIn(BigDecimal.ONE)
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
    fun setAmount_singleProviderThrowsAmountOutOfRange_emitsSwapAmountOutOfRange() = runTest {
        val provider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "paycore"
            coEvery { supports(tokenIn, tokenOut) } returns true
            coEvery { fetchQuote(tokenIn, tokenOut, any(), any()) } throws SwapAmountOutOfRange()
        }

        val service = createService(listOf(provider), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertTrue(
            "Expected SwapAmountOutOfRange but got ${state.error}",
            state.error is SwapAmountOutOfRange,
        )
        assertTrue("Quotes must be empty when only provider failed", state.quotes.isEmpty())
    }

    @Test
    fun setAmount_someProvidersSucceedAndOneAmountOutOfRange_errorCleared() = runTest {
        val succeeding = mockProvider(providerId = "ok", quoteAmountOut = BigDecimal("5"))
        val failing = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "paycore"
            coEvery { supports(tokenIn, tokenOut) } returns true
            coEvery { fetchQuote(tokenIn, tokenOut, any(), any()) } throws SwapAmountOutOfRange()
        }

        val service = createService(listOf(succeeding, failing), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertNull("Error must be cleared when at least one provider has a quote", state.error)
        assertEquals(listOf("ok"), state.quotes.map { it.provider.id })
    }

    @Test
    fun setAmount_allProvidersFailAtLeastOneAmountOutOfRange_emitsSwapAmountOutOfRange() = runTest {
        val networkFailing = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "network"
            coEvery { supports(tokenIn, tokenOut) } returns true
            coEvery { fetchQuote(tokenIn, tokenOut, any(), any()) } throws RuntimeException("offline")
        }
        val amountFailing = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "paycore"
            coEvery { supports(tokenIn, tokenOut) } returns true
            coEvery { fetchQuote(tokenIn, tokenOut, any(), any()) } throws SwapAmountOutOfRange()
        }

        val service = createService(listOf(networkFailing, amountFailing), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertTrue(
            "Generic provider failure must NOT override SwapAmountOutOfRange (got ${state.error})",
            state.error is SwapAmountOutOfRange,
        )
    }

    @Test
    fun setAmount_swapDepositTooSmallTakesPrecedenceOverAmountOutOfRange() = runTest {
        val depositTooSmall = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "changenow"
            coEvery { supports(tokenIn, tokenOut) } returns true
            coEvery { fetchQuote(tokenIn, tokenOut, any(), any()) } throws SwapDepositTooSmall(BigDecimal("0.001"))
        }
        val amountOutOfRange = mockk<IMultiSwapProvider>(relaxed = true) {
            every { id } returns "paycore"
            coEvery { supports(tokenIn, tokenOut) } returns true
            coEvery { fetchQuote(tokenIn, tokenOut, any(), any()) } throws SwapAmountOutOfRange()
        }

        val service = createService(listOf(amountOutOfRange, depositTooSmall), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        assertTrue(
            "SwapDepositTooSmall must take precedence (got ${state.error})",
            state.error is SwapDepositTooSmall,
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
        service.setAmountIn(BigDecimal.ONE)
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

    @Test
    fun setAmount_amountChanged_marksQuotingImmediately() = runTest {
        val provider = mockProvider(providerId = "provider")
        val service = createService(listOf(provider), testScheduler)

        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        advanceUntilIdle()

        // Both tokens chosen but no amount yet -> not quoting.
        assertFalse(service.stateFlow.value.quoting)

        service.setAmountIn(BigDecimal.ONE)

        // Changing the amount flips quoting=true immediately, before the debounced fetch
        // runs, so the swap button shows the spinner and cannot act on a stale quote.
        assertTrue(service.stateFlow.value.quoting)
    }

    @Test
    fun setAmount_noDirectQuotesButRouteFound_exposesLeg1QuotesForSelection() = runTest {
        val service = createService(
            providers = listOf(unsupportedDirectProvider()),
            scheduler = testScheduler,
            routeResolver = routeResolverWithLeg1Quotes(),
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        val state = service.stateFlow.value
        // The provider picker closes on an empty `quotes` list, so a 2-step route must publish
        // its leg 1 quotes there - otherwise the user cannot switch the first swap provider.
        assertEquals(listOf("leg1a", "leg1b"), state.quotes.map { it.provider.id })
        assertEquals("leg1a", state.quote?.provider?.id)
    }

    @Test
    fun selectQuote_routeActive_rebuildsRouteWithSelectedLeg1Provider() = runTest {
        val routeResolver = routeResolverWithLeg1Quotes()
        val service = createService(
            providers = listOf(unsupportedDirectProvider()),
            scheduler = testScheduler,
            routeResolver = routeResolver,
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        service.selectQuote(leg1QuoteB)
        advanceUntilIdle()

        // Leg 2 is quoted for the leg 1 output, so picking another leg 1 provider must re-resolve the route
        coVerify { routeResolver.findRoute(any(), any(), any(), any(), any(), leg1ProviderB) }
    }

    @Test
    fun disabledIdsChange_routeActive_keepsRouteInsteadOfPublishingLeg1QuoteAsDirect() = runTest {
        val disabledIdsFlow = MutableStateFlow(emptySet<String>())
        val service = createService(
            providers = listOf(unsupportedDirectProvider()),
            scheduler = testScheduler,
            disabledIdsFlow = disabledIdsFlow,
            routeResolver = routeResolverWithLeg1Quotes(),
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        disabledIdsFlow.value = setOf("leg1a")
        advanceUntilIdle()

        // `quotes` holds leg 1 quotes while a route is active, so treating them as direct quotes
        // would drop the route and show the intermediate amount as the final one.
        assertNotNull(service.stateFlow.value.multiSwapRoute)
    }

    @Test
    fun selectQuote_directQuoteFromCurrentList_selectsItWithoutRequoting() = runTest {
        val higher = mockProvider(providerId = "higher", quoteAmountOut = BigDecimal("10"))
        val lower = mockProvider(providerId = "lower", quoteAmountOut = BigDecimal("5"))

        val service = createService(listOf(higher, lower), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        val worseQuote = service.stateFlow.value.quotes.first { it.provider.id == "lower" }
        service.selectQuote(worseQuote)

        // A direct swap keeps the plain path: the picked quote is published right away,
        // without a refetch and without blocking the swap button.
        val state = service.stateFlow.value
        assertEquals("lower", state.quote?.provider?.id)
        assertFalse(state.quoting)
    }

    @Test
    fun selectQuote_routeActive_marksQuotingImmediately() = runTest {
        val service = createService(
            providers = listOf(unsupportedDirectProvider()),
            scheduler = testScheduler,
            routeResolver = routeResolverWithLeg1Quotes(),
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        service.selectQuote(leg1QuoteB)

        // The rebuild runs asynchronously, so quoting must be published synchronously - otherwise
        // the user could confirm the swap on the superseded route right after closing the picker.
        assertTrue(service.stateFlow.value.quoting)
    }

    @Test
    fun selectQuote_quoteMissingFromCurrentQuotes_doesNotPublishStaleQuote() = runTest {
        val direct = mockProvider(providerId = "direct", quoteAmountOut = BigDecimal("5"))
        val service = createService(listOf(direct), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        // The picker may hold a snapshot taken while a 2-step route was active. Publishing such a
        // leg 1 quote as the current one would show the intermediate amount as the final output.
        service.selectQuote(leg1QuoteB)
        advanceUntilIdle()

        assertEquals("direct", service.stateFlow.value.quote?.provider?.id)
    }

    private fun unsupportedDirectProvider(): IMultiSwapProvider = mockk(relaxed = true) {
        every { id } returns "direct"
        coEvery { supports(tokenIn, tokenOut) } returns false
    }

    private fun routeResolverWithLeg1Quotes(): MultiSwapRouteResolver {
        val route = mockk<MultiSwapRoute>(relaxed = true) {
            every { leg1Quotes } returns listOf(leg1QuoteA, leg1QuoteB)
            every { selectedLeg1Quote } returns leg1QuoteA
        }
        return mockk(relaxed = true) {
            coEvery { findRoute(any(), any(), any(), any(), any(), any()) } returns route
        }
    }

    private fun routeQuote(quoteProvider: IMultiSwapProvider): SwapProviderQuote =
        mockk(relaxed = true) {
            every { provider } returns quoteProvider
        }

    @Test
    fun switchPairs_exactIn_movesQuotedOutputToInput() = runTest {
        val provider = mockProvider("provider", quoteAmountOut = BigDecimal("5"))
        val service = createService(listOf(provider), testScheduler)
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        service.switchPairs()

        val state = service.stateFlow.value
        assertEquals(tokenOut, state.tokenIn)
        assertEquals(tokenIn, state.tokenOut)
        assertEquals(BigDecimal("5"), state.amountIn)
        assertEquals(SwapAmountDirection.In, state.direction)
        assertNull(state.requestedAmountOut)
        assertNull(state.amountInMax)
    }

    @Test
    fun switchPairs_multiSwap_movesFinalLegOutputToInput() = runTest {
        val provider = mockProvider("provider")
        val leg1 = providerQuote(provider, BigDecimal.ONE, BigDecimal("2"))
        val leg2 = providerQuote(provider, BigDecimal("2"), BigDecimal("9"))
        val route = MultiSwapRoute(
            intermediateCoin = mockk(),
            leg1Quotes = listOf(leg1),
            leg2Quotes = listOf(leg2),
            commissionReserve = BigDecimal.ZERO,
            selectedLeg1Quote = leg1,
            selectedLeg2Quote = leg2,
        )
        val fetch = mockk<FetchSwapQuotesUseCase> {
            coEvery { this@mockk(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        }
        val resolver = mockk<MultiSwapRouteResolver> {
            coEvery { findRoute(any(), any(), any(), any(), any(), any()) } returns route
        }
        val service = createService(
            providers = listOf(provider),
            scheduler = testScheduler,
            fetchSwapQuotesUseCase = fetch,
            routeResolver = resolver,
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountIn(BigDecimal.ONE)
        advanceUntilIdle()

        service.switchPairs()

        assertEquals(BigDecimal("9"), service.stateFlow.value.amountIn)
    }

    @Test
    fun switchPairs_exactOut_movesRequestedOutputToInputAndClearsExactOutState() = runTest {
        val provider = mockProvider("provider")
        val exactOutQuote = providerQuote(
            provider,
            amountIn = BigDecimal("3"),
            amountOut = BigDecimal("7"),
            amountInMax = BigDecimal("4"),
        )
        val fetch = mockk<FetchSwapQuotesUseCase> {
            coEvery { this@mockk(any(), any(), any(), any(), any(), any(), any()) } returns
                listOf(exactOutQuote)
        }
        val service = createService(
            providers = listOf(provider),
            scheduler = testScheduler,
            fetchSwapQuotesUseCase = fetch,
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountOut(BigDecimal("7"))
        advanceUntilIdle()

        assertEquals(BigDecimal("7"), service.stateFlow.value.requestedAmountOut)
        assertEquals(BigDecimal("4"), service.stateFlow.value.amountInMax)

        service.switchPairs()

        val state = service.stateFlow.value
        assertEquals(BigDecimal("7"), state.amountIn)
        assertEquals(SwapAmountDirection.In, state.direction)
        assertNull(state.requestedAmountOut)
        assertNull(state.amountInMax)
    }

    @Test
    fun setAmountOut_disabledProvider_isNotRequested() = runTest {
        val enabled = mockProvider("enabled")
        val disabled = mockProvider("disabled")
        val fetch = mockk<FetchSwapQuotesUseCase>(relaxed = true)
        val service = createService(
            providers = listOf(enabled, disabled),
            scheduler = testScheduler,
            disabledIds = setOf("disabled"),
            fetchSwapQuotesUseCase = fetch,
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountOut(BigDecimal.ONE)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            fetch(
                match { it == listOf(enabled) },
                tokenIn,
                tokenOut,
                BigDecimal.ONE,
                SwapAmountDirection.Out,
                any(),
                any(),
            )
        }
    }

    @Test
    fun disabledIdsChange_exactOutProviderReEnabled_requotesOnceAndKeepsSelection() = runTest {
        val selected = mockProvider("selected")
        val reEnabled = mockProvider("re-enabled")
        val selectedQuote = providerQuote(selected, BigDecimal("2"), BigDecimal.ONE)
        val reEnabledQuote = providerQuote(reEnabled, BigDecimal("3"), BigDecimal.ONE)
        val disabledIdsFlow = MutableStateFlow(setOf("re-enabled"))
        val fetch = mockk<FetchSwapQuotesUseCase> {
            coEvery {
                this@mockk(any(), any(), any(), any(), any(), any(), any())
            } coAnswers {
                firstArg<List<IMultiSwapProvider>>().mapNotNull { provider ->
                    when (provider.id) {
                        "selected" -> selectedQuote
                        "re-enabled" -> reEnabledQuote
                        else -> null
                    }
                }
            }
        }
        val service = createService(
            providers = listOf(selected, reEnabled),
            scheduler = testScheduler,
            disabledIdsFlow = disabledIdsFlow,
            fetchSwapQuotesUseCase = fetch,
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountOut(BigDecimal.ONE)
        advanceUntilIdle()

        disabledIdsFlow.value = emptySet()
        advanceUntilIdle()

        coVerify(exactly = 2) {
            fetch(any(), any(), any(), any(), any(), any(), any())
        }
        assertEquals("selected", service.stateFlow.value.quote?.provider?.id)
    }

    @Test
    fun disabledIdsChange_exactOutProviderDisabled_doesNotRequote() = runTest {
        val provider = mockProvider("selected")
        val quote = providerQuote(provider, BigDecimal("2"), BigDecimal.ONE)
        val disabledIdsFlow = MutableStateFlow(emptySet<String>())
        val fetch = mockk<FetchSwapQuotesUseCase> {
            coEvery {
                this@mockk(any(), any(), any(), any(), any(), any(), any())
            } returns listOf(quote)
        }
        val service = createService(
            providers = listOf(provider),
            scheduler = testScheduler,
            disabledIdsFlow = disabledIdsFlow,
            fetchSwapQuotesUseCase = fetch,
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountOut(BigDecimal.ONE)
        advanceUntilIdle()

        disabledIdsFlow.value = setOf(provider.id)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            fetch(any(), any(), any(), any(), any(), any(), any())
        }
        assertNull(service.stateFlow.value.quote)
        assertTrue(service.stateFlow.value.error is NoExactOutSwapProvider)
    }

    @Test
    fun disabledIdsChange_duringExactOutQuotation_keepsLoadingUntilSuccess() = runTest {
        val selected = mockProvider("selected")
        val other = mockProvider("other")
        val selectedQuote = providerQuote(selected, BigDecimal("2"), BigDecimal.ONE)
        val otherQuote = providerQuote(other, BigDecimal("3"), BigDecimal("2"))
        val disabledIdsFlow = MutableStateFlow(emptySet<String>())
        val fetchStarted = CompletableDeferred<Unit>()
        val fetchResult = CompletableDeferred<List<SwapProviderQuote>>()
        var fetchCount = 0
        val fetch = mockk<FetchSwapQuotesUseCase> {
            coEvery {
                this@mockk(any(), any(), any(), any(), any(), any(), any())
            } coAnswers {
                fetchCount++
                if (fetchCount == 1) {
                    listOf(selectedQuote)
                } else {
                    fetchStarted.complete(Unit)
                    fetchResult.await()
                }
            }
        }
        val service = createService(
            providers = listOf(selected, other),
            scheduler = testScheduler,
            disabledIdsFlow = disabledIdsFlow,
            fetchSwapQuotesUseCase = fetch,
        )
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountOut(BigDecimal.ONE)
        advanceUntilIdle()

        service.setAmountOut(BigDecimal("2"))
        advanceTimeBy(601)
        runCurrent()
        assertTrue(fetchStarted.isCompleted)

        disabledIdsFlow.value = setOf(selected.id)
        runCurrent()

        assertTrue(service.stateFlow.value.quoting)
        assertNull(service.stateFlow.value.quote)
        assertNull(service.stateFlow.value.error)

        fetchResult.complete(listOf(otherQuote))
        advanceUntilIdle()

        assertFalse(service.stateFlow.value.quoting)
        assertEquals(other.id, service.stateFlow.value.quote?.provider?.id)
        assertNull(service.stateFlow.value.error)
    }

    @Test
    fun disabledIdsChange_exactOutProviderReEnabledDuringDebounce_quotesOnce() = runTest {
        val provider = mockProvider("re-enabled")
        val quote = providerQuote(provider, BigDecimal("2"), BigDecimal.ONE)
        val disabledIdsFlow = MutableStateFlow(setOf(provider.id))
        val fetch = mockk<FetchSwapQuotesUseCase> {
            coEvery {
                this@mockk(any(), any(), any(), any(), any(), any(), any())
            } returns listOf(quote)
        }
        val service = createService(
            providers = listOf(provider),
            scheduler = testScheduler,
            disabledIdsFlow = disabledIdsFlow,
            fetchSwapQuotesUseCase = fetch,
        )
        advanceUntilIdle()
        service.setTokenIn(tokenIn)
        service.setTokenOut(tokenOut)
        service.setAmountOut(BigDecimal.ONE)

        disabledIdsFlow.value = emptySet()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            fetch(any(), any(), any(), any(), any(), any(), any())
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
abstract class SwapQuoteServiceTestFixture {

    private val mainDispatcher = UnconfinedTestDispatcher()

    protected val tokenIn = mockk<Token>()
    protected val tokenOut = mockk<Token>()

    protected fun mockProvider(
        providerId: String,
        quoteAmountOut: BigDecimal = BigDecimal.ONE,
        supports: Boolean = true,
    ): IMultiSwapProvider {
        val expectedTokenIn = tokenIn
        val expectedTokenOut = tokenOut

        return mockk(relaxed = true) {
            every { id } returns providerId
            coEvery { supports(expectedTokenIn, expectedTokenOut) } returns supports
            coEvery {
                fetchQuote(expectedTokenIn, expectedTokenOut, BigDecimal.ONE, any())
            } returns mockk(relaxed = true) {
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    protected fun providerQuote(
        provider: IMultiSwapProvider,
        amountIn: BigDecimal,
        amountOut: BigDecimal,
        amountInMax: BigDecimal? = null,
    ): SwapProviderQuote = SwapProviderQuote(
        provider = provider,
        swapQuote = mockk(relaxed = true) {
            every { this@mockk.tokenIn } returns this@SwapQuoteServiceTestFixture.tokenIn
            every { this@mockk.tokenOut } returns this@SwapQuoteServiceTestFixture.tokenOut
            every { this@mockk.amountIn } returns amountIn
            every { this@mockk.amountOut } returns amountOut
            every { this@mockk.amountInMax } returns amountInMax
        },
    )

    protected fun createService(
        providers: List<IMultiSwapProvider>,
        scheduler: TestCoroutineScheduler,
        disabledIds: Set<String> = emptySet(),
        fetchSwapQuotesUseCase: FetchSwapQuotesUseCase? = null,
        routeResolver: MultiSwapRouteResolver? = null,
    ): SwapQuoteService = createService(
        providers = providers,
        scheduler = scheduler,
        disabledIdsFlow = MutableStateFlow(disabledIds),
        fetchSwapQuotesUseCase = fetchSwapQuotesUseCase,
        routeResolver = routeResolver,
    )

    protected fun createService(
        providers: List<IMultiSwapProvider>,
        scheduler: TestCoroutineScheduler,
        disabledIdsFlow: MutableStateFlow<Set<String>>,
        fetchSwapQuotesUseCase: FetchSwapQuotesUseCase? = null,
        routeResolver: MultiSwapRouteResolver? = null,
    ): SwapQuoteService {
        val dispatcher = StandardTestDispatcher(scheduler)
        val resolvedRouteResolver = routeResolver ?: mockk<MultiSwapRouteResolver>(relaxed = true) {
            coEvery { findRoute(any(), any(), any(), any(), any(), any()) } returns null
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
            resolvedRouteResolver,
            fetchSwapQuotesUseCase ?: FetchSwapQuotesUseCase(mockk(relaxed = true)),
            repository,
            registry,
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )
    }
}
