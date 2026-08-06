package cash.p.terminal.core.usecase

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.multiswap.AssetFiatRateService
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapAmountAccuracy
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.Currency
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class IterativeExactOutSearchTest {

    private val tokenIn = mockk<Token> {
        every { decimals } returns 8
    }
    private val tokenOut = mockk<Token>()
    private val currency = Currency("USD", "$", 2, 0)
    private val currencyManager = mockk<CurrencyManager> {
        every { baseCurrency } returns currency
    }
    private val rateService = mockk<AssetFiatRateService>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(
        dispatcher,
        CoroutineScope(dispatcher),
    )

    @Test
    fun search_proportionalQuote_convergesWithinTwoRequests() = runTest {
        mockRates(BigDecimal.ONE, BigDecimal.ONE)
        var requests = 0
        val provider = provider { input ->
            requests++
            input
        }

        val result = search().search(
            provider,
            tokenIn,
            tokenOut,
            BigDecimal("100"),
            emptyMap(),
        )

        assertEquals(SwapAmountAccuracy.Estimated, result?.amountOutAccuracy)
        assertTrue(requests <= 2)
        assertTrue(requireNotNull(result).amountOut >= BigDecimal("100"))
        assertTrue(result.amountOut <= BigDecimal("100.5"))
    }

    @Test
    fun search_ammWithPriceImpact_convergesWithinBudget() = runTest {
        mockRates(BigDecimal.ONE, BigDecimal.ONE)
        var requests = 0
        val provider = provider { input ->
            requests++
            input.multiply(BigDecimal("1000"))
                .divide(BigDecimal("1000").add(input), 16, RoundingMode.HALF_UP)
        }

        val result = search().search(
            provider,
            tokenIn,
            tokenOut,
            BigDecimal("100"),
            emptyMap(),
        )

        assertTrue(requireNotNull(result).amountOut >= BigDecimal("100"))
        assertTrue(result.amountOut <= BigDecimal("100.5"))
        assertTrue(requests <= 4)
    }

    @Test
    fun search_spreadAndFixedFee_convergesWithinBudget() = runTest {
        mockRates(BigDecimal.ONE, BigDecimal.ONE)
        var requests = 0
        val provider = provider { input ->
            requests++
            input.multiply(BigDecimal("0.9")).subtract(BigDecimal("5"))
        }

        val result = search().search(
            provider,
            tokenIn,
            tokenOut,
            BigDecimal("100"),
            emptyMap(),
        )

        assertTrue(requireNotNull(result).amountOut >= BigDecimal("100"))
        assertTrue(result.amountOut <= BigDecimal("100.5"))
        assertTrue(requests <= 4)
    }

    @Test
    fun search_missingPrices_usesExploratoryRequest() = runTest {
        mockRates(null, BigDecimal.ZERO)
        var requests = 0
        val provider = provider { input ->
            requests++
            input
        }

        val result = search().search(
            provider,
            tokenIn,
            tokenOut,
            BigDecimal("10"),
            emptyMap(),
        )

        assertEquals(0, result?.amountOut?.compareTo(BigDecimal("10")))
        assertEquals(1, requests)
    }

    @Test
    fun search_nonTerminatingPriceRatio_doesNotThrow() = runTest {
        mockRates(BigDecimal.ONE, BigDecimal("3"))
        val provider = provider { input -> input.multiply(BigDecimal("3")) }

        val result = search().search(
            provider,
            tokenIn,
            tokenOut,
            BigDecimal("100"),
            emptyMap(),
        )

        assertTrue(requireNotNull(result).amountOut >= BigDecimal("100"))
    }

    @Test
    fun search_nonPositiveOutput_stopsWithoutExtraRequests() = runTest {
        mockRates(null, null)
        var requests = 0
        val provider = provider {
            requests++
            BigDecimal.ZERO
        }

        val result = search().search(
            provider,
            tokenIn,
            tokenOut,
            BigDecimal.TEN,
            emptyMap(),
        )

        assertNull(result)
        assertEquals(1, requests)
    }

    @Test
    fun search_providerThrows_returnsNullAndReportsError() = runTest {
        mockRates(null, null)
        val failure = IllegalStateException("quote unavailable")
        val provider = provider { throw failure }
        var reportedError: Throwable? = null

        val result = search().search(
            provider,
            tokenIn,
            tokenOut,
            BigDecimal.TEN,
            emptyMap(),
        ) { _, error ->
            reportedError = error
        }

        assertNull(result)
        assertSame(failure, reportedError)
    }

    @Test
    fun search_unbracketedModel_returnsNullWithinBudget() = runTest {
        mockRates(BigDecimal.ONE, BigDecimal.ONE)
        var requests = 0
        val provider = provider {
            requests++
            BigDecimal("200")
        }

        val result = search().search(
            provider,
            tokenIn,
            tokenOut,
            BigDecimal("100"),
            emptyMap(),
        )

        assertNull(result)
        assertTrue(requests <= 4)
    }

    @Test
    fun search_sameKey_reusesCachedQuoteWithoutRefreshingCreatedAt() = runTest {
        mockRates(BigDecimal.ONE, BigDecimal.ONE)
        var now = 1_000L
        var requests = 0
        val provider = provider { input ->
            requests++
            input
        }
        val search = search { now }

        val first = search.search(provider, tokenIn, tokenOut, BigDecimal.TEN, emptyMap())
        now += 10_000
        val cached = search.search(provider, tokenIn, tokenOut, BigDecimal.TEN, emptyMap())

        assertSame(first, cached)
        assertEquals(1_000L, cached?.createdAt)
        assertTrue(requests <= 2)

        val requestsBeforeExpiry = requests
        now = 21_001L
        search.search(provider, tokenIn, tokenOut, BigDecimal.TEN, emptyMap())
        assertTrue(requests > requestsBeforeExpiry)
    }

    @Test
    fun search_concurrentSameKey_runsSingleNetworkScenario() = runTest {
        mockRates(null, null)
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val provider = provider { input ->
            requests++
            gate.await()
            input
        }
        val search = search()

        val first = async {
            search.search(provider, tokenIn, tokenOut, BigDecimal.TEN, emptyMap())
        }
        val second = async {
            search.search(provider, tokenIn, tokenOut, BigDecimal.TEN, emptyMap())
        }
        yield()

        assertEquals(1, requests)
        gate.complete(Unit)
        assertSame(first.await(), second.await())
        assertEquals(1, requests)
    }

    @Test
    fun search_firstWaiterCancelled_secondWaiterStillReceivesSharedResult() = runTest {
        mockRates(null, null)
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val provider = provider { input ->
            requests++
            started.complete(Unit)
            gate.await()
            input
        }
        val search = search()

        val first = async {
            search.search(provider, tokenIn, tokenOut, BigDecimal.TEN, emptyMap())
        }
        started.await()
        val second = async {
            search.search(provider, tokenIn, tokenOut, BigDecimal.TEN, emptyMap())
        }
        yield()

        first.cancelAndJoin()
        gate.complete(Unit)

        assertNotNull(second.await())
        assertEquals(1, requests)
    }

    @Test
    fun invalidate_inFlightSearch_replacementUsesNewNetworkScenario() = runTest {
        mockRates(null, null)
        val firstStarted = CompletableDeferred<Unit>()
        val firstGate = CompletableDeferred<Unit>()
        var requests = 0
        val provider = provider { input ->
            requests++
            if (requests == 1) {
                firstStarted.complete(Unit)
                firstGate.await()
            }
            input
        }
        val search = search()
        val stale = async {
            search.search(provider, tokenIn, tokenOut, BigDecimal.TEN, emptyMap())
        }
        firstStarted.await()

        search.invalidate()
        val replacement = search.search(
            provider,
            tokenIn,
            tokenOut,
            BigDecimal.TEN,
            emptyMap(),
        )

        assertNotNull(replacement)
        assertEquals(2, requests)
        try {
            stale.await()
            fail("Stale search must be cancelled by invalidation")
        } catch (_: CancellationException) {
            Unit
        }
    }

    @Test
    fun invalidate_cachedQuote_forcesNewSearch() = runTest {
        mockRates(null, null)
        var requests = 0
        val provider = provider { input ->
            requests++
            input
        }
        val search = search()

        search.search(provider, tokenIn, tokenOut, BigDecimal.ONE, emptyMap())
        search.invalidate()
        search.search(provider, tokenIn, tokenOut, BigDecimal.ONE, emptyMap())

        assertEquals(2, requests)
    }

    private fun search(currentTimeMillis: () -> Long = System::currentTimeMillis) =
        IterativeExactOutSearch(
            rateService,
            currencyManager,
            dispatcherProvider,
        ).apply {
            this.currentTimeMillis = currentTimeMillis
        }

    private fun mockRates(priceIn: BigDecimal?, priceOut: BigDecimal?) {
        coEvery { rateService.rate(tokenIn, currency) } returns priceIn
        coEvery { rateService.rate(tokenOut, currency) } returns priceOut
    }

    private fun provider(output: suspend (BigDecimal) -> BigDecimal): IMultiSwapProvider =
        mockk(relaxed = true) {
            every { id } returns "provider"
            coEvery { fetchQuote(tokenIn, tokenOut, any(), any()) } coAnswers {
                val amountIn = thirdArg<BigDecimal>()
                quote(amountIn, output(amountIn))
            }
        }

    private fun quote(amountIn: BigDecimal, amountOut: BigDecimal): ISwapQuote =
        mockk(relaxed = true) {
            every { this@mockk.amountIn } returns amountIn
            every { this@mockk.amountOut } returns amountOut
            every { tokenIn } returns this@IterativeExactOutSearchTest.tokenIn
            every { tokenOut } returns this@IterativeExactOutSearchTest.tokenOut
        }
}
