package cash.p.terminal.modules.market.filters

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.modules.market.filters.MarketFiltersModule.BlockchainViewItem
import cash.p.terminal.strings.helpers.TranslatableString
import io.horizontalsystems.core.entities.Blockchain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.net.UnknownHostException

class MarketFiltersViewModel(val service: MarketFiltersService)
    : ViewModelUiState<MarketFiltersUiState>() {

    private var coinListSet = FilterViewItemWrapper(
        cash.p.terminal.strings.helpers.Translator.getString(CoinList.Top250.titleResId),
        CoinList.Top250,
    )
    private var period = FilterViewItemWrapper(
        cash.p.terminal.strings.helpers.Translator.getString(TimePeriod.TimePeriod_1D.titleResId),
        TimePeriod.TimePeriod_1D,
    )
    private var filterTradingSignal = FilterViewItemWrapper.getAny<FilterTradingSignal>()
    private var marketCap = rangeEmpty
    private var volume = rangeEmpty
    private var priceChange = FilterViewItemWrapper.getAny<PriceChange>()
    private var outperformedBtcOn = false
    private var outperformedEthOn = false
    private var outperformedBnbOn = false
    private var priceCloseToAth = false
    private var priceCloseToAtl = false
    private var listedOnTopExchangesOn = false
    private var solidCexOn = false
    private var solidDexOn = false
    private var goodDistributionOn = false
    private var selectedBlockchainsValue: String? = null
    private var selectedBlockchains = listOf<Blockchain>()
    private var blockchainOptions = listOf<BlockchainViewItem>()
    private var showSpinner = false
    private var buttonEnabled = false
    private var buttonTitle = cash.p.terminal.strings.helpers.Translator.getString(R.string.Market_Filter_ShowResults)
    private var errorMessage: TranslatableString? = null

    private var reloadDataJob: Job? = null

    val coinListsViewItemOptions = CoinList.values().map {
        FilterViewItemWrapper(cash.p.terminal.strings.helpers.Translator.getString(it.titleResId), it)
    }
    val marketCapViewItemOptions = getRanges(service.currencyCode)
    val volumeViewItemOptions = getRanges(service.currencyCode)
    val periodViewItemOptions = TimePeriod.values().map {
        FilterViewItemWrapper(cash.p.terminal.strings.helpers.Translator.getString(it.titleResId), it)
    }

    val tradingSignals = listOf(FilterViewItemWrapper.getAny<FilterTradingSignal>()) +
                FilterTradingSignal.values().map { FilterViewItemWrapper<FilterTradingSignal?>(cash.p.terminal.strings.helpers.Translator.getString(it.titleResId), it) }
    val priceChangeViewItemOptions =
        listOf(FilterViewItemWrapper.getAny<PriceChange>()) + PriceChange.values().map {
            FilterViewItemWrapper<PriceChange?>(cash.p.terminal.strings.helpers.Translator.getString(it.titleResId), it)
        }

    init {
        showSpinner = true
        updateSelectedBlockchains()
        emitState()
        reloadData()
    }

    override fun createState() = MarketFiltersUiState(
        coinListSet = coinListSet,
        period = period,
        marketCap = marketCap,
        volume = volume,
        priceChange = priceChange,
        outperformedBtcOn = outperformedBtcOn,
        outperformedEthOn = outperformedEthOn,
        outperformedBnbOn = outperformedBnbOn,
        priceCloseToAth = priceCloseToAth,
        priceCloseToAtl = priceCloseToAtl,
        selectedBlockchainsValue = selectedBlockchainsValue,
        selectedBlockchains = selectedBlockchains,
        blockchainOptions = blockchainOptions,
        showSpinner = showSpinner,
        buttonEnabled = buttonEnabled,
        buttonTitle = buttonTitle,
        errorMessage = errorMessage,
        listedOnTopExchangesOn = listedOnTopExchangesOn,
        solidCexOn = solidCexOn,
        solidDexOn = solidDexOn,
        goodDistributionOn = goodDistributionOn,
        filterTradingSignal = filterTradingSignal,
    )

    fun reset() {
        updateCoinList(
            FilterViewItemWrapper(
                cash.p.terminal.strings.helpers.Translator.getString(CoinList.Top250.titleResId),
                CoinList.Top250,
            )
        )
        marketCap = rangeEmpty
        volume = rangeEmpty
        period = FilterViewItemWrapper(
            cash.p.terminal.strings.helpers.Translator.getString(TimePeriod.TimePeriod_1D.titleResId),
            TimePeriod.TimePeriod_1D,
        )
        priceChange = FilterViewItemWrapper.getAny()
        outperformedBtcOn = false
        outperformedEthOn = false
        outperformedBnbOn = false
        priceCloseToAth = false
        priceCloseToAtl = false
        listedOnTopExchangesOn = false
        solidCexOn = false
        solidDexOn = false
        goodDistributionOn = false
        selectedBlockchains = emptyList()
        filterTradingSignal = FilterViewItemWrapper.getAny()
        updateSelectedBlockchains()
        emitState()
        reloadData()
    }

    fun updateCoinList(value: FilterViewItemWrapper<CoinList>) {
        coinListSet = value
        service.coinCount = value.item.itemsCount
        service.clearCache()
        showSpinner = true
        emitState()
        reloadData()
    }

    fun updateMarketCap(value: FilterViewItemWrapper<Range?>) {
        marketCap = value
        emitState()
        reloadData()
    }

    fun updateVolume(value: FilterViewItemWrapper<Range?>) {
        volume = value
        emitState()
        reloadData()
    }

    fun updatePeriod(value: FilterViewItemWrapper<TimePeriod>) {
        period = value
        emitState()
        reloadData()
    }

    fun updateTradingSignal(value: FilterViewItemWrapper<FilterTradingSignal?>) {
        filterTradingSignal = value
        emitState()
        reloadData()
    }

    fun updatePriceChange(value: FilterViewItemWrapper<PriceChange?>) {
        priceChange = value
        emitState()
        reloadData()
    }

    fun updateOutperformedBtcOn(checked: Boolean) {
        outperformedBtcOn = checked
        emitState()
        reloadData()
    }

    fun updateOutperformedEthOn(checked: Boolean) {
        outperformedEthOn = checked
        emitState()
        reloadData()
    }

    fun updateOutperformedBnbOn(checked: Boolean) {
        outperformedBnbOn = checked
        emitState()
        reloadData()
    }

    fun updateOutperformedAthOn(checked: Boolean) {
        priceCloseToAth = checked
        emitState()
        reloadData()
    }

    fun updateOutperformedAtlOn(checked: Boolean) {
        priceCloseToAtl = checked
        emitState()
        reloadData()
    }

    fun updateListedOnTopExchangesOn(checked: Boolean) {
        listedOnTopExchangesOn = checked
        emitState()
        reloadData()
    }

    fun updateSolidCexOn(checked: Boolean) {
        solidCexOn = checked
        emitState()
        reloadData()
    }

    fun updateSolidDexOn(checked: Boolean) {
        solidDexOn = checked
        emitState()
        reloadData()
    }

    fun updateGoodDistributionOn(checked: Boolean) {
        goodDistributionOn = checked
        emitState()
        reloadData()
    }

    fun anyBlockchains() {
        selectedBlockchains = emptyList()
        updateSelectedBlockchains()
        reloadData()
    }

    fun onBlockchainCheck(blockchain: Blockchain) {
        selectedBlockchains += blockchain
        updateSelectedBlockchains()
        reloadData()
    }

    fun onBlockchainUncheck(blockchain: Blockchain) {
        selectedBlockchains -= blockchain
        updateSelectedBlockchains()
        reloadData()
    }

    private fun reloadData() {
        reloadDataJob?.cancel()
        reloadDataJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                service.filterMarketCap = marketCap.item?.values
                service.filterVolume = volume.item?.values
                service.filterPeriod = period.item
                service.filterPriceChange = priceChange.item?.values
                service.filterOutperformedBtcOn = outperformedBtcOn
                service.filterOutperformedEthOn = outperformedEthOn
                service.filterOutperformedBnbOn = outperformedBnbOn
                service.filterListedOnTopExchanges = listedOnTopExchangesOn
                service.filterSolidCex = solidCexOn
                service.filterSolidDex = solidDexOn
                service.filterGoodDistribution = goodDistributionOn
                service.filterPriceCloseToAth = priceCloseToAth
                service.filterPriceCloseToAtl = priceCloseToAtl
                service.filterBlockchains = selectedBlockchains
                service.filterTradingSignal = filterTradingSignal.item?.getAdvices() ?: emptyList()

                val numberOfItems = service.fetchNumberOfItems()

                buttonTitle = cash.p.terminal.strings.helpers.Translator.getString(R.string.Market_Filter_ShowResults_Counter, numberOfItems)
                buttonEnabled = numberOfItems > 0
                errorMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                buttonTitle = cash.p.terminal.strings.helpers.Translator.getString(R.string.Market_Filter_ShowResults)
                buttonEnabled = false
                errorMessage = convertErrorMessage(e)
            }

            showSpinner = false

            ensureActive()
            emitState()
        }
    }

    private fun updateSelectedBlockchains() {
        blockchainOptions = service.blockchains.map { blockchain ->
            BlockchainViewItem(blockchain, selectedBlockchains.contains(blockchain))
        }
        selectedBlockchainsValue =
            if (selectedBlockchains.isEmpty()) null else selectedBlockchains.size.toString()
    }

    private fun convertErrorMessage(error: Throwable) = when (error) {
        is UnknownHostException -> TranslatableString.ResString(R.string.Hud_Text_NoInternet)
        else -> TranslatableString.PlainString(error.message ?: error.javaClass.simpleName)
    }
}

val rangeEmpty = FilterViewItemWrapper.getAny<Range>()

fun getRanges(currencyCode: String): List<FilterViewItemWrapper<Range?>> {
    return listOf(rangeEmpty) + Range.valuesByCurrency(currencyCode).map {
        FilterViewItemWrapper(cash.p.terminal.strings.helpers.Translator.getString(it.titleResId), it)
    }
}

data class MarketFiltersUiState(
    val coinListSet: FilterViewItemWrapper<CoinList>,
    val period: FilterViewItemWrapper<TimePeriod>,
    val filterTradingSignal: FilterViewItemWrapper<FilterTradingSignal?>,
    val marketCap: FilterViewItemWrapper<Range?>,
    val volume: FilterViewItemWrapper<Range?>,
    val priceChange: FilterViewItemWrapper<PriceChange?>,
    val outperformedBtcOn: Boolean,
    val outperformedEthOn: Boolean,
    val outperformedBnbOn: Boolean,
    val priceCloseToAth: Boolean,
    val priceCloseToAtl: Boolean,
    val selectedBlockchainsValue: String?,
    val selectedBlockchains: List<Blockchain>,
    val blockchainOptions: List<BlockchainViewItem>,
    val showSpinner: Boolean,
    val buttonEnabled: Boolean,
    val buttonTitle: String,
    val errorMessage: TranslatableString?,
    val listedOnTopExchangesOn: Boolean,
    val solidCexOn: Boolean,
    val solidDexOn: Boolean,
    val goodDistributionOn: Boolean
)
