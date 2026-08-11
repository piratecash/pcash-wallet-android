package cash.p.terminal.modules.multiswap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.core.usecase.FetchSwapQuotesUseCase
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRegistry
import cash.p.terminal.modules.multiswap.providers.SwapProvidersRepository
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal

enum class SwapQuoteSelectionTarget { Primary, RouteLeg2 }

class SwapQuoteService(
    private val routeResolver: MultiSwapRouteResolver,
    private val fetchSwapQuotesUseCase: FetchSwapQuotesUseCase,
    private val swapProvidersRepository: SwapProvidersRepository,
    private val swapProvidersRegistry: SwapProvidersRegistry,
    private val dispatcherProvider: DispatcherProvider,
) {
    private companion object {
        const val DEBOUNCE_INPUT_IN_MSEC = 300L
        const val DEBOUNCE_INPUT_OUT_MSEC = 600L
    }

    private var runQuotationJob: Job? = null

    private val allProviders: List<IMultiSwapProvider>
        get() = swapProvidersRegistry.providers

    val providers: List<IMultiSwapProvider> get() = allProviders

    private val enabledProviders: List<IMultiSwapProvider>
        get() = allProviders.filterNot { swapProvidersRepository.isDisabled(it.id) }

    private val disabledByUserProviders: List<IMultiSwapProvider>
        get() = allProviders.filter { swapProvidersRepository.isDisabled(it.id) }

    fun findProviderById(id: String): IMultiSwapProvider? =
        swapProvidersRegistry.findById(id)

    private var amount: BigDecimal? = null
    private var direction = SwapAmountDirection.In
    private var tokenIn: Token? = null
    private var tokenOut: Token? = null
    private var quoting = false
    private var quotes: List<SwapProviderQuote> = listOf()
    private var preferredProvider: IMultiSwapProvider? = null
    private var error by mutableStateOf<Throwable?>(null)
    private var quote: SwapProviderQuote? = null
    private var multiSwapRoute: MultiSwapRoute? = null

    private val _stateFlow = MutableStateFlow(
        State(
            amountIn = null,
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            quoting = quoting,
            quotes = quotes,
            preferredProvider = preferredProvider,
            quote = quote,
            error = error,
            multiSwapRoute = multiSwapRoute,
            direction = direction,
            requestedAmountOut = null,
            amountInMax = null,
        )
    )
    val stateFlow = _stateFlow.asStateFlow()

    private val coroutineScope = CoroutineScope(dispatcherProvider.io + SupervisorJob())
    private var quotingJob: Job? = null
    private var settings: Map<String, Any?> = mapOf()
    val swapSettings: Map<String, Any?>
        get() = settings
    private var previousDisabledIds = swapProvidersRepository.disabledIds.value

    fun clear() {
        coroutineScope.cancel()
    }

    init {
        coroutineScope.launch {
            swapProvidersRepository.disabledIds
                .drop(1)
                .collect { disabledIds ->
                    onDisabledProvidersChanged(disabledIds)
                }
        }
    }

    private fun onDisabledProvidersChanged(disabledIds: Set<String>) {
        val newlyEnabledIds = previousDisabledIds - disabledIds
        previousDisabledIds = disabledIds
        if (direction == SwapAmountDirection.Out &&
            newlyEnabledIds.any { id -> quotes.none { it.provider.id == id } }
        ) {
            preferredProvider = quote?.provider
            runQuotationJob?.cancel()
            runQuotation()
            return
        }
        if (multiSwapRoute != null) {
            // Quotes belong to leg 1 of the route, they cannot be reused as direct quotes
            runQuotation()
            return
        }

        val enabledQuotes = quotes.filterNot {
            swapProvidersRepository.isDisabled(it.provider.id)
        }
        val currentProviderId = quote?.provider?.id
        val currentStillEnabled = enabledQuotes.any { it.provider.id == currentProviderId }

        when {
            currentStillEnabled -> Unit
            enabledQuotes.isNotEmpty() -> {
                quote = enabledQuotes.first()
                error = null
                multiSwapRoute = null
                emitState()
            }
            direction == SwapAmountDirection.Out -> {
                quote = null
                error = if (quoting) null else NoExactOutSwapProvider()
                multiSwapRoute = null
                emitState()
            }
            else -> runQuotation()
        }
    }

    private fun emitState() {
        _stateFlow.update {
            State(
                amountIn = when (direction) {
                    SwapAmountDirection.In -> amount
                    SwapAmountDirection.Out -> quote?.amountIn
                },
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                quoting = quoting,
                quotes = quotes,
                preferredProvider = preferredProvider,
                quote = quote,
                error = error,
                multiSwapRoute = multiSwapRoute,
                direction = direction,
                requestedAmountOut = amount.takeIf { direction == SwapAmountDirection.Out },
                amountInMax = quote?.amountInMax,
            )
        }
    }

    suspend fun start() = withContext(dispatcherProvider.io) {
        allProviders.forEach {
            try {
                it.start()
            } catch (e: Throwable) {
                Timber.d(e, "error on starting ${it.id}")
            }
        }
    }

    private fun runQuotation(clearQuotes: Boolean = false) {
        quotingJob?.cancel()
        quoting = false
        if (clearQuotes) {
            quotes = listOf()
            quote = null
            multiSwapRoute = null
        }
        error = null

        if (clearQuotes) {
            emitState()
        }

        val tokenIn = tokenIn
        val tokenOut = tokenOut
        val amount = amount
        val direction = direction

        if (tokenIn != null && tokenOut != null) {
            quotingJob = coroutineScope.launch {
                if (amount != null && amount > BigDecimal.ZERO) {
                    quoting = true
                    emitState()

                    val newQuotes = fetchSwapQuotesUseCase(
                        providers = if (direction == SwapAmountDirection.Out) {
                            enabledProviders
                        } else {
                            allProviders
                        },
                        tokenIn = tokenIn,
                        tokenOut = tokenOut,
                        amount = amount,
                        direction = direction,
                        settings = settings,
                        onProviderError = { _, e ->
                            when (e) {
                                is SwapDepositTooSmall -> {
                                    val current = error as? SwapDepositTooSmall
                                    if (current == null || current.minValue > e.minValue) {
                                        error = e
                                    }
                                }
                                is SwapAmountOutOfRange -> {
                                    if (error !is SwapDepositTooSmall) {
                                        error = e
                                    }
                                }
                                else -> Timber.d(e, "fetchQuoteError")
                            }
                        },
                    )
                    if (amount != this@SwapQuoteService.amount || direction != this@SwapQuoteService.direction) {
                        return@launch // ignore outdated quotes
                    }
                    quotes = newQuotes

                    val enabledQuotes = newQuotes.filterNot {
                        swapProvidersRepository.isDisabled(it.provider.id)
                    }

                    if (enabledQuotes.isEmpty()) {
                        if (direction == SwapAmountDirection.In) {
                            tryFallbackToMultiSwapRoute(
                                tokenIn,
                                tokenOut,
                                amount,
                                noDirectProviders = true,
                            )
                            multiSwapRoute?.let { route ->
                                // The picker switches the first leg of the route, so it must list leg 1 quotes
                                quotes = route.leg1Quotes
                            }
                            quote = multiSwapRoute?.selectedLeg1Quote
                        } else {
                            multiSwapRoute = null
                            quote = null
                            error = NoExactOutSwapProvider()
                        }
                    } else {
                        error = null
                        multiSwapRoute = null
                        quote = preferredProvider
                            ?.let { provider -> enabledQuotes.find { it.provider == provider } }
                            ?: enabledQuotes.firstOrNull()
                    }

                    if (preferredProvider != null && quotes.none { it.provider == preferredProvider }) {
                        preferredProvider = null
                    }

                    quoting = false
                    emitState()
                } else {
                    // Amount is null or zero - clear quotes, don't set error yet
                    quotes = listOf()
                    quote = null
                    multiSwapRoute = null
                    emitState()
                }
            }
        } else {
            // Tokens are null - clear quotes
            quotes = listOf()
            quote = null
            multiSwapRoute = null
            emitState()
        }
    }

    private suspend fun tryFallbackToMultiSwapRoute(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        noDirectProviders: Boolean,
    ) {
        val route = routeResolver.findRoute(
            enabledProviders,
            tokenIn,
            tokenOut,
            amountIn,
            settings,
            preferredProvider,
        )
        if (route != null) {
            multiSwapRoute = route
            error = null
        } else {
            multiSwapRoute = null
            error = error
                ?.takeIf { it is SwapDepositTooSmall || it is SwapAmountOutOfRange }
                ?: resolveEmptyResultError(tokenIn, tokenOut, noDirectProviders)
        }
    }

    private suspend fun resolveEmptyResultError(
        tokenIn: Token,
        tokenOut: Token,
        noDirectProviders: Boolean,
    ): Throwable = when {
        !noDirectProviders -> SwapRouteNotFound()
        disabledByUserProviders.isEmpty() -> NoSupportedSwapProvider()
        fetchSwapQuotesUseCase
            .findSupportedProviders(disabledByUserProviders, tokenIn, tokenOut)
            .isNotEmpty() -> NoEnabledSwapProvider()
        else -> NoSupportedSwapProvider()
    }

    fun setAmountIn(value: BigDecimal?) {
        setAmount(value, SwapAmountDirection.In)
    }

    fun setAmountOut(value: BigDecimal?) {
        setAmount(value, SwapAmountDirection.Out)
    }

    private fun setAmount(value: BigDecimal?, newDirection: SwapAmountDirection) {
        if (amount == value && direction == newDirection) {
            runQuotationWithDebounce()
            return
        }

        amount = value
        direction = newDirection
        preferredProvider = null

        runQuotationWithDebounce()
    }

    private fun runQuotationWithDebounce() {
        runQuotationJob?.cancel()

        quotingJob?.cancel()
        // A fresh quote is pending: mark quoting so the swap button stays disabled
        // until real quotes for the current input arrive (covers the debounce window).
        quoting = true
        // Keep previous quotes during requoting to prevent choose provider from closing
        error = null
        emitState()

        runQuotationJob = coroutineScope.launch {
            delay(
                when (direction) {
                    SwapAmountDirection.In -> DEBOUNCE_INPUT_IN_MSEC
                    SwapAmountDirection.Out -> DEBOUNCE_INPUT_OUT_MSEC
                }
            )
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

        amount = when (direction) {
            SwapAmountDirection.In -> multiSwapRoute?.selectedLeg2Quote?.amountOut ?: quote?.amountOut
            SwapAmountDirection.Out -> amount
        }
        direction = SwapAmountDirection.In

        runQuotation(clearQuotes = true)
    }

    fun selectQuote(
        quote: SwapProviderQuote,
        target: SwapQuoteSelectionTarget,
    ): Boolean {
        if (target == SwapQuoteSelectionTarget.RouteLeg2) {
            val route = multiSwapRoute ?: return false
            val currentQuote = route.leg2Quotes.firstOrNull { it == quote } ?: return false
            multiSwapRoute = route.copy(selectedLeg2Quote = currentQuote)
            emitState()
            return true
        }

        preferredProvider = quote.provider
        val currentQuote = quotes.find { it.provider == quote.provider }

        // A route quotes leg 2 for the output of leg 1, so switching leg 1 requires a new route.
        // A quote missing from the current list comes from an outdated picker snapshot and must
        // never be published as is - it may be a leg 1 quote whose amountOut is the intermediate token.
        if (multiSwapRoute != null || currentQuote == null) {
            // Mark quoting synchronously so the swap button cannot act on the superseded quote
            quoting = true
            emitState()
            runQuotation()
        } else {
            this.quote = currentQuote
            emitState()
        }
        return true
    }

    fun reQuote() {
        runQuotation()
    }

    fun invalidateAndReQuote() {
        runQuotationJob?.cancel()
        coroutineScope.launch {
            quotingJob?.cancelAndJoin()
            fetchSwapQuotesUseCase.invalidateSearchCache()
            reQuote()
        }
    }

    fun setSwapSettings(settings: Map<String, Any?>) {
        this.settings = settings

        runQuotation()
    }

    fun onActionStarted() {
        preferredProvider = quote?.provider
    }

    fun onActionCompleted() {
        invalidateAndReQuote()
    }

    data class State(
        val amountIn: BigDecimal?,
        val tokenIn: Token?,
        val tokenOut: Token?,
        val quoting: Boolean,
        val quotes: List<SwapProviderQuote>,
        val preferredProvider: IMultiSwapProvider?,
        val quote: SwapProviderQuote?,
        val error: Throwable?,
        val multiSwapRoute: MultiSwapRoute?,
        val direction: SwapAmountDirection,
        val requestedAmountOut: BigDecimal?,
        val amountInMax: BigDecimal?,
    )
}
