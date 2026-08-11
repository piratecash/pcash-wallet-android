package cash.p.terminal.modules.multiswap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.HSCaution
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.exchange.ButtonState
import cash.p.terminal.modules.multiswap.exchange.LegStatus
import cash.p.terminal.modules.multiswap.exchange.LegUiState
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangePresentation
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangeUiState
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.coinImageUrl
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.Currency
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal
import java.math.RoundingMode

private enum class MultiSwapRouteRefreshState { Idle, Refreshing, Failed }

class SwapViewModel(
    private val quoteService: SwapQuoteService,
    private val balanceService: TokenBalanceService,
    private val priceImpactService: PriceImpactService,
    currencyManager: CurrencyManager,
    private val fiatServiceIn: FiatService,
    private val fiatServiceOut: FiatService,
    private val timerService: TimerService,
    private val networkAvailabilityService: NetworkAvailabilityService,
    private val marketKit: MarketKitWrapper,
    tokenIn: Token?,
    tokenOut: Token?
) : ViewModelUiState<SwapUiState>() {

    private val quoteLifetime = 20
    internal val quotesFlow = quoteService.stateFlow.map { it.quotes }

    private var networkState = networkAvailabilityService.stateFlow.value
    private var quoteState = quoteService.stateFlow.value
    private var balanceState = balanceService.stateFlow.value
    private var priceImpactState = priceImpactService.stateFlow.value
    private var timerState = timerService.stateFlow.value
    private var multiSwapRouteInfoSnapshot = quoteState.multiSwapRoute
    private var multiSwapRouteRefreshState by mutableStateOf(MultiSwapRouteRefreshState.Idle)

    var timeRemainingProgress by mutableStateOf<Float?>(null)
        private set

    private var fiatAmountIn: BigDecimal? = null
    private var fiatAmountOut: BigDecimal? = null
    private var fiatAmountInInputEnabled = false
    private var fiatAmountOutInputEnabled = false
    private var fiatInputDirection: SwapAmountDirection? = null
    private val currency = currencyManager.baseCurrency
    private val balanceHiddenManager: IBalanceHiddenManager by inject(IBalanceHiddenManager::class.java)
    private val walletUseCase: WalletUseCase by inject(WalletUseCase::class.java)
    private var warningMessage: TranslatableString? = null

    init {
        balanceService.start(viewModelScope)

        viewModelScope.launch {
            quoteService.start()
        }

        viewModelScope.launch {
            balanceHiddenManager.anyWalletVisibilityChangedFlow.collect {
                emitState()
            }
        }
        viewModelScope.launch {
            networkAvailabilityService.stateFlow.collect {
                handleUpdatedNetworkState(it)
            }
        }
        viewModelScope.launch {
            quoteService.stateFlow.collect {
                handleUpdatedQuoteState(it)
            }
        }
        viewModelScope.launch {
            balanceService.stateFlow.collect {
                handleUpdatedBalanceState(it)
            }
        }
        viewModelScope.launch {
            priceImpactService.stateFlow.collect {
                handleUpdatedPriceImpactState(it)
            }
        }
        viewModelScope.launch {
            fiatServiceIn.stateFlow.collect {
                handleUpdatedFiatState(it, SwapAmountDirection.In)
            }
        }
        viewModelScope.launch {
            fiatServiceOut.stateFlow.collect {
                handleUpdatedFiatState(it, SwapAmountDirection.Out)
            }
        }
        viewModelScope.launch {
            timerService.stateFlow.collect {
                val prevTimeout = timerState.timeout
                timerState = it

                timeRemainingProgress = it.remaining?.let { remaining ->
                    remaining / quoteLifetime.toFloat()
                }

                if (it.timeout != prevTimeout) {
                    emitState()
                }
            }
        }

        fiatServiceIn.setCurrency(currency)
        addCloseable(fiatServiceIn)
        fiatServiceOut.setCurrency(currency)
        addCloseable(fiatServiceOut)
        networkAvailabilityService.start(viewModelScope)
        tokenIn?.let {
            quoteService.setTokenIn(it)
        }
        tokenOut?.let {
            quoteService.setTokenOut(it)
        }
    }

    override fun createState(): SwapUiState {
        val feeToken = balanceState.feeToken
        val fee = balanceState.fee
        val networkFeeFiatAmount = if (feeToken != null && fee != null) {
            marketKit.coinPrice(feeToken.coin.uid, currency.code)?.let { coinPrice ->
                fee * coinPrice.value
            }
        } else null

        return SwapUiState(
            amountIn = quoteState.amountIn,
            displayAmountOut = displayAmountOut,
            tokenIn = quoteState.tokenIn,
            tokenOut = quoteState.tokenOut,
            quoting = quoteState.quoting,
            quotes = quoteState.quotes,
            preferredProvider = quoteState.preferredProvider,
            quote = quoteState.quote,
            error = networkState.error ?: quoteState.error ?: balanceState.error
                ?: priceImpactState.error,
            availableBalance = balanceState.balance,
            displayBalance = balanceState.displayBalance,
            networkFee = fee,
            networkFeeFiatAmount = networkFeeFiatAmount,
            feeToken = feeToken,
            feeCoinBalance = balanceState.feeCoinBalance,
            insufficientFeeBalance = balanceState.insufficientFeeBalance,
            balanceHidden = quoteState.tokenIn?.let {
                balanceHiddenManager.isWalletBalanceHidden(it.tokenQuery.id)
            } ?: balanceHiddenManager.balanceHidden,
            warningMessage = warningMessage,
            priceImpact = priceImpactState.priceImpact,
            priceImpactLevel = priceImpactState.priceImpactLevel,
            priceImpactCaution = priceImpactState.priceImpactCaution,
            fiatAmountIn = fiatAmountIn,
            fiatAmountOut = fiatAmountOut,
            fiatPriceImpact = priceImpactState.fiatPriceImpact,
            currency = currency,
            fiatAmountInInputEnabled = fiatAmountInInputEnabled,
            fiatAmountOutInputEnabled = fiatAmountOutInputEnabled,
            fiatPriceImpactLevel = priceImpactState.fiatPriceImpactLevel,
            timeout = timerState.timeout,
            multiSwapRoute = quoteState.multiSwapRoute,
            direction = quoteState.direction,
            requestedAmountOut = quoteState.requestedAmountOut,
            amountInMax = quoteState.amountInMax,
            amountOutAccuracy = quoteState.quote?.amountOutAccuracy ?: SwapAmountAccuracy.Exact,
            quoteCautions = quoteState.quote?.cautions.orEmpty(),
        )
    }

    private fun fetchWarningMessageAsync() {
        viewModelScope.launch {
            warningMessage = obtainWarningMessage()
            emitState() // Update UI again once warning is fetched
        }
    }

    private fun handleUpdatedNetworkState(networkState: NetworkAvailabilityService.State) {
        this.networkState = networkState

        emitState()

        if (networkState.networkAvailable && quoteState.error != null) {
            reQuote()
        }
    }

    private fun handleUpdatedBalanceState(balanceState: TokenBalanceService.State) {
        this.balanceState = balanceState

        emitState()
    }

    private fun handleUpdatedQuoteState(quoteState: SwapQuoteService.State) {
        updateMultiSwapRouteRefreshState(quoteState)
        this.quoteState = quoteState

        balanceService.setToken(quoteState.tokenIn)
        balanceService.setAmount(quoteState.amountInMax ?: quoteState.amountIn)

        priceImpactService.setPriceImpact(
            quoteState.quote?.priceImpact?.negate(),
            quoteState.quote?.provider?.title
        )

        fiatServiceIn.setToken(quoteState.tokenIn)
        fiatServiceIn.setAmount(quoteState.amountIn)
        fiatServiceOut.setToken(quoteState.tokenOut)
        fiatServiceOut.setAmount(displayAmountOut)

        emitState() // Emit immediately so UI updates without waiting for warning
        fetchWarningMessageAsync()

        if (quoteState.quote != null) {
            val elapsedMillis = System.currentTimeMillis() - quoteState.quote.createdAt
            val remainingSeconds = (quoteLifetime - elapsedMillis / 1000).coerceAtLeast(0)
            timerService.start(remainingSeconds)
        } else {
            timerService.reset()
        }
    }

    private fun handleUpdatedPriceImpactState(priceImpactState: PriceImpactService.State) {
        this.priceImpactState = priceImpactState

        emitState()
    }

    private fun handleUpdatedFiatState(
        state: FiatService.State,
        direction: SwapAmountDirection,
    ) {
        when (direction) {
            SwapAmountDirection.In -> {
                fiatAmountInInputEnabled = state.rate != null
                fiatAmountIn = state.fiatAmount
                priceImpactService.setFiatAmountIn(state.fiatAmount)
            }
            SwapAmountDirection.Out -> {
                fiatAmountOutInputEnabled = state.rate != null
                fiatAmountOut = state.fiatAmount
                priceImpactService.setFiatAmountOut(state.fiatAmount)
            }
        }
        if (state.inputSource == FiatService.InputSource.Fiat &&
            fiatInputDirection == direction
        ) {
            setQuoteAmount(state.amount, direction)
        }
        emitState()
    }

    fun onSelectQuote(quote: SwapProviderQuote) {
        quoteService.selectQuote(quote, SwapQuoteSelectionTarget.Primary)
    }

    fun onSelectLeg2Quote(quote: SwapProviderQuote): Boolean {
        if (!canContinueMultiSwapRoute()) return false
        return quoteService.selectQuote(quote, SwapQuoteSelectionTarget.RouteLeg2)
    }

    fun canContinueMultiSwapRoute(): Boolean = isMultiSwapRouteReady(
        quoting = quoteState.quoting,
        timeout = timerState.timeout,
        route = quoteState.multiSwapRoute,
    )

    fun refreshExpiredMultiSwapRoute() {
        if (shouldRefreshMultiSwapRoute(quoteState.quoting, timerState.timeout, quoteState.multiSwapRoute)) {
            refreshMultiSwapRoute()
        }
    }

    fun refreshMultiSwapRoute() {
        if (multiSwapRouteRefreshState == MultiSwapRouteRefreshState.Refreshing ||
            quoteState.quoting ||
            multiSwapRouteInfoSnapshot == null
        ) {
            return
        }
        multiSwapRouteRefreshState = MultiSwapRouteRefreshState.Refreshing
        reQuote()
    }

    fun multiSwapRouteInfoUiState(uiState: SwapUiState): MultiSwapExchangeUiState? {
        val route = multiSwapRouteInfoRoute(uiState) ?: return null
        val leg1 = route.selectedLeg1Quote
        val leg2 = route.selectedLeg2Quote
        val buttonState = multiSwapRouteInfoButtonState(uiState, route)
        return MultiSwapExchangeUiState(
            leg1 = leg1.toLegUiState(uiState.currency, uiState.fiatAmountIn, fiatAmount(leg1.tokenOut, leg1.amountOut)),
            leg2 = leg2.toLegUiState(uiState.currency, fiatAmount(leg2.tokenIn, leg2.amountIn), uiState.fiatAmountOut),
            buttonState = buttonState,
            showContinueLater = false,
            presentation = MultiSwapExchangePresentation.RouteInfo,
            routeExplanationTokens = listOf(
                leg1.tokenIn.coin.code,
                route.intermediateCoin.coin.code,
                leg2.tokenOut.coin.code,
            ),
            leg2ProviderClickable = buttonState == ButtonState.Enabled && route.leg2Quotes.isNotEmpty(),
        )
    }

    private fun multiSwapRouteInfoRoute(uiState: SwapUiState): MultiSwapRoute? =
        uiState.multiSwapRoute ?: multiSwapRouteInfoSnapshot?.takeIf {
            multiSwapRouteRefreshState != MultiSwapRouteRefreshState.Idle
        }

    private fun multiSwapRouteInfoButtonState(uiState: SwapUiState, route: MultiSwapRoute): ButtonState = when {
        multiSwapRouteRefreshState == MultiSwapRouteRefreshState.Failed -> ButtonState.Refresh
        multiSwapRouteRefreshState == MultiSwapRouteRefreshState.Refreshing || uiState.quoting || uiState.timeout ->
            ButtonState.Quoting
        isMultiSwapRouteReady(uiState.quoting, uiState.timeout, route) -> ButtonState.Enabled
        else -> ButtonState.Disabled
    }

    private fun updateMultiSwapRouteRefreshState(quoteState: SwapQuoteService.State) {
        quoteState.multiSwapRoute?.let { multiSwapRouteInfoSnapshot = it }
        if (quoteState.quoting) {
            if (this.quoteState.multiSwapRoute != null ||
                multiSwapRouteRefreshState != MultiSwapRouteRefreshState.Idle
            ) {
                multiSwapRouteRefreshState = MultiSwapRouteRefreshState.Refreshing
            }
            return
        }
        if (multiSwapRouteRefreshState == MultiSwapRouteRefreshState.Idle) return

        multiSwapRouteRefreshState = when {
            quoteState.error != null -> MultiSwapRouteRefreshState.Failed
            quoteState.multiSwapRoute != null -> MultiSwapRouteRefreshState.Idle
            else -> {
                multiSwapRouteInfoSnapshot = null
                MultiSwapRouteRefreshState.Idle
            }
        }
    }

    fun onEnterAmount(v: BigDecimal?) = setTokenAmount(v, SwapAmountDirection.In)
    fun onEnterAmountOut(v: BigDecimal?) = setTokenAmount(v, SwapAmountDirection.Out)
    fun onEnterFiatAmount(v: BigDecimal?) = setFiatAmount(v, SwapAmountDirection.In)
    fun onEnterFiatAmountOut(v: BigDecimal?) = setFiatAmount(v, SwapAmountDirection.Out)

    fun onEnterAmountPercentage(percentage: Int) {
        val tokenIn = quoteState.tokenIn ?: return
        val availableBalance = balanceState.balance ?: return

        val amount = availableBalance
            .times(BigDecimal(percentage / 100.0))
            .setScale(tokenIn.decimals, RoundingMode.DOWN)
            .stripTrailingZeros()

        setTokenAmount(amount, SwapAmountDirection.In)
    }

    fun onSelectTokenIn(token: Token) {
        quoteService.setTokenIn(token)
    }

    fun onSelectTokenOut(token: Token) {
        quoteService.setTokenOut(token)
    }

    fun onSwitchPairs() {
        fiatInputDirection = null
        fiatServiceIn.useTokenAmount()
        fiatServiceOut.useTokenAmount()
        quoteService.switchPairs()
    }

    fun toggleHideBalance() {
        HudHelper.vibrate(App.instance)
        val tokenIn = quoteState.tokenIn
        if (tokenIn != null) {
            balanceHiddenManager.toggleWalletBalanceHidden(tokenIn.tokenQuery.id)
        } else {
            balanceHiddenManager.toggleBalanceHidden()
        }
        emitState()
    }

    fun createMissingTokens(tokens: Set<Token>) {
        viewModelScope.launch {
            walletUseCase.awaitWallets(tokens)
            quoteService.invalidateAndReQuote()
        }
    }

    fun onUpdateSettings(settings: Map<String, Any?>) = quoteService.setSwapSettings(settings)
    fun reQuote() = quoteService.reQuote()
    fun onActionStarted() = quoteService.onActionStarted()
    fun onActionCompleted() = quoteService.onActionCompleted()

    fun getCurrentQuote() = quoteState.quote
    fun getSettings() = quoteService.swapSettings

    private fun setTokenAmount(value: BigDecimal?, direction: SwapAmountDirection) {
        fiatInputDirection = null
        deactivateOtherFiatInput(direction)
        fiatService(direction).setInputAmount(value)
        setQuoteAmount(value, direction)
    }

    private fun setFiatAmount(value: BigDecimal?, direction: SwapAmountDirection) {
        fiatInputDirection = direction
        deactivateOtherFiatInput(direction)
        fiatService(direction).setFiatAmount(value)
    }

    private fun deactivateOtherFiatInput(direction: SwapAmountDirection) {
        fiatService(
            when (direction) {
                SwapAmountDirection.In -> SwapAmountDirection.Out
                SwapAmountDirection.Out -> SwapAmountDirection.In
            }
        ).useTokenAmount()
    }

    private fun fiatService(direction: SwapAmountDirection) = when (direction) {
        SwapAmountDirection.In -> fiatServiceIn
        SwapAmountDirection.Out -> fiatServiceOut
    }

    private fun setQuoteAmount(value: BigDecimal?, direction: SwapAmountDirection) {
        when (direction) {
            SwapAmountDirection.In -> quoteService.setAmountIn(value)
            SwapAmountDirection.Out -> quoteService.setAmountOut(value)
        }
    }

    private val displayAmountOut: BigDecimal?
        get() = when (quoteState.direction) {
            SwapAmountDirection.In ->
                quoteState.multiSwapRoute?.selectedLeg2Quote?.amountOut ?: quoteState.quote?.amountOut
            SwapAmountDirection.Out -> quoteState.requestedAmountOut
        }

    private suspend fun obtainWarningMessage(): TranslatableString? {
        val quote = quoteState.quote ?: return null

        return quote.provider.getWarningMessage(quote.tokenIn, quote.tokenOut)
    }

    private fun fiatAmount(token: Token, amount: BigDecimal): BigDecimal? =
        marketKit.coinPrice(token.coin.uid, currency.code)?.value?.let(amount::multiply)

    override fun onCleared() {
        quoteService.clear()
        super.onCleared()
    }

    class Factory(private val tokenIn: Token?, private val tokenOut: Token?) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val swapQuoteService: SwapQuoteService by inject(SwapQuoteService::class.java)
            val marketKit: MarketKitWrapper by inject(MarketKitWrapper::class.java)
            val assetFiatRateService: AssetFiatRateService by inject(AssetFiatRateService::class.java)
            val tokenBalanceService = TokenBalanceService(App.adapterManager, marketKit)
            val priceImpactService = PriceImpactService()

            return SwapViewModel(
                quoteService = swapQuoteService,
                balanceService = tokenBalanceService,
                priceImpactService = priceImpactService,
                currencyManager = App.currencyManager,
                fiatServiceIn = FiatService(assetFiatRateService),
                fiatServiceOut = FiatService(assetFiatRateService),
                timerService = TimerService(),
                networkAvailabilityService = NetworkAvailabilityService(App.connectivityManager),
                marketKit = marketKit,
                tokenIn = tokenIn,
                tokenOut = tokenOut
            ) as T
        }
    }
}

internal fun isMultiSwapRouteReady(
    quoting: Boolean,
    timeout: Boolean,
    route: MultiSwapRoute?,
): Boolean {
    val settledRoute = settledMultiSwapRoute(quoting, route)
    return !timeout && settledRoute != null && settledRoute.leg1Quotes.contains(settledRoute.selectedLeg1Quote)
        && settledRoute.leg2Quotes.contains(settledRoute.selectedLeg2Quote)
}

internal fun shouldRefreshMultiSwapRoute(quoting: Boolean, timeout: Boolean, route: MultiSwapRoute?): Boolean =
    timeout && settledMultiSwapRoute(quoting, route) != null

private fun settledMultiSwapRoute(quoting: Boolean, route: MultiSwapRoute?): MultiSwapRoute? =
    route?.takeUnless { quoting }

private fun SwapProviderQuote.toLegUiState(
    currency: Currency,
    fiatAmountIn: BigDecimal?,
    fiatAmountOut: BigDecimal?,
) = PriceImpactService.fiatPriceImpact(fiatAmountOut, fiatAmountIn).let { priceImpact ->
    LegUiState(
        status = LegStatus.Pending,
        providerName = provider.title,
        providerIcon = provider.icon,
        tokenIn = tokenIn,
        tokenOut = tokenOut,
        amountIn = amountIn,
        amountOut = amountOut,
        fiatAmountIn = fiatAmountIn,
        fiatAmountOut = fiatAmountOut,
        currency = currency,
        badgeIn = tokenIn.badge,
        badgeOut = tokenOut.badge,
        coinIn = tokenIn.coin.code,
        coinOut = tokenOut.coin.code,
        amountInFormatted = amountIn.stripTrailingZeros().toPlainString(),
        amountOutFormatted = amountOut.stripTrailingZeros().toPlainString(),
        priceImpact = priceImpact,
        priceImpactLevel = PriceImpactService.priceImpactLevel(priceImpact),
        riskType = provider.riskType,
        estimationTime = estimationTime,
        coinIconUrlIn = coinImageUrl(tokenIn.coin.uid),
        coinIconUrlOut = coinImageUrl(tokenOut.coin.uid),
    )
}

data class SwapUiState(
    val amountIn: BigDecimal?,
    val displayAmountOut: BigDecimal?,
    val tokenIn: Token?,
    val tokenOut: Token?,
    val quoting: Boolean,
    val quotes: List<SwapProviderQuote>,
    val preferredProvider: IMultiSwapProvider?,
    val quote: SwapProviderQuote?,
    val error: Throwable?,
    val availableBalance: BigDecimal?,
    val displayBalance: BigDecimal?,
    val networkFee: BigDecimal?,
    val networkFeeFiatAmount: BigDecimal?,
    val feeToken: Token?,
    val feeCoinBalance: BigDecimal?,
    val insufficientFeeBalance: Boolean,
    val balanceHidden: Boolean,
    val warningMessage: TranslatableString?,
    val priceImpact: BigDecimal?,
    val priceImpactLevel: PriceImpactLevel?,
    val priceImpactCaution: HSCaution?,
    val fiatAmountIn: BigDecimal?,
    val fiatAmountOut: BigDecimal?,
    val fiatPriceImpact: BigDecimal?,
    val currency: Currency,
    val fiatAmountInInputEnabled: Boolean,
    val fiatAmountOutInputEnabled: Boolean,
    val fiatPriceImpactLevel: PriceImpactLevel?,
    val timeout: Boolean,
    val multiSwapRoute: MultiSwapRoute?,
    val direction: SwapAmountDirection,
    val requestedAmountOut: BigDecimal?,
    val amountInMax: BigDecimal?,
    val amountOutAccuracy: SwapAmountAccuracy,
    val quoteCautions: List<HSCaution>,
) {
    private val requestedAmount: BigDecimal?
        get() = when (direction) {
            SwapAmountDirection.In -> amountIn
            SwapAmountDirection.Out -> requestedAmountOut
        }

    val currentStep: SwapStep
        get() {
            val amount = requestedAmount
            return when {
                error != null -> SwapStep.Error(error)
                tokenIn == null -> SwapStep.InputRequired(InputType.TokenIn)
                tokenOut == null -> SwapStep.InputRequired(InputType.TokenOut)
                amount == null || amount <= BigDecimal.ZERO ->
                    SwapStep.InputRequired(InputType.Amount)
                // No fresh quote for the current input yet (fetching or pending debounce) - keep loading
                quoting || quote == null -> SwapStep.Quoting
                quote.actionRequired != null ->
                    SwapStep.ActionRequired(requireNotNull(quote.actionRequired))
                else -> SwapStep.Proceed
            }
        }
}

sealed class SwapStep {
    data class InputRequired(val inputType: InputType) : SwapStep()
    object Quoting : SwapStep()
    data class Error(val error: Throwable) : SwapStep()
    object Proceed : SwapStep()
    data class ActionRequired(val action: ISwapProviderAction) : SwapStep()
}

enum class InputType {
    TokenIn,
    TokenOut,
    Amount
}
