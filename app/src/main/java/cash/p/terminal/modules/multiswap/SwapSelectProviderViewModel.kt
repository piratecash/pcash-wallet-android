package cash.p.terminal.modules.multiswap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import java.math.BigDecimal
import java.math.RoundingMode

class SwapSelectProviderViewModel(private val quotes: List<SwapProviderQuote>) :
    ViewModelUiState<SwapSelectProviderUiState>() {
    private val currencyManager = App.currencyManager
    private val marketKit = App.marketKit

    private val currency = currencyManager.baseCurrency
    private var token: Token = quotes.first().tokenOut
    private var rate: BigDecimal? = marketKit.coinPrice(token.coin.uid, currency.code)?.value

    // To show straight or reversed rate in provider list item
    private var isRegularRateDirection = true

    private var quoteViewItems = getViewItems(quotes)

    init {
        viewModelScope.launch {
            marketKit.coinPriceObservable("swap-providers", token.coin.uid, currency.code)
                .asFlow()
                .collect {
                    rate = it.value
                    quoteViewItems = getViewItems(quotes)
                    emitState()
                }
        }
    }

    private fun getViewItems(quotes: List<SwapProviderQuote>): List<QuoteViewItem> {
        val bestProviderAmountOut = quotes.firstOrNull()?.amountOut ?: return emptyList()
        return quotes.mapIndexed { index, quote ->
            val fiatAmount = getFiatValue(quote.amountOut)?.getFormattedFull()
            val tokenAmount = App.numberFormatter.formatCoinFull(
                value = quote.amountOut,
                code = quote.tokenOut.coin.code,
                coinDecimals = quote.tokenOut.decimals
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
                diffWithFirst = if (index != 0) {
                    tryOrNull {
                        ((quote.amountOut - bestProviderAmountOut) / bestProviderAmountOut * BigDecimal(
                            100
                        )).setScale(2, RoundingMode.DOWN)
                            .stripTrailingZeros()
                    }
                } else {
                    null
                },
                rateFrom = rateFrom,
                rateTo = rateTo
            )
        }
    }

    override fun createState() = SwapSelectProviderUiState(
        quoteViewItems = quoteViewItems
    )

    private fun getFiatValue(amount: BigDecimal?): CurrencyValue? {
        return amount?.let {
            rate?.multiply(it)
        }?.let { fiatBalance ->
            CurrencyValue(currency, fiatBalance)
        }
    }

    fun swapRates() {
        isRegularRateDirection = !isRegularRateDirection
        quoteViewItems = getViewItems(quotes)
        emitState()
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

    class Factory(private val quotes: List<SwapProviderQuote>) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SwapSelectProviderViewModel(quotes) as T
        }
    }
}

data class SwapSelectProviderUiState(val quoteViewItems: List<QuoteViewItem>)

data class QuoteViewItem(
    val quote: SwapProviderQuote,
    val fiatAmount: String?,
    val tokenAmount: String,
    val diffWithFirst: BigDecimal?,
    val rateFrom: String,
    val rateTo: String
)
