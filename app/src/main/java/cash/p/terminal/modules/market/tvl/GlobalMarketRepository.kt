package cash.p.terminal.modules.market.tvl

import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.modules.metricchart.MetricsType
import io.horizontalsystems.chartview.models.ChartPoint
import cash.p.terminal.wallet.models.DefiMarketInfo
import io.horizontalsystems.core.models.HsTimePeriod
import io.reactivex.Single
import java.math.BigDecimal

class GlobalMarketRepository(
    private val marketKit: MarketKitWrapper
) {

    private var cache: List<DefiMarketInfo> = listOf()

    fun getGlobalMarketPoints(
        currencyCode: String,
        chartInterval: HsTimePeriod,
        metricsType: MetricsType
    ): Single<List<ChartPoint>> {
        return marketKit.globalMarketPointsSingle(currencyCode, chartInterval)
            .map { list ->
                list.map { point ->
                    val value = when (metricsType) {
                        MetricsType.TotalMarketCap -> point.marketCap
                        MetricsType.Volume24h -> point.volume24h
                        MetricsType.Etf -> point.defiMarketCap
                        MetricsType.TvlInDefi -> point.tvl
                    }

                    val dominance = if (metricsType == MetricsType.TotalMarketCap) point.btcDominance.toFloat() else null
                    ChartPoint(value = value.toFloat(), timestamp = point.timestamp, dominance = dominance)
                }
            }
    }

    fun getTvlGlobalMarketPoints(
        chain: String,
        currencyCode: String,
        chartInterval: HsTimePeriod,
    ): Single<List<ChartPoint>> {
        return marketKit.marketInfoGlobalTvlSingle(chain, currencyCode, chartInterval)
            .map { list ->
                list.map { point ->
                      ChartPoint(point.value.toFloat(), point.timestamp)
                }
            }
    }

    fun getMarketTvlItems(
        currency: Currency,
        chain: TvlModule.Chain,
        chartInterval: HsTimePeriod?,
        sortDescending: Boolean,
        forceRefresh: Boolean
    ): Single<List<TvlModule.MarketTvlItem>> =
        Single.create { emitter ->
            try {
                val defiMarketInfos = defiMarketInfos(currency.code, forceRefresh)
                val marketTvlItems = getMarketTvlItems(defiMarketInfos, currency, chain, chartInterval, sortDescending)
                emitter.onSuccess(marketTvlItems)
            } catch (error: Throwable) {
                emitter.onError(error)
            }
        }

    private fun defiMarketInfos(currencyCode: String, forceRefresh: Boolean): List<DefiMarketInfo> =
        if (forceRefresh || cache.isEmpty()) {
            val defiMarketInfo = marketKit.defiMarketInfosSingle(currencyCode).blockingGet()

            cache = defiMarketInfo

            defiMarketInfo
        } else {
            cache
        }

    private fun getMarketTvlItems(
        defiMarketInfoList: List<DefiMarketInfo>,
        currency: Currency,
        chain: TvlModule.Chain,
        chartInterval: HsTimePeriod?,
        sortDescending: Boolean
    ): List<TvlModule.MarketTvlItem> {
        val tvlItems = defiMarketInfoList.map { defiMarketInfo ->
            val diffPercent: BigDecimal? = when (chartInterval) {
                HsTimePeriod.Day1 -> defiMarketInfo.tvlChange1D
                HsTimePeriod.Week1 -> defiMarketInfo.tvlChange1W
                HsTimePeriod.Month1 -> defiMarketInfo.tvlChange1M
                HsTimePeriod.Year1 -> defiMarketInfo.tvlChange1Y
                else -> null
            }
            val diff: CurrencyValue? = diffPercent?.let {
                CurrencyValue(currency, defiMarketInfo.tvl * it.divide(BigDecimal(100)))
            }

            val tvl: BigDecimal = if (chain == TvlModule.Chain.All) {
                defiMarketInfo.tvl
            } else {
                defiMarketInfo.chainTvls[chain.name] ?: BigDecimal.ZERO
            }

            TvlModule.MarketTvlItem(
                defiMarketInfo.fullCoin,
                defiMarketInfo.name,
                defiMarketInfo.chains,
                defiMarketInfo.logoUrl,
                CurrencyValue(currency, tvl),
                diff,
                diffPercent,
                defiMarketInfo.tvlRank.toString()
            )
        }

        val chainTvlItems = if (chain == TvlModule.Chain.All) {
            tvlItems
        } else {
            tvlItems.filter { it.chains.contains(chain.name) }
        }

        return if (sortDescending) {
            chainTvlItems.sortedByDescending { it.tvl.value }
        } else {
            chainTvlItems.sortedBy { it.tvl.value }
        }
    }

}
