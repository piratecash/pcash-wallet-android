package cash.p.terminal.core.stats

import com.google.gson.Gson
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.storage.StatsDao
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.entities.StatRecord
import cash.p.terminal.wallet.BalanceSortType
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.modules.coin.CoinModule
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule
import cash.p.terminal.modules.main.MainModule
import cash.p.terminal.modules.market.MarketField
import cash.p.terminal.modules.market.MarketModule
import cash.p.terminal.modules.market.SortingField
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.modules.market.TopMarket
import cash.p.terminal.modules.market.etf.EtfModule
import cash.p.terminal.modules.market.favorites.WatchlistSorting
import cash.p.terminal.modules.market.filters.TimePeriod
import cash.p.terminal.modules.market.search.MarketSearchSection
import cash.p.terminal.modules.market.tvl.TvlModule
import cash.p.terminal.modules.metricchart.MetricsType
import cash.p.terminal.modules.metricchart.ProChartModule
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.modules.transactionInfo.options.SpeedUpCancelType
import cash.p.terminal.modules.transactions.FilterTransactionType
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.core.models.HsTimePeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.Executors

fun stat(page: StatPage, section: StatSection? = null, event: StatEvent) {
    App.statsManager.logStat(page, section, event)
}

class StatsManager(
    private val statsDao: StatsDao,
    private val localStorage: ILocalStorage,
    private val marketKit: MarketKitWrapper,
    private val appConfigProvider: AppConfigProvider,
    private val backgroundManager: BackgroundManager,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            backgroundManager.stateFlow.collect { state ->
                if (state == BackgroundManagerState.EnterForeground) {
                    sendStats()
                }
            }
        }
    }

    var uiStatsEnabled = getInitialUiStatsEnabled()
        private set

    private val _uiStatsEnabledFlow = MutableStateFlow(uiStatsEnabled)
    val uiStatsEnabledFlow = _uiStatsEnabledFlow.asStateFlow()

    private val gson by lazy { Gson() }
    private val executor = Executors.newCachedThreadPool()
    private var statsEnabled = false   // P.CASH wallet will not sent anonymous stats
    private val syncInterval = 60 * 60 // 1H in seconds
    private val sqliteMaxVariableNumber = 999

    private fun getInitialUiStatsEnabled(): Boolean {
        return false
        /*
        val uiStatsEnabled = localStorage.uiStatsEnabled
        if (uiStatsEnabled != null) return uiStatsEnabled

        val signatures = listOf(
            "b797339fb356afce5160fe49274ee17a1c1816db", // appcenter
            "5afb2517b06caac7f108ba9d96ad826f1c4ba30c", // hs
        )

        val applicationSignatures = App.instance.getApplicationSignatures()
        return applicationSignatures.any {
            signatures.contains(it.toHexString())
        }
         */
    }

    fun logStat(eventPage: StatPage, eventSection: StatSection? = null, event: StatEvent) {
        if (!uiStatsEnabled) return

        executor.submit {
            if (!statsEnabled) return@submit
            try {
                val eventMap = buildMap {
                    put("event_page", eventPage.key)
                    put("event", event.name)
                    eventSection?.let { put("event_section", it.key) }
                    event.params?.let { params ->
                        putAll(params.map { (param, value) -> param.key to value })
                    }
                    put("time", Instant.now().epochSecond)
                }

                val json = gson.toJson(eventMap)
//                Log.e("e", json)
                statsDao.insert(StatRecord(json))
            } catch (error: Throwable) {
//                Log.e("e", "logStat error", error)
            }
        }
    }

    fun sendStats() {
        if (!uiStatsEnabled) return

        executor.submit {
            try {
                val statLastSyncTime = localStorage.statsLastSyncTime
                val currentTime = Instant.now().epochSecond

                if (currentTime - statLastSyncTime < syncInterval) return@submit
                if (!statsEnabled) return@submit

                val stats = statsDao.getAll()
                if (stats.isNotEmpty()) {
                    val statsArray = "[${stats.joinToString { it.json }}]"
//                    Log.e("e", "send $statsArray")
                    marketKit.sendStats(statsArray, appConfigProvider.appVersion, appConfigProvider.appId).blockingGet()

                    stats.chunked(sqliteMaxVariableNumber).forEach { chunk ->
                        statsDao.delete(chunk.map { it.id })
                    }
                    localStorage.statsLastSyncTime = currentTime
                }

            } catch (error: Throwable) {
//                Log.e("e", "sendStats error", error)
            }
        }
    }

    fun toggleUiStats(enabled: Boolean) {
        localStorage.uiStatsEnabled = enabled
        uiStatsEnabled = enabled
        _uiStatsEnabledFlow.update { enabled }
    }

}

val BalanceSortType.statSortType
    get() = when (this) {
        BalanceSortType.Name -> StatSortType.Name
        BalanceSortType.PercentGrowth -> StatSortType.PriceChange
        BalanceSortType.Value -> StatSortType.Balance
    }

val ProChartModule.ChartType.statPage
    get() = when (this) {
        ProChartModule.ChartType.CexVolume -> StatPage.CoinAnalyticsCexVolume
        ProChartModule.ChartType.DexVolume -> StatPage.CoinAnalyticsDexVolume
        ProChartModule.ChartType.DexLiquidity -> StatPage.CoinAnalyticsDexLiquidity
        ProChartModule.ChartType.TxCount -> StatPage.CoinAnalyticsTxCount
        ProChartModule.ChartType.AddressesCount -> StatPage.CoinAnalyticsActiveAddresses
        ProChartModule.ChartType.Tvl -> StatPage.CoinAnalyticsTvl
    }

val HsTimePeriod?.statPeriod: StatPeriod
    get() = when (this) {
        HsTimePeriod.Hour1 -> StatPeriod.Hour1
        HsTimePeriod.Day1 -> StatPeriod.Day1
        HsTimePeriod.Week1 -> StatPeriod.Week1
        HsTimePeriod.Month1 -> StatPeriod.Month1
        HsTimePeriod.Year1 -> StatPeriod.Year1
        null -> StatPeriod.All
    }

val MarketField.statField: StatField
    get() = when (this) {
        MarketField.PriceDiff -> StatField.Price
        MarketField.MarketCap -> StatField.MarketCap
        MarketField.Volume -> StatField.Volume
    }

val SortingField.statSortType: StatSortType
    get() = when (this) {
        SortingField.HighestCap -> StatSortType.HighestCap
        SortingField.LowestCap -> StatSortType.LowestCap
        SortingField.HighestVolume -> StatSortType.HighestVolume
        SortingField.LowestVolume -> StatSortType.LowestVolume
        SortingField.TopGainers -> StatSortType.TopGainers
        SortingField.TopLosers -> StatSortType.TopLosers
    }

val WatchlistSorting.statSortType: StatSortType
    get() = when (this) {
        WatchlistSorting.Manual -> StatSortType.Manual
        WatchlistSorting.HighestCap -> StatSortType.HighestCap
        WatchlistSorting.LowestCap -> StatSortType.LowestCap
        WatchlistSorting.Gainers -> StatSortType.TopGainers
        WatchlistSorting.Losers -> StatSortType.TopLosers
    }


val CoinModule.Tab.statTab: StatTab
    get() = when (this) {
        CoinModule.Tab.Overview -> StatTab.Overview
        CoinModule.Tab.Details -> StatTab.Analytics
        CoinModule.Tab.Market -> StatTab.Markets
    }

val CoinAnalyticsModule.RankType.statPage: StatPage
    get() = when (this) {
        CoinAnalyticsModule.RankType.CexVolumeRank -> StatPage.CoinRankCexVolume
        CoinAnalyticsModule.RankType.DexVolumeRank -> StatPage.CoinRankDexVolume
        CoinAnalyticsModule.RankType.DexLiquidityRank -> StatPage.CoinRankDexLiquidity
        CoinAnalyticsModule.RankType.AddressesRank -> StatPage.CoinRankAddress
        CoinAnalyticsModule.RankType.TransactionCountRank -> StatPage.CoinRankTxCount
        CoinAnalyticsModule.RankType.RevenueRank -> StatPage.CoinRankRevenue
        CoinAnalyticsModule.RankType.FeeRank -> StatPage.CoinRankFee
        CoinAnalyticsModule.RankType.HoldersRank -> StatPage.CoinRankHolders
    }

val cash.p.terminal.wallet.AccountType.statAccountType: String
    get() = when (this) {
        is cash.p.terminal.wallet.AccountType.Mnemonic -> {
            if (passphrase.isEmpty()) "mnemonic_${words.size}" else "mnemonic_with_passphrase_${words.size}"
        }

        is cash.p.terminal.wallet.AccountType.BitcoinAddress -> {
            "btc_address"
        }

        is cash.p.terminal.wallet.AccountType.Cex -> {
            "cex"
        }

        is cash.p.terminal.wallet.AccountType.EvmAddress -> {
            "evm_address"
        }

        is cash.p.terminal.wallet.AccountType.EvmPrivateKey -> {
            "evm_private_key"
        }

        is cash.p.terminal.wallet.AccountType.HdExtendedKey -> {
            if (hdExtendedKey.isPublic) {
                "account_x_pub_key"
            } else {
                when (hdExtendedKey.derivedType) {
                    HDExtendedKey.DerivedType.Bip32 -> "bip32"
                    HDExtendedKey.DerivedType.Master -> "bip32_root_key"
                    HDExtendedKey.DerivedType.Account -> "account_x_priv_key"
                }
            }
        }

        is cash.p.terminal.wallet.AccountType.SolanaAddress -> {
            "sol_address"
        }

        is cash.p.terminal.wallet.AccountType.TonAddress -> {
            "ton_address"
        }

        is cash.p.terminal.wallet.AccountType.TronAddress -> {
            "tron_address"
        }
    }


val MetricsType.statPage: StatPage
    get() = when (this) {
        MetricsType.TotalMarketCap -> StatPage.GlobalMetricsMarketCap
        MetricsType.Volume24h -> StatPage.GlobalMetricsVolume
        MetricsType.Etf -> StatPage.GlobalMetricsEtf
        MetricsType.TvlInDefi -> StatPage.GlobalMetricsTvlInDefi
    }

val MainModule.MainNavigation.statTab: StatTab
    get() = when (this) {
        MainModule.MainNavigation.Market -> StatTab.Markets
        MainModule.MainNavigation.Balance -> StatTab.Balance
        MainModule.MainNavigation.Transactions -> StatTab.Transactions
        MainModule.MainNavigation.Settings -> StatTab.Settings
    }

val TopMarket.statMarketTop: StatMarketTop
    get() = when (this) {
        TopMarket.Top100 -> StatMarketTop.Top100
        TopMarket.Top200 -> StatMarketTop.Top200
        TopMarket.Top300 -> StatMarketTop.Top300
        TopMarket.Top500 -> StatMarketTop.Top500
    }

val MarketModule.ListType.statSection: StatSection
    get() = when (this) {
        MarketModule.ListType.TopGainers -> StatSection.TopGainers
        MarketModule.ListType.TopLosers -> StatSection.TopLosers
    }

val TimeDuration.statPeriod: StatPeriod
    get() = when (this) {
        TimeDuration.OneDay -> StatPeriod.Day1
        TimeDuration.SevenDay -> StatPeriod.Week1
        TimeDuration.ThirtyDay -> StatPeriod.Month1
    }

val MarketModule.Tab.statTab: StatTab
    get() = when (this) {
        MarketModule.Tab.Posts -> StatTab.News
        MarketModule.Tab.Watchlist -> StatTab.Watchlist
        MarketModule.Tab.Coins -> StatTab.Coins
        MarketModule.Tab.Platform -> StatTab.Platforms
        MarketModule.Tab.Pairs -> StatTab.Pairs
    }

val MarketSearchSection.statSection: StatSection
    get() = when (this) {
        MarketSearchSection.Recent -> StatSection.Recent
        MarketSearchSection.Popular -> StatSection.Popular
        MarketSearchSection.SearchResults -> StatSection.SearchResults
    }

val TimePeriod.statPeriod: StatPeriod
    get() = when (this) {
        TimePeriod.TimePeriod_1D -> StatPeriod.Day1
        TimePeriod.TimePeriod_1W -> StatPeriod.Week1
        TimePeriod.TimePeriod_1M -> StatPeriod.Month1
        TimePeriod.TimePeriod_2W -> TODO()
        TimePeriod.TimePeriod_3M -> TODO()
        TimePeriod.TimePeriod_6M -> TODO()
        TimePeriod.TimePeriod_1Y -> TODO()
    }

val FilterTransactionType.statTab: StatTab
    get() = when (this) {
        FilterTransactionType.All -> StatTab.All
        FilterTransactionType.Incoming -> StatTab.Incoming
        FilterTransactionType.Outgoing -> StatTab.Outgoing
        FilterTransactionType.Swap -> StatTab.Swap
        FilterTransactionType.Approve -> StatTab.Approve
    }

val SpeedUpCancelType.statResendType: StatResendType
    get() = when (this) {
        SpeedUpCancelType.SpeedUp -> StatResendType.SpeedUp
        SpeedUpCancelType.Cancel -> StatResendType.Cancel
    }

val ThemeType.statValue: String
    get() = when (this) {
        ThemeType.Dark -> "dark"
        ThemeType.Light -> "light"
        ThemeType.System -> "system"
    }

val PriceChangeInterval.statValue: String
    get() = when (this) {
        PriceChangeInterval.LAST_24H -> "hour_24"
        PriceChangeInterval.FROM_UTC_MIDNIGHT -> "midnight_utc"
    }

val BalanceViewType.statValue: String
    get() = when (this) {
        BalanceViewType.CoinThenFiat -> "coin"
        BalanceViewType.FiatThenCoin -> "currency"
    }

val LaunchPage.statValue: String
    get() = when (this) {
        LaunchPage.Auto -> "auto"
        LaunchPage.Balance -> "balance"
        LaunchPage.Market -> "market_overview"
        LaunchPage.Watchlist -> "watchlist"
    }

val TvlModule.TvlDiffType.statType: String
    get() = when (this) {
        TvlModule.TvlDiffType.Percent -> "percent"
        TvlModule.TvlDiffType.Currency -> "currency"
    }

val EtfModule.SortBy.statSortType: StatSortType
    get() = when (this) {
        EtfModule.SortBy.HighestAssets -> StatSortType.HighestAssets
        EtfModule.SortBy.LowestAssets -> StatSortType.LowestAssets
        EtfModule.SortBy.Inflow -> StatSortType.Inflow
        EtfModule.SortBy.Outflow -> StatSortType.Outflow
    }
