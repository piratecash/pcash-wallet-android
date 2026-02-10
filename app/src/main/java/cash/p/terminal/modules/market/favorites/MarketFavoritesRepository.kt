package cash.p.terminal.modules.market.favorites

import cash.p.terminal.core.managers.MarketFavoritesManager
import cash.p.terminal.modules.market.MarketItem
import cash.p.terminal.modules.market.filters.TimePeriod
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.models.CoinPrice
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.rx2.await
import java.math.BigDecimal

class MarketFavoritesRepository(
    private val marketKit: MarketKitWrapper,
    private val manager: MarketFavoritesManager
) {
    val dataUpdatedObservable by manager::dataUpdatedAsync

    private suspend fun getFavorites(
        currency: Currency,
        period: TimePeriod
    ): List<MarketItem> {
        val favoriteCoins = manager.getAll()
        if (favoriteCoins.isEmpty()) return listOf()

        val favoriteCoinUids = favoriteCoins.map { it.coinUid }
        val favoriteUidSet = favoriteCoinUids.toSet()
        val marketInfoList = marketKit
            .marketInfosSingle(favoriteCoinUids, currency.code).await()
            .filter { it.fullCoin.coin.uid in favoriteUidSet }

        val apiItems = marketInfoList.map { marketInfo ->
            MarketItem.createFromCoinMarket(
                marketInfo = marketInfo,
                currency = currency,
                period = period
            )
        }

        val returnedUids = marketInfoList.map { it.fullCoin.coin.uid }.toSet()
        val missingUids = favoriteCoinUids.filterNot { it in returnedUids }
        if (missingUids.isEmpty()) return apiItems

        return apiItems + buildFallbackItems(missingUids, currency, period)
    }

    private fun buildFallbackItems(
        coinUids: List<String>,
        currency: Currency,
        period: TimePeriod
    ): List<MarketItem> {
        val fullCoins = marketKit.fullCoins(coinUids)
        val priceMap = marketKit.coinPriceMap(coinUids, currency.code)
        val zero = CurrencyValue(currency, BigDecimal.ZERO)

        return fullCoins.map { fullCoin ->
            val coinPrice = priceMap[fullCoin.coin.uid]
            MarketItem(
                fullCoin = fullCoin,
                volume = zero,
                rate = CurrencyValue(currency, coinPrice?.value ?: BigDecimal.ZERO),
                diff = coinPrice?.diff(period),
                marketCap = zero,
                rank = fullCoin.coin.marketCapRank
            )
        }
    }

    private fun CoinPrice.diff(period: TimePeriod): BigDecimal? = when (period) {
        // CoinPrice lacks UTC-midnight field, diff24h is the closest approximation
        TimePeriod.TimePeriod_1D -> diff24h
        TimePeriod.TimePeriod_1W -> diff7d
        TimePeriod.TimePeriod_1M -> diff30d
        TimePeriod.TimePeriod_1Y -> diff1y
        else -> null
    }

    fun getSignals(uids: List<String>) = marketKit.getCoinSignalsSingle(uids)

    suspend fun get(period: TimePeriod, currency: Currency): List<MarketItem> {
        return getFavorites(currency, period)
    }

    fun removeFavorite(uid: String) {
        manager.remove(uid)
    }
}
