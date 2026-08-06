package cash.p.terminal.core.usecase

import cash.p.terminal.modules.multiswap.SwapAmountDirection
import cash.p.terminal.modules.multiswap.SwapExecutionMode
import cash.p.terminal.modules.multiswap.SwapProviderQuote
import cash.p.terminal.modules.multiswap.providers.IExactOutSwapProvider
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.sortedByBest
import cash.p.terminal.wallet.Token
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException

class FetchSwapQuotesUseCase(
    private val iterativeExactOutSearch: IterativeExactOutSearch,
) {
    suspend operator fun invoke(
        providers: List<IMultiSwapProvider>,
        tokenIn: Token,
        tokenOut: Token,
        amount: BigDecimal,
        direction: SwapAmountDirection,
        settings: Map<String, Any?> = emptyMap(),
        onProviderError: ((IMultiSwapProvider, Throwable) -> Unit)? = null,
    ): List<SwapProviderQuote> = coroutineScope {
        val supported = findSupportedProviders(providers, tokenIn, tokenOut, direction)
        if (supported.isEmpty()) return@coroutineScope emptyList()

        supported.map { supportedProvider ->
            async {
                fetchQuote(
                    supportedProvider,
                    tokenIn,
                    tokenOut,
                    amount,
                    direction,
                    settings,
                    onProviderError,
                )
            }
        }.awaitAll().filterNotNull().sortedByBest(direction)
    }

    suspend fun findSupportedProviders(
        providers: List<IMultiSwapProvider>,
        tokenIn: Token,
        tokenOut: Token,
        direction: SwapAmountDirection = SwapAmountDirection.In,
    ): List<SupportedProvider> = coroutineScope {
        providers.map { provider ->
            async {
                try {
                    withTimeoutOrNull(SUPPORTS_TIMEOUT_MS) {
                        resolveSupport(provider, tokenIn, tokenOut, direction)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Timber.d(error, "supports error: ${provider.id}")
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun invalidateSearchCache() {
        iterativeExactOutSearch.invalidate()
    }

    private suspend fun resolveSupport(
        provider: IMultiSwapProvider,
        tokenIn: Token,
        tokenOut: Token,
        direction: SwapAmountDirection,
    ): SupportedProvider? = when (direction) {
        SwapAmountDirection.In -> provider.takeIf { it.supports(tokenIn, tokenOut) }
            ?.let { SupportedProvider(it, nativeExactOut = false) }

        SwapAmountDirection.Out -> {
            val exactOutProvider = provider as? IExactOutSwapProvider
            when {
                exactOutProvider?.supportsExactOut(tokenIn, tokenOut) == true ->
                    SupportedProvider(provider, nativeExactOut = true)

                exactOutProvider != null -> null

                provider.supports(tokenIn, tokenOut) ->
                    SupportedProvider(provider, nativeExactOut = false)

                else -> null
            }
        }
    }

    private suspend fun fetchQuote(
        supportedProvider: SupportedProvider,
        tokenIn: Token,
        tokenOut: Token,
        amount: BigDecimal,
        direction: SwapAmountDirection,
        settings: Map<String, Any?>,
        onProviderError: ((IMultiSwapProvider, Throwable) -> Unit)?,
    ): SwapProviderQuote? {
        val provider = supportedProvider.provider
        return try {
            withTimeoutOrNull(timeout(direction)) {
                when {
                    direction == SwapAmountDirection.In -> SwapProviderQuote(
                        provider = provider,
                        swapQuote = provider.fetchQuote(tokenIn, tokenOut, amount, settings),
                    )

                    supportedProvider.nativeExactOut -> {
                        val exactOutProvider = provider as IExactOutSwapProvider
                        SwapProviderQuote(
                            provider = provider,
                            swapQuote = exactOutProvider.fetchQuoteExactOut(
                                tokenIn,
                                tokenOut,
                                amount,
                                settings,
                            ),
                            executionMode = SwapExecutionMode.NativeExactOut,
                            amountOutAccuracy = exactOutProvider.exactOutAccuracy,
                        )
                    }

                    else -> iterativeExactOutSearch.search(
                        provider,
                        tokenIn,
                        tokenOut,
                        amount,
                        settings,
                        onProviderError,
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onProviderError?.invoke(provider, error)
                ?: Timber.d(error, "fetchQuoteError: ${provider.id}")
            null
        }
    }

    private fun timeout(direction: SwapAmountDirection): Long = when (direction) {
        SwapAmountDirection.In -> EXACT_IN_TIMEOUT_MS
        SwapAmountDirection.Out -> EXACT_OUT_TIMEOUT_MS
    }

    data class SupportedProvider(
        val provider: IMultiSwapProvider,
        val nativeExactOut: Boolean,
    )

    private companion object {
        const val SUPPORTS_TIMEOUT_MS = 5_000L
        const val EXACT_IN_TIMEOUT_MS = 5_000L
        const val EXACT_OUT_TIMEOUT_MS = 12_000L
    }
}
