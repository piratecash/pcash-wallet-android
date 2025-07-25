package cash.p.terminal.modules.coin.coinmarkets

import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui.compose.Select
import io.horizontalsystems.core.CurrencyManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.models.MarketTicker
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CoinMarketsService(
    val fullCoin: FullCoin,
    private val currencyManager: CurrencyManager,
    private val marketKit: MarketKitWrapper,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var marketTickers = listOf<MarketTicker>()

    private var verifiedType: VerifiedType = VerifiedType.All

    val verifiedMenu = Select(verifiedType, VerifiedType.entries)
    val stateObservable: BehaviorSubject<DataState<List<MarketTickerItem>>> = BehaviorSubject.create()

    val currency get() = currencyManager.baseCurrency


    fun start() {
        coroutineScope.launch {
            syncMarketTickers()
        }
    }

    private suspend fun syncMarketTickers() {
        try {
            val tickers = marketKit.marketTickersSingle(fullCoin.coin.uid, currency.code)
            marketTickers = tickers.sortedByDescending { it.volume }
            emitItems()
        } catch (e: Throwable) {
            stateObservable.onNext(DataState.Error(e))
        }
    }

    fun stop() {
        coroutineScope.cancel()
    }

    fun setVerifiedType(verifiedType: VerifiedType) {
        this.verifiedType = verifiedType

        emitItems()
    }

    @Synchronized
    private fun emitItems() {
        val filtered = when (verifiedType) {
            VerifiedType.Verified -> marketTickers.filter { it.verified }
            VerifiedType.All -> marketTickers
        }

        stateObservable.onNext(DataState.Success(filtered.map { createItem(it) }))
    }

    private fun createItem(marketTicker: MarketTicker): MarketTickerItem = MarketTickerItem(
        market = marketTicker.marketName,
        marketImageUrl = marketTicker.marketImageUrl,
        baseCoinCode = marketTicker.base,
        targetCoinCode = marketTicker.target,
        volumeFiat = marketTicker.fiatVolume,
        volumeToken = marketTicker.volume,
        tradeUrl = marketTicker.tradeUrl,
        verified = marketTicker.verified
    )
}
