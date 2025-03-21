package cash.p.terminal.network.pirate.data.repository

import cash.p.terminal.network.data.entity.ChartPeriod
import cash.p.terminal.network.pirate.api.PlaceApi
import cash.p.terminal.network.pirate.data.database.CacheChangeNowCoinAssociationDao
import cash.p.terminal.network.pirate.data.database.entity.ChangeNowAssociationCoin
import cash.p.terminal.network.pirate.data.mapper.PiratePlaceMapper
import cash.p.terminal.network.pirate.domain.enity.CoinPriceChart
import cash.p.terminal.network.pirate.domain.enity.MarketTicker
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class PiratePlaceRepositoryImpl(
    private val placeApi: PlaceApi,
    private val piratePlaceMapper: PiratePlaceMapper,
    private val cacheChangeNowCoinAssociationDao: CacheChangeNowCoinAssociationDao
) : PiratePlaceRepository {
    private val mutex = Mutex()

    private companion object {
        const val CACHE_DURATION = 1 * 24 * 60 * 60 * 1000L // 1 day
    }

    override suspend fun getCoinInfo(coin: String) = withContext(Dispatchers.IO) {
        placeApi.getCoinInfo(coin = coin).let(piratePlaceMapper::mapCoinInfo)
    }

    override suspend fun getInvestmentData(coin: String, address: String) =
        withContext(Dispatchers.IO) {
            placeApi.getInvestmentData(coin = coin, address = address)
                .let(piratePlaceMapper::mapInvestmentData)
        }

    override suspend fun getChangeNowCoinAssociation(uid: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val coinList = cacheChangeNowCoinAssociationDao.getCoins(uid)
            if (coinList == null || System.currentTimeMillis() - coinList.timestamp > CACHE_DURATION) {
                placeApi.getChangeNowCoinAssociation(uid)
                    .let(piratePlaceMapper::mapChangeNowCoinAssociationList)
                    .apply {
                        cacheChangeNowCoinAssociationDao.insertCoins(
                            ChangeNowAssociationCoin(
                                uid = uid,
                                coinData = this,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
            } else {
                return@withContext coinList.coinData
            }
        }
    }

    override suspend fun getInvestmentChart(coin: String, address: String, period: ChartPeriod) =
        withContext(Dispatchers.IO) {
            placeApi.getInvestmentChart(coin = coin, address = address, period = period.value)
                .let(piratePlaceMapper::mapInvestmentGraphData)
        }

    override suspend fun getStakeData(coin: String, address: String) = withContext(Dispatchers.IO) {
        placeApi.getStakeData(coin = coin, address = address).let(piratePlaceMapper::mapStakeData)
    }

    override suspend fun getCalculatorData(coin: String, amount: Double) =
        withContext(Dispatchers.IO) {
            placeApi.getCalculatorData(coin, amount).let(piratePlaceMapper::mapCalculatorData)
        }

    override suspend fun getCoinPriceChart(
        coin: String,
        periodType: ChartPeriod
    ): List<CoinPriceChart> = withContext(Dispatchers.IO) {
        placeApi.getCoinPriceChart(coin, periodType).map(piratePlaceMapper::mapPricePoint)
    }

    override suspend fun getMarketTickers(coin: String): List<MarketTicker> = withContext(Dispatchers.IO) {
        placeApi.getMarketTickers(coin).map(piratePlaceMapper::mapMarketTicker)
    }
}