package io.horizontalsystems.chartview.chart

import androidx.lifecycle.viewModelScope
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.components.TabItem
import io.horizontalsystems.chartview.ChartData
import io.horizontalsystems.chartview.R
import io.horizontalsystems.chartview.entity.ChartInfoData
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.Value
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.entities.viewState
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.core.models.HsTimePeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import org.koin.java.KoinJavaComponent.inject
import java.util.Date

open class ChartViewModel(
    private val service: AbstractChartService,
    private val valueFormatter: ChartModule.ChartNumberFormatter,
    private val considerAlwaysPositive: Boolean = false,
) : ViewModelUiState<ChartUiState>() {

    private var tabItems = listOf<TabItem<HsTimePeriod?>>()
    private var chartHeaderView: ChartModule.ChartHeaderView? = null
    private var chartInfoData: ChartInfoData? = null
    private var loading = false
    private var titleHidden = false
    private var viewState: ViewState = ViewState.Success
    private val numberFormatter: IAppNumberFormatter by inject(IAppNumberFormatter::class.java)

    init {
        loading = true
        emitState()

        viewModelScope.launch {
            service.chartTypeObservable.asFlow().collect { chartType ->
                val tabItems = service.chartIntervals.map {
                    val titleResId = it?.stringResId ?: R.string.CoinPage_TimeDuration_All
                    TabItem(Translator.getString(titleResId), it == chartType.orElse(null), it)
                }
                this@ChartViewModel.tabItems = tabItems

                emitState()
            }
        }

        viewModelScope.launch {
            service.chartPointsWrapperObservable.asFlow().collect { chartItemsDataState ->
                chartItemsDataState.viewState?.let {
                    viewState = it
                }

                loading = false

                syncChartItems(chartItemsDataState.getOrNull())

                emitState()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            service.start()
        }
    }

    protected fun setTitleHidden(titleHidden: Boolean) {
        this.titleHidden = titleHidden
        emitState()
    }

    override fun createState() = ChartUiState(
        tabItems = tabItems,
        chartHeaderView = chartHeaderView,
        chartInfoData = chartInfoData,
        loading = loading,
        viewState = viewState,
        hasVolumes = service.hasVolumes,
        chartViewType = service.chartViewType,
        considerAlwaysPositive = considerAlwaysPositive,
        titleHidden = titleHidden
    )

    fun onSelectChartInterval(chartInterval: HsTimePeriod?) {
        loading = true
        viewModelScope.launch {
            // Solution to prevent flickering.
            //
            // When items are loaded fast for chartInterval change
            // it shows loading state for a too little period of time.
            // It looks like a flickering.
            // It is true for most cases. Updating UI with some delay resolves it.
            // Since it is true for the most cases here we set delay.
            delay(300)
            emitState()
        }

        service.updateChartInterval(chartInterval)
    }

    fun refresh() {
        loading = true
        emitState()

        service.refresh()
    }

    private fun syncChartItems(chartPointsWrapper: ChartPointsWrapper?) {
        if (chartPointsWrapper == null || chartPointsWrapper.items.isEmpty()) {
            chartHeaderView = null
            chartInfoData = null

            return
        }

        val chartData = ChartData(
            chartPointsWrapper.items,
            chartPointsWrapper.isMovementChart,
            false,
            chartPointsWrapper.indicators
        )

        val headerView = if (!chartPointsWrapper.isMovementChart) {
            val value = valueFormatter.formatValue(service.currency, chartData.sum())
            ChartModule.ChartHeaderView(
                value = value,
                valueHint = null,
                date = null,
                diff = null,
                extraData = null
            )
        } else {
            val chartItems = chartPointsWrapper.items

            val latestItem = chartItems.last()
            val lastItemValue = latestItem.value
            val currentValue =
                valueFormatter.formatValue(service.currency, lastItemValue.toBigDecimal())

            val dominanceData = latestItem.dominance?.let { dominance ->
                val earliestItem = chartItems.first()
                val diff = earliestItem.dominance?.let { earliestDominance ->
                    Value.Percent((dominance - earliestDominance).toBigDecimal())
                }

                ChartModule.ChartHeaderExtraData.Dominance(
                    numberFormatter.format(dominance, 0, 2, suffix = "%"),
                    diff
                )
            }
            ChartModule.ChartHeaderView(
                value = currentValue,
                valueHint = null,
                date = null,
                diff = Value.Percent(chartData.diff()),
                extraData = dominanceData
            )
        }

        val (minValue, maxValue) = getMinMax(chartData.minValue, chartData.maxValue)

        val chartInfoData = ChartInfoData(
            chartData,
            maxValue,
            minValue
        )

        this.chartHeaderView = headerView
        this.chartInfoData = chartInfoData
    }

    private val noChangesLimitPercent = 0.2f
    private fun getMinMax(minValue: Float, maxValue: Float): Pair<String?, String?> {
        var max = maxValue
        var min = minValue

        if (max == min) {
            min *= (1 - noChangesLimitPercent)
            max *= (1 + noChangesLimitPercent)
        }

        val maxValueStr = getFormattedValue(max, service.currency)
        val minValueStr = getFormattedValue(min, service.currency)

        return Pair(minValueStr, maxValueStr)

    }

    private fun getFormattedValue(value: Float, currency: Currency): String {
        return valueFormatter.formatValue(currency, value.toBigDecimal())
    }

    override fun onCleared() {
        service.stop()
    }

    fun getSelectedPoint(selectedItem: SelectedItem): ChartModule.ChartHeaderView {
        val value =
            valueFormatter.formatValue(service.currency, selectedItem.mainValue.toBigDecimal())
        val dayAndTime = DateHelper.getFullDate(Date(selectedItem.timestamp * 1000))

        return ChartModule.ChartHeaderView(
            value = value,
            valueHint = null,
            date = dayAndTime,
            diff = null,
            extraData = getItemExtraData(selectedItem)
        )
    }

    private fun getItemExtraData(item: SelectedItem): ChartModule.ChartHeaderExtraData? {
        val movingAverages = item.movingAverages
        val rsi = item.rsi
        val macd = item.macd
        val dominance = item.dominance
        val volume = item.volume

        return when {
            movingAverages.isNotEmpty() || rsi != null || macd != null -> {
                ChartModule.ChartHeaderExtraData.Indicators(movingAverages, rsi, macd)
            }

            dominance != null -> {
                ChartModule.ChartHeaderExtraData.Dominance(
                    numberFormatter.format(dominance, 0, 2, suffix = "%"),
                    null
                )
            }

            volume != null -> ChartModule.ChartHeaderExtraData.Volume(
                numberFormatter.formatFiatShort(
                    volume.toBigDecimal(),
                    service.currency.symbol,
                    2
                )
            )

            else -> null
        }
    }
}

val HsTimePeriod.stringResId: Int
    get() = when (this) {
        HsTimePeriod.Hour1 -> R.string.CoinPage_TimeDuration_Hour
        HsTimePeriod.Day1 -> R.string.CoinPage_TimeDuration_Day
        HsTimePeriod.Week1 -> R.string.CoinPage_TimeDuration_Week
        HsTimePeriod.Month1 -> R.string.CoinPage_TimeDuration_Month
        HsTimePeriod.Year1 -> R.string.CoinPage_TimeDuration_Year
    }
