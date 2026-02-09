package io.horizontalsystems.chartview.chart

import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

class ChartCurrencyValueFormatterSignificant : ChartModule.ChartNumberFormatter {
    private val numberFormatter: IAppNumberFormatter by inject(IAppNumberFormatter::class.java)
    override fun formatValue(currency: Currency, value: BigDecimal): String {
        if (value >= BigDecimal.ONE) {
            return numberFormatter.format(value, 0, 2, currency.symbol)
        }
        return numberFormatter.formatFiatFull(value, currency.symbol)
    }
}
