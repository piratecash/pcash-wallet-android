package cash.p.terminal.modules.multiswap

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class SwapSelectProviderViewModel(
    private var quotes: List<SwapProviderQuote>,
    private val direction: SwapAmountDirection,
    private val assetFiatRateService: AssetFiatRateService = getKoinInstance(),
    quoteUpdates: Flow<List<SwapProviderQuote>> = emptyFlow(),
) : ViewModelUiState<SwapSelectProviderUiState>() {
    private val currencyManager = App.currencyManager

    private val currency = currencyManager.baseCurrency
    private var token: Token = displayToken(quotes.first())
    private var rate: BigDecimal? = null

    // To show straight or reversed rate in provider list item
    private var isRegularRateDirection = true

    private var sortType = ProviderSortType.BestPrice
    private var quoteViewItems = getViewItems(quotes.sorted())

    init {
        viewModelScope.launch {
            assetFiatRateService.rateFlow("swap-providers", token, currency)
                .collect {
                    rate = it
                    rebuildViewItems()
                }
        }
        viewModelScope.launch {
            quoteUpdates.collect {
                if (quotes == it) return@collect
                quotes = it
                rebuildViewItems()
            }
        }
    }

    private fun rebuildViewItems() {
        quoteViewItems = getViewItems(quotes.sorted())
        emitState()
    }

    private fun List<SwapProviderQuote>.sorted(): List<SwapProviderQuote> = when (sortType) {
        ProviderSortType.BestPrice -> sortedWith(
            priceComparator().thenBy { it.estimationTime ?: Long.MAX_VALUE }
        )

        ProviderSortType.BestTime -> sortedWith(
            compareBy<SwapProviderQuote> { it.estimationTime ?: Long.MAX_VALUE }
                .then(priceComparator())
        )
    }

    private fun getViewItems(quotes: List<SwapProviderQuote>): List<QuoteViewItem> {
        // Diff is always measured against the best rate, regardless of the active sort order.
        val bestAmount = quotes.minOfOrNull(::priceRank) ?: return emptyList()
        return quotes.map { quote ->
            val amount = displayAmount(quote)
            val token = displayToken(quote)
            val fiatAmount = getFiatValue(amount)?.getFormattedFull()
            val tokenAmount = App.numberFormatter.formatCoinFull(
                value = amount,
                code = token.coin.code,
                coinDecimals = token.decimals
            )
            val (rateFrom, rateTo) = getRateString(
                tokenIn = quote.tokenIn,
                tokenOut = quote.tokenOut,
                amountIn = quote.amountIn,
                amountOut = quote.amountOut
            )
            QuoteViewItem(
                quote = quote,
                fiatAmount = fiatAmount,
                tokenAmount = tokenAmount,
                diffWithFirst = priceDifference(amount, bestAmount),
                rateFrom = rateFrom,
                rateTo = rateTo,
                estimationTime = quote.estimationTime
            )
        }
    }

    private fun priceComparator(): Comparator<SwapProviderQuote> = when (direction) {
        SwapAmountDirection.In -> compareByDescending(SwapProviderQuote::amountOut)
        SwapAmountDirection.Out -> compareBy(SwapProviderQuote::amountIn)
    }

    private fun priceRank(quote: SwapProviderQuote): BigDecimal = when (direction) {
        SwapAmountDirection.In -> quote.amountOut.negate()
        SwapAmountDirection.Out -> quote.amountIn
    }

    private fun displayAmount(quote: SwapProviderQuote): BigDecimal = when (direction) {
        SwapAmountDirection.In -> quote.amountOut
        SwapAmountDirection.Out -> quote.amountIn
    }

    private fun displayToken(quote: SwapProviderQuote): Token = when (direction) {
        SwapAmountDirection.In -> quote.tokenOut
        SwapAmountDirection.Out -> quote.tokenIn
    }

    private fun priceDifference(amount: BigDecimal, bestRank: BigDecimal): BigDecimal? {
        val bestAmount = when (direction) {
            SwapAmountDirection.In -> bestRank.negate()
            SwapAmountDirection.Out -> bestRank
        }
        val isWorse = when (direction) {
            SwapAmountDirection.In -> amount < bestAmount
            SwapAmountDirection.Out -> amount > bestAmount
        }
        if (!isWorse || bestAmount.compareTo(BigDecimal.ZERO) == 0) return null

        return tryOrNull {
            val difference = when (direction) {
                SwapAmountDirection.In -> amount - bestAmount
                SwapAmountDirection.Out -> bestAmount - amount
            }
            difference.multiply(BigDecimal(100))
                .divide(bestAmount, 2, RoundingMode.DOWN)
                .stripTrailingZeros()
        }
    }

    override fun createState() = SwapSelectProviderUiState(
        quoteViewItems = quoteViewItems,
        sortType = sortType
    )

    fun setSortType(sortType: ProviderSortType) {
        this.sortType = sortType
        rebuildViewItems()
    }

    private fun getFiatValue(amount: BigDecimal?): CurrencyValue? {
        return amount?.let {
            rate?.multiply(it)
        }?.let { fiatBalance ->
            CurrencyValue(currency, fiatBalance)
        }
    }

    fun swapRates() {
        isRegularRateDirection = !isRegularRateDirection
        rebuildViewItems()
    }

    private fun getRateString(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        amountOut: BigDecimal
    ): Pair<String, String> {
        return try {
            if (isRegularRateDirection) {
                val price = amountOut.divide(amountIn, tokenOut.decimals, RoundingMode.HALF_EVEN)
                    .stripTrailingZeros()
                CoinValue(tokenIn, BigDecimal.ONE).getFormattedFull() to CoinValue(
                    tokenOut,
                    price
                ).getFormattedFull()
            } else {
                val price = amountIn.divide(amountOut, tokenIn.decimals, RoundingMode.HALF_EVEN)
                    .stripTrailingZeros()
                CoinValue(tokenOut, BigDecimal.ONE).getFormattedFull() to CoinValue(
                    tokenIn,
                    price
                ).getFormattedFull()
            }
        } catch (e: ArithmeticException) {
            "" to ""
        }
    }

    class Factory(
        private val quotes: List<SwapProviderQuote>,
        private val direction: SwapAmountDirection,
        private val quoteUpdates: Flow<List<SwapProviderQuote>> = emptyFlow(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SwapSelectProviderViewModel(
                quotes = quotes,
                direction = direction,
                quoteUpdates = quoteUpdates,
            ) as T
        }
    }
}

data class SwapSelectProviderUiState(
    val quoteViewItems: List<QuoteViewItem>,
    val sortType: ProviderSortType
)

data class QuoteViewItem(
    val quote: SwapProviderQuote,
    val fiatAmount: String?,
    val tokenAmount: String,
    val diffWithFirst: BigDecimal?,
    val rateFrom: String,
    val rateTo: String,
    val estimationTime: Long?
)

enum class ProviderSortType(@StringRes val titleRes: Int) {
    BestPrice(R.string.swap_sort_best_rate),
    BestTime(R.string.swap_sort_best_time),
}
