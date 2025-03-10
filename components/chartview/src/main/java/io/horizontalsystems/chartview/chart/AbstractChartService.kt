package io.horizontalsystems.chartview.chart

import androidx.annotation.CallSuper
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.models.HsTimePeriod
import io.horizontalsystems.chartview.ChartViewType
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import java.util.Optional

abstract class AbstractChartService {
    open val hasVolumes = false
    abstract val chartIntervals: List<HsTimePeriod?>
    abstract val chartViewType: ChartViewType

    protected abstract val currencyManager: CurrencyManager
    protected abstract val initialChartInterval: HsTimePeriod
    protected open suspend fun getAllItems(currency: Currency): ChartPointsWrapper {
        throw Exception("Not Implemented")
    }

    protected abstract suspend fun getItems(
        chartInterval: HsTimePeriod,
        currency: Currency
    ): ChartPointsWrapper

    protected var chartInterval: HsTimePeriod? = null
        set(value) {
            field = value
            chartTypeObservable.onNext(Optional.ofNullable(value))
        }

    val currency: Currency
        get() = currencyManager.baseCurrency
    val chartTypeObservable = BehaviorSubject.create<Optional<HsTimePeriod>>()

    val chartPointsWrapperObservable = BehaviorSubject.create<Result<ChartPointsWrapper>>()

    protected val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var fetchItemsJob: Job? = null

    open suspend fun start() {
        coroutineScope.launch {
            currencyManager.baseCurrencyUpdatedSignal.asFlow().collect {
                fetchItems()
            }
        }

        chartInterval = initialChartInterval
        fetchItems()
    }

    protected fun dataInvalidated() {
        fetchItems()
    }

    open fun stop() {
        coroutineScope.cancel()
    }

    @CallSuper
    open fun updateChartInterval(chartInterval: HsTimePeriod?) {
        this.chartInterval = chartInterval

        fetchItems()
    }

    fun refresh() {
        fetchItems()
    }

    @Synchronized
    private fun fetchItems() {
        fetchItemsJob?.cancel()
        fetchItemsJob = coroutineScope.launch {

            try {
                val tmpChartInterval = chartInterval
                val itemsSingle = when {
                    tmpChartInterval == null -> getAllItems(currency)
                    else -> getItems(tmpChartInterval, currency)
                }
                chartPointsWrapperObservable.onNext(Result.success(itemsSingle))
            } catch (e: CancellationException) {
                // Do nothing
            } catch (e: Throwable) {
                chartPointsWrapperObservable.onNext(Result.failure(e))
            }
        }
    }
}

