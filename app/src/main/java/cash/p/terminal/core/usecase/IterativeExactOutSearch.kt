package cash.p.terminal.core.usecase

import cash.p.terminal.modules.multiswap.AssetFiatRateService
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapAmountAccuracy
import cash.p.terminal.modules.multiswap.SwapExecutionMode
import cash.p.terminal.modules.multiswap.SwapProviderQuote
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ln
import kotlin.math.pow

class IterativeExactOutSearch(
    private val assetFiatRateService: AssetFiatRateService,
    private val currencyManager: CurrencyManager,
    dispatcherProvider: DispatcherProvider,
) {
    internal var currentTimeMillis: () -> Long = System::currentTimeMillis
    private val scope = CoroutineScope(dispatcherProvider.io + SupervisorJob())
    private val mutex = Mutex()
    private val results = mutableMapOf<Key, SwapProviderQuote>()
    private val inFlight = mutableMapOf<Key, InFlight>()
    private var generation = 0

    suspend fun search(
        provider: IMultiSwapProvider,
        tokenIn: Token,
        tokenOut: Token,
        target: BigDecimal,
        settings: Map<String, Any?>,
        onProviderError: ((IMultiSwapProvider, Throwable) -> Unit)? = null,
    ): SwapProviderQuote? {
        val key = Key(provider.id, tokenIn, tokenOut, target, settings.hashCode())
        val entry = mutex.withLock {
            cachedResult(key)?.let { return it }
            inFlight[key]?.also { it.waiters++ } ?: createInFlight(
                key = key,
                provider = provider,
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                target = target,
                settings = settings,
                onProviderError = onProviderError,
            ).also {
                it.waiters = 1
                inFlight[key] = it
            }
        }

        entry.deferred.start()
        return try {
            entry.deferred.await()
        } finally {
            release(key, entry)
        }
    }

    suspend fun invalidate() {
        val detached = mutex.withLock {
            generation++
            results.clear()
            inFlight.values.toList().also { inFlight.clear() }
        }
        detached.forEach { it.deferred.cancel() }
    }

    private fun createInFlight(
        key: Key,
        provider: IMultiSwapProvider,
        tokenIn: Token,
        tokenOut: Token,
        target: BigDecimal,
        settings: Map<String, Any?>,
        onProviderError: ((IMultiSwapProvider, Throwable) -> Unit)?,
    ): InFlight {
        val entryGeneration = generation
        val entry = InFlight(entryGeneration)
        entry.deferred = scope.async(start = CoroutineStart.LAZY) {
            val result = findQuote(
                provider,
                tokenIn,
                tokenOut,
                target,
                settings,
                onProviderError,
            )
            if (result != null) {
                cache(key, entry.generation, result)
            }
            result
        }
        return entry
    }

    private suspend fun release(key: Key, entry: InFlight) = withContext(NonCancellable) {
        val cancel = mutex.withLock {
            entry.waiters--
            if (entry.waiters == 0 && inFlight[key] === entry) {
                inFlight.remove(key)
                true
            } else {
                false
            }
        }
        if (cancel) entry.deferred.cancel()
    }

    private fun cachedResult(key: Key): SwapProviderQuote? {
        val result = results[key] ?: return null
        return result.takeIf { currentTimeMillis() - it.createdAt <= CACHE_TTL_MS }
            ?: run {
                results.remove(key)
                null
            }
    }

    private suspend fun cache(key: Key, entryGeneration: Int, quote: SwapProviderQuote) {
        mutex.withLock {
            if (entryGeneration != generation) return@withLock
            val now = currentTimeMillis()
            results.entries.removeAll { now - it.value.createdAt > CACHE_TTL_MS }
            results[key] = quote
        }
    }

    private suspend fun findQuote(
        provider: IMultiSwapProvider,
        tokenIn: Token,
        tokenOut: Token,
        target: BigDecimal,
        settings: Map<String, Any?>,
        onProviderError: ((IMultiSwapProvider, Throwable) -> Unit)?,
    ): SwapProviderQuote? {
        return try {
            performSearch(provider, tokenIn, tokenOut, target, settings)
                .also {
                    if (it == null) Timber.d("Exact-out search did not converge: ${provider.id}")
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onProviderError?.invoke(provider, error)
                ?: Timber.d(error, "Exact-out search failed: ${provider.id}")
            null
        }
    }

    private suspend fun performSearch(
        provider: IMultiSwapProvider,
        tokenIn: Token,
        tokenOut: Token,
        target: BigDecimal,
        settings: Map<String, Any?>,
    ): SwapProviderQuote? {
        val upperTarget = target.multiply(ACCEPTANCE_MULTIPLIER)
        val progress = SearchProgress(initialCandidate(tokenIn, tokenOut, target))

        repeat(MAX_REQUESTS) {
            val candidate = progress.nextUnattempted(tokenIn.decimals) ?: return null
            val quote = provider.fetchQuote(tokenIn, tokenOut, candidate, settings)
            val current = Probe(candidate, quote.amountOut)
            if (current.output >= target && current.output <= upperTarget) {
                return estimatedQuote(provider, quote)
            }

            progress.record(current, target)
            progress.candidate = nextCandidate(
                current = current,
                previous = progress.previous,
                lower = progress.lower,
                upper = progress.upper,
                target = target,
                decimals = tokenIn.decimals,
            ) ?: return null
            progress.previous = current
        }
        return null
    }

    private fun estimatedQuote(
        provider: IMultiSwapProvider,
        quote: ISwapQuote,
    ) = SwapProviderQuote(
        provider = provider,
        swapQuote = quote,
        executionMode = SwapExecutionMode.ExactIn,
        amountOutAccuracy = SwapAmountAccuracy.Estimated,
        createdAt = currentTimeMillis(),
    )

    private suspend fun initialCandidate(
        tokenIn: Token,
        tokenOut: Token,
        target: BigDecimal,
    ): BigDecimal {
        val currency = currencyManager.baseCurrency
        val priceIn = assetFiatRateService.rate(tokenIn, currency)
        val priceOut = assetFiatRateService.rate(tokenOut, currency)
        if (cannotCalculateExponent(priceIn, priceOut)) {
            return target
        }
        requireNotNull(priceIn)
        requireNotNull(priceOut)
        val rate = priceIn.divide(priceOut, MathContext.DECIMAL64)
        return target.divide(rate, MathContext.DECIMAL64).multiply(START_MULTIPLIER)
    }

    private fun cannotCalculateExponent(priceIn: BigDecimal?, priceOut: BigDecimal?): Boolean =
        priceIn == null || priceOut == null ||
            priceIn.signum() <= 0 || priceOut.signum() <= 0

    private fun nextCandidate(
        current: Probe,
        previous: Probe?,
        lower: Probe?,
        upper: Probe?,
        target: BigDecimal,
        decimals: Int,
    ): BigDecimal? {
        val proposed = logarithmicStep(current, previous, target)
        val withinBracket = proposed?.takeIf { candidate ->
            (lower == null || candidate > lower.input) && (upper == null || candidate < upper.input)
        }
        return normalizeCandidate(withinBracket ?: bisect(lower, upper) ?: return null, decimals)
    }

    private fun logarithmicStep(
        current: Probe,
        previous: Probe?,
        target: BigDecimal,
    ): BigDecimal? {
        if (current.output <= BigDecimal.ZERO) return null

        val exponent = if (previous == null) {
            1.0
        } else {
            if (cannotCalculateExponent(current, previous)) return null
            val denominator = ln(current.input.toDouble() / previous.input.toDouble())
            val value = ln(current.output.toDouble() / previous.output.toDouble()) / denominator
            if (!value.isFinite()) return null
            value.coerceIn(MIN_EXPONENT, MAX_EXPONENT)
        }

        val value = current.input.toDouble() *
            (target.toDouble() / current.output.toDouble()).pow(1.0 / exponent) *
            MID_BAND_MULTIPLIER
        return value.takeIf(Double::isFinite)?.let(BigDecimal::valueOf)
    }

    private fun cannotCalculateExponent(current: Probe, previous: Probe): Boolean =
        current.input.compareTo(previous.input) == 0 ||
            current.output.compareTo(previous.output) == 0 ||
            previous.output <= BigDecimal.ZERO

    private fun bisect(lower: Probe?, upper: Probe?): BigDecimal? {
        if (lower == null || upper == null) return null
        return lower.input.add(upper.input).divide(TWO, MathContext.DECIMAL64)
    }

    private fun normalizeCandidate(value: BigDecimal, decimals: Int): BigDecimal? {
        if (value <= BigDecimal.ZERO) return null
        return try {
            value.setScale(decimals, RoundingMode.UP).stripTrailingZeros()
        } catch (_: ArithmeticException) {
            null
        }
    }

    private data class Probe(
        val input: BigDecimal,
        val output: BigDecimal,
    )

    private inner class SearchProgress(
        var candidate: BigDecimal,
        var previous: Probe? = null,
        var lower: Probe? = null,
        var upper: Probe? = null,
        private val attempted: MutableSet<BigDecimal> = mutableSetOf(),
    ) {
        fun nextUnattempted(decimals: Int): BigDecimal? {
            val normalized = normalizeCandidate(candidate, decimals) ?: return null
            return normalized.takeIf(attempted::add)
        }

        fun record(probe: Probe, target: BigDecimal) {
            if (probe.output < target) {
                if (lower == null || probe.output > requireNotNull(lower).output) {
                    lower = probe
                }
            } else {
                if (upper == null || probe.output < requireNotNull(upper).output) {
                    upper = probe
                }
            }
        }
    }

    private data class Key(
        val providerId: String,
        val tokenIn: Token,
        val tokenOut: Token,
        val target: BigDecimal,
        val settingsHash: Int,
    )

    private class InFlight(
        val generation: Int,
        var waiters: Int = 0,
    ) {
        lateinit var deferred: Deferred<SwapProviderQuote?>
    }

    private companion object {
        const val MAX_REQUESTS = 4
        const val CACHE_TTL_MS = 20_000L
        const val MIN_EXPONENT = 0.2
        const val MAX_EXPONENT = 2.0
        const val MID_BAND_MULTIPLIER = 1.0025
        val TWO = BigDecimal(2)
        val ACCEPTANCE_MULTIPLIER = BigDecimal("1.005")
        val START_MULTIPLIER = BigDecimal("1.02")
    }
}
