package cash.p.terminal.modules.market.overview

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.modules.market.TopMarket
import cash.p.terminal.modules.market.topcoins.MarketTopMoversRepository
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import cash.p.terminal.wallet.models.MarketOverview
import cash.p.terminal.wallet.models.TopMovers
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await

class MarketOverviewService(
    private val marketTopMoversRepository: MarketTopMoversRepository,
    private val marketKit: MarketKitWrapper,
    private val backgroundManager: BackgroundManager,
    private val currencyManager: CurrencyManager
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var topMoversJob: Job? = null
    private var marketOverviewJob: Job? = null

    val topMarketOptions: List<TopMarket> = TopMarket.values().toList()
    val timeDurationOptions: List<TimeDuration> = listOf(
        TimeDuration.SevenDay,
        TimeDuration.ThirtyDay,
    )
    val topMoversObservable: BehaviorSubject<Result<TopMovers>> = BehaviorSubject.create()
    val marketOverviewObservable: BehaviorSubject<Result<MarketOverview>> = BehaviorSubject.create()

    private fun updateTopMovers() {
        topMoversJob?.cancel()
        topMoversJob = coroutineScope.launch {
            try {
                val topMovers = marketTopMoversRepository.getTopMovers(currencyManager.baseCurrency).await()
                topMoversObservable.onNext(Result.success(topMovers))
            } catch (e: Throwable) {
                topMoversObservable.onNext(Result.failure(e))
            }
        }
    }

    private fun updateMarketOverview() {
        marketOverviewJob?.cancel()
        marketOverviewJob = coroutineScope.launch {
            try {
                val marketOverview =
                    marketKit.marketOverviewSingle(currencyManager.baseCurrency.code).await()
                marketOverviewObservable.onNext(Result.success(marketOverview))
            } catch (e: Throwable) {
                marketOverviewObservable.onNext(Result.failure(e))
            }
        }
    }

    private fun forceRefresh() {
        updateTopMovers()
        updateMarketOverview()
    }

    fun start() {
        coroutineScope.launch {
            backgroundManager.stateFlow.collect { state ->
                if (state == BackgroundManagerState.EnterForeground) {
                    forceRefresh()
                }
            }
        }

        coroutineScope.launch {
            currencyManager.baseCurrencyUpdatedSignal.asFlow().collect {
                forceRefresh()
            }
        }

        forceRefresh()
    }

    fun stop() {
        coroutineScope.cancel()
    }

    fun refresh() {
        forceRefresh()
    }

}
