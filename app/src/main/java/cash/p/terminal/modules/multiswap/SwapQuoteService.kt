package cash.p.terminal.modules.multiswap

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.modules.multiswap.providers.AllBridgeProvider
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.MayaProvider
import cash.p.terminal.modules.multiswap.providers.OneInchProvider
import cash.p.terminal.modules.multiswap.providers.PancakeSwapProvider
import cash.p.terminal.modules.multiswap.providers.PancakeSwapV3Provider
import cash.p.terminal.modules.multiswap.providers.QuickSwapProvider
import cash.p.terminal.modules.multiswap.providers.StonFiProvider
import cash.p.terminal.modules.multiswap.providers.ThorChainProvider
import cash.p.terminal.modules.multiswap.providers.UniswapProvider
import cash.p.terminal.modules.multiswap.providers.UniswapV3Provider
import cash.p.terminal.modules.multiswap.providers.ChangeNowProvider
import cash.p.terminal.modules.multiswap.providers.QuickexProvider
import cash.p.terminal.wallet.Token
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

class SwapQuoteService(
    changeNowProvider: ChangeNowProvider,
    quickexProvider: QuickexProvider
) {
    private val stonFiProvider: StonFiProvider by inject(StonFiProvider::class.java)

    private companion object {
        const val DEBOUNCE_INPUT_MSEC: Long = 300
    }
    private var runQuotationJob: Job? = null

    private val allProviders = listOf(
        OneInchProvider,
        PancakeSwapProvider,
        PancakeSwapV3Provider,
        QuickSwapProvider,
        UniswapProvider,
        UniswapV3Provider,
        changeNowProvider,
        quickexProvider,
        ThorChainProvider,
        MayaProvider,
        AllBridgeProvider,
        stonFiProvider
    )

    private var amountIn: BigDecimal? = null
    private var tokenIn: Token? = null
    private var tokenOut: Token? = null
    private var quoting = false
    private var quotes: List<SwapProviderQuote> = listOf()
    private var preferredProvider: IMultiSwapProvider? = null
    private var error by mutableStateOf<Throwable?>(null)
    private var quote: SwapProviderQuote? = null

    private val _stateFlow = MutableStateFlow(
        State(
            amountIn = amountIn,
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            quoting = quoting,
            quotes = quotes,
            preferredProvider = preferredProvider,
            quote = quote,
            error = error,
        )
    )
    val stateFlow = _stateFlow.asStateFlow()

    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private var quotingJob: Job? = null
    private var settings: Map<String, Any?> = mapOf()

    private fun emitState() {
        _stateFlow.update {
            State(
                amountIn = amountIn,
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                quoting = quoting,
                quotes = quotes,
                preferredProvider = preferredProvider,
                quote = quote,
                error = error,
            )
        }
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        allProviders.forEach {
            try {
                it.start()
            } catch (e: Throwable) {
                Log.d("AAA", "error on starting ${it.id}, $e", e)
            }
        }
    }

    private fun runQuotation(clearQuotes: Boolean = false) {
        quotingJob?.cancel()
        quoting = false
        if (clearQuotes) {
            quotes = listOf()
            quote = null
        }
        error = null

        val tokenIn = tokenIn
        val tokenOut = tokenOut
        val amountIn = amountIn

        if (tokenIn != null && tokenOut != null) {
            quotingJob = coroutineScope.launch {
                val supportedProviders = allProviders.filter { it.supports(tokenIn, tokenOut) }

                if (supportedProviders.isEmpty()) {
                    error = NoSupportedSwapProvider()
                    emitState()
                } else if (amountIn != null && amountIn > BigDecimal.ZERO) {
                    quoting = true
                    emitState()

                    val newQuotes = fetchQuotes(supportedProviders, tokenIn, tokenOut, amountIn)
                        .run { sortedByDescending { it.amountOut } }
                    if (amountIn != this@SwapQuoteService.amountIn) {
                        return@launch // ignore outdated quotes
                    }
                    quotes = newQuotes

                    if (preferredProvider != null && quotes.none { it.provider == preferredProvider }) {
                        preferredProvider = null
                    }

                    if (quotes.isEmpty()) {
                        if (error == null) {
                            error = SwapRouteNotFound()
                        }
                    } else {
                        error = null
                        quote = preferredProvider
                            ?.let { provider -> quotes.find { it.provider == provider } }
                            ?: quotes.firstOrNull()
                    }

                    quoting = false
                    emitState()
                } else {
                    // Amount is null or zero - clear quotes
                    quotes = listOf()
                    quote = null
                    emitState()
                }
            }
        } else {
            // Tokens are null - clear quotes
            quotes = listOf()
            quote = null
            emitState()
        }
    }

    private suspend fun fetchQuotes(
        supportedProviders: List<IMultiSwapProvider>,
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
    ) = coroutineScope {
        supportedProviders
            .map { provider ->
                async {
                    try {
                        withTimeout(5000) {
                            val quote = provider.fetchQuote(tokenIn, tokenOut, amountIn, settings)
                            SwapProviderQuote(provider = provider, swapQuote = quote)
                        }
                    } catch (e: SwapDepositTooSmall) {
                        // Save only lowest min value error
                        if (error == null || ((error as? SwapDepositTooSmall)?.minValue?.let { it > e.minValue } == true)) {
                            error = e
                        }
                        null
                    } catch (e: Throwable) {
                        Log.d("AAA", "fetchQuoteError: ${provider.id}", e)
                        null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
            .sortedWith(
                compareByDescending<SwapProviderQuote> { it.provider.priority }
                    .thenByDescending { it.amountOut }
            )
    }

    fun setAmount(v: BigDecimal?) {
        if (amountIn == v) {
            runQuotationWithDebounce()
            return
        }

        amountIn = v
        preferredProvider = null

        runQuotationWithDebounce()
    }

    private fun runQuotationWithDebounce() {
        runQuotationJob?.cancel()

        quotingJob?.cancel()
        quoting = false
        // Keep previous quotes during requoting to prevent choose provider from closing
        error = null
        emitState()

        runQuotationJob = coroutineScope.launch {
            delay(DEBOUNCE_INPUT_MSEC)
            runQuotation()
        }
    }

    fun setTokenIn(token: Token) {
        if (tokenIn == token) return

        tokenIn = token
        preferredProvider = null
        if (tokenOut == token) {
            tokenOut = null
        }

        runQuotation(clearQuotes = true)
    }

    fun setTokenOut(token: Token) {
        if (tokenOut == token) return

        tokenOut = token
        preferredProvider = null
        if (tokenIn == token) {
            tokenIn = null
        }

        runQuotation(clearQuotes = true)
    }

    fun switchPairs() {
        val tmpTokenIn = tokenIn

        tokenIn = tokenOut
        tokenOut = tmpTokenIn

        amountIn = quote?.amountOut

        runQuotation(clearQuotes = true)
    }

    fun selectQuote(quote: SwapProviderQuote) {
        preferredProvider = quote.provider
        this.quote = quote

        emitState()
    }

    fun reQuote() {
        runQuotation()
    }

    fun setSwapSettings(settings: Map<String, Any?>) {
        this.settings = settings

        runQuotation()
    }

    fun onActionStarted() {
        preferredProvider = quote?.provider
    }

    fun onActionCompleted() {
        reQuote()
    }

    fun getSwapSettings() = settings

    data class State(
        val amountIn: BigDecimal?,
        val tokenIn: Token?,
        val tokenOut: Token?,
        val quoting: Boolean,
        val quotes: List<SwapProviderQuote>,
        val preferredProvider: IMultiSwapProvider?,
        val quote: SwapProviderQuote?,
        val error: Throwable?,
    )
}
