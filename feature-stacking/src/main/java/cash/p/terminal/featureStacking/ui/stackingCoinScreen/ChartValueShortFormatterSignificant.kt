package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import io.horizontalsystems.chartview.chart.ChartModule
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

class ChartValueShortFormatterSignificant : ChartModule.ChartNumberFormatter {
    private val numberFormatter: IAppNumberFormatter by inject(IAppNumberFormatter::class.java)
    override fun formatValue(currency: Currency, value: BigDecimal): String {
        return numberFormatter.formatNumberShort(value, 5)
    }
}
