package cash.p.terminal.modules.xtransaction.helpers

import androidx.compose.runtime.Composable
import cash.p.terminal.core.App
import java.math.BigDecimal

@Composable
fun coinAmountString(value: BigDecimal?, coinCode: String, coinDecimals: Int?, sign: String): String {
    if (value == null) return "---"

    return sign + App.numberFormatter.formatCoinFull(value, coinCode, coinDecimals  ?: 8)
}

@Composable
fun fiatAmountString(value: BigDecimal?, fiatSymbol: String): String {
    if (value == null) return "---"

    return App.numberFormatter.formatFiatFull(value, fiatSymbol)
}