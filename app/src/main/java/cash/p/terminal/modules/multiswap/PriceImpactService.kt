package cash.p.terminal.modules.multiswap

import cash.p.terminal.R
import cash.p.terminal.core.HSCaution
import cash.p.terminal.strings.helpers.TranslatableString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.RoundingMode

class PriceImpactService {
    private val warningPriceImpact = BigDecimal(5)
    private val forbiddenPriceImpact = BigDecimal(20)
    private var fiatAmountIn: BigDecimal? = null
    private var fiatAmountOut: BigDecimal? = null
    private var fiatPriceImpact: BigDecimal? = null
    private var fiatPriceImpactLevel: PriceImpactLevel? = null

    private var priceImpact: BigDecimal? = null
    private var priceImpactLevel: PriceImpactLevel? = null
    private var priceImpactCaution: HSCaution? = null
    private var error: Throwable? = null

    private val _stateFlow = MutableStateFlow(
        State(
            priceImpact = priceImpact,
            priceImpactLevel = priceImpactLevel,
            priceImpactCaution = priceImpactCaution,
            fiatPriceImpact = fiatPriceImpact,
            fiatPriceImpactLevel = fiatPriceImpactLevel,
            error = error
        )
    )
    val stateFlow = _stateFlow.asStateFlow()

    fun setPriceImpact(priceImpact: BigDecimal?, providerTitle: String?) {
        priceImpactLevel = priceImpactLevel(priceImpact)
        this.priceImpact = priceImpact

        val priceImpactAbs = priceImpact?.abs()
        priceImpactCaution = if (priceImpactAbs != null && priceImpact.signum() < 0) {
            when {
                priceImpactAbs > forbiddenPriceImpact -> {
                    HSCaution(
                        s = TranslatableString.ResString(R.string.Swap_PriceImpact),
                        type = HSCaution.Type.Error,
                        description = TranslatableString.ResString(
                            R.string.Swap_PriceImpactTooHigh,
                            providerTitle ?: ""
                        )
                    )
                }

                priceImpactAbs > warningPriceImpact -> {
                    HSCaution(
                        s = TranslatableString.ResString(R.string.Swap_PriceImpact),
                        type = HSCaution.Type.Warning,
                        description = TranslatableString.ResString(R.string.Swap_PriceImpactWarning)
                    )
                }

                else -> {
                    null
                }
            }
        } else {
            null
        }

        error = if (priceImpactAbs != null && priceImpactAbs > forbiddenPriceImpact) {
            PriceImpactTooHigh(providerTitle)
        } else {
            null
        }

        emitState()
    }

    private fun emitState() {
        _stateFlow.update {
            State(
                priceImpact,
                priceImpactLevel,
                priceImpactCaution,
                fiatPriceImpact,
                fiatPriceImpactLevel,
                error
            )
        }
    }

    private fun refreshFiatPriceImpact() {
        val fiatAmountIn = fiatAmountIn
        val fiatAmountOut = fiatAmountOut

        fiatPriceImpact = fiatPriceImpact(fiatAmountOut, fiatAmountIn)
        fiatPriceImpactLevel = priceImpactLevel(fiatPriceImpact)
    }

    fun setFiatAmountIn(fiatAmountIn: BigDecimal?) {
        this.fiatAmountIn = fiatAmountIn

        refreshFiatPriceImpact()

        emitState()
    }

    fun setFiatAmountOut(fiatAmountOut: BigDecimal?) {
        this.fiatAmountOut = fiatAmountOut

        refreshFiatPriceImpact()

        emitState()
    }

    data class State(
        val priceImpact: BigDecimal?,
        val priceImpactLevel: PriceImpactLevel?,
        val priceImpactCaution: HSCaution?,
        val fiatPriceImpact: BigDecimal?,
        val fiatPriceImpactLevel: PriceImpactLevel?,
        val error: Throwable?
    )

    companion object {
        fun fiatPriceImpact(amountOut: BigDecimal?, amountIn: BigDecimal?): BigDecimal? {
            if (amountOut == null || amountIn == null || amountIn.compareTo(BigDecimal.ZERO) == 0) return null

            return amountOut.subtract(amountIn)
                .multiply(BigDecimal("100"))
                .divide(amountIn, 2, RoundingMode.DOWN)
                .stripTrailingZeros()
        }

        fun priceImpactLevel(priceImpact: BigDecimal?): PriceImpactLevel = when {
            priceImpact == null -> PriceImpactLevel.Normal
            priceImpact < BigDecimal.ZERO -> PriceImpactLevel.Warning
            priceImpact > BigDecimal.ZERO -> PriceImpactLevel.Good
            else -> PriceImpactLevel.Normal
        }
    }
}

data class PriceImpactTooHigh(val providerTitle: String?) : Exception()
