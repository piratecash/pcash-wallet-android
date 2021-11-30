package io.horizontalsystems.bankwallet.modules.metricchart

import io.horizontalsystems.bankwallet.R
import io.horizontalsystems.bankwallet.core.UnsupportedException
import io.horizontalsystems.marketkit.MarketKit
import io.horizontalsystems.marketkit.models.ChartType
import io.horizontalsystems.marketkit.models.TimePeriod
import io.reactivex.Single

class CoinTvlFetcher(
    private val marketKit: MarketKit,
    private val coinUid: String,
) : IMetricChartFetcher {

    override val title: Int = R.string.CoinPage_Tvl

    override val chartTypes = listOf(
        ChartType.TODAY,
        ChartType.WEEKLY,
        ChartType.MONTHLY_BY_DAY,
    )

    override fun fetchSingle(currencyCode: String, chartType: ChartType) = try {
        val timePeriod = getTimePeriod(chartType)
        marketKit.marketInfoTvlSingle(coinUid, currencyCode, timePeriod)
            .map { info ->
                info.map { point ->
                    MetricChartModule.Item(point.value, null, point.timestamp)
                }
            }
    } catch (e: Exception) {
        Single.error(e)
    }

    private fun getTimePeriod(chartType: ChartType) = when (chartType) {
        ChartType.TODAY -> TimePeriod.Hour24
        ChartType.WEEKLY -> TimePeriod.Day7
        ChartType.MONTHLY_BY_DAY -> TimePeriod.Day30
        else -> throw UnsupportedException("Unsupported chartType $chartType")
    }
}