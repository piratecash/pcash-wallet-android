package cash.p.terminal.network.pirate.domain.repository

import cash.p.terminal.network.data.entity.ChartPeriod
import cash.p.terminal.network.pirate.domain.enity.CalculatorData
import cash.p.terminal.network.pirate.domain.enity.ChangeNowAssociatedCoin
import cash.p.terminal.network.pirate.domain.enity.CoinPriceChart
import cash.p.terminal.network.pirate.domain.enity.InvestmentData
import cash.p.terminal.network.pirate.domain.enity.InvestmentGraphData
import cash.p.terminal.network.pirate.domain.enity.MarketTicker
import cash.p.terminal.network.pirate.domain.enity.PiratePlaceCoin
import cash.p.terminal.network.pirate.domain.enity.PriceChangeCoinInfo
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.network.pirate.domain.enity.StakeData

interface PiratePlaceRepository {
    suspend fun getCoinInfo(coinGeckoUid: String): PiratePlaceCoin
    suspend fun getCoinsPriceChange(coinGeckoUidList: List<String>, currencyCode: String): List<PriceChangeCoinInfo>?
    suspend fun getInvestmentData(coinGeckoUid: String, address: String): InvestmentData
    suspend fun getChangeNowCoinAssociation(coinGeckoUid: String): List<ChangeNowAssociatedCoin>
    suspend fun getInvestmentChart(coinGeckoUid: String, address: String, period: ChartPeriod): InvestmentGraphData
    suspend fun getStakeData(coinGeckoUid: String, address: String): StakeData
    suspend fun getCalculatorData(coinGeckoUid: String, amount: Double): CalculatorData
    suspend fun getCoinPriceChart(coinGeckoUid: String, periodType: ChartPeriod): List<CoinPriceChart>
    suspend fun getMarketTickers(coinGeckoUid: String): List<MarketTicker>
    
    // Premium API methods
    suspend fun checkTrialPremiumStatus(address: String): TrialPremiumResult?
    suspend fun activateTrialPremium(address: String): TrialPremiumResult?
}