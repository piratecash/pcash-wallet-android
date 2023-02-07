package cash.p.terminal.modules.evmfee.legacy

import cash.p.terminal.core.Warning
import cash.p.terminal.core.subscribeIO
import cash.p.terminal.entities.DataState
import cash.p.terminal.modules.evmfee.FeeRangeConfig
import cash.p.terminal.modules.evmfee.FeeRangeConfig.Bound
import cash.p.terminal.modules.evmfee.FeeSettingsWarning
import cash.p.terminal.modules.evmfee.GasPriceInfo
import cash.p.terminal.modules.evmfee.IEvmGasPriceService
import io.horizontalsystems.ethereumkit.core.LegacyGasPriceProvider
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import java.lang.Long.max
import java.math.BigDecimal

class LegacyGasPriceService(
    private val gasPriceProvider: LegacyGasPriceProvider,
    private val minRecommendedGasPrice: Long? = null,
    private val initialGasPrice: Long? = null,
) : IEvmGasPriceService {

    var recommendedGasPrice: Long? = null
    private var disposable: Disposable? = null

    private val gasPriceRangeConfig = FeeRangeConfig(
        lowerBound = Bound.Multiplied(BigDecimal(0.6)),
        upperBound = Bound.Multiplied(BigDecimal(3.0))
    )

    private val overpricingBound = Bound.Multiplied(BigDecimal(1.5))
    private val riskOfStuckBound = Bound.Multiplied(BigDecimal(0.9))

    override var state: DataState<GasPriceInfo> = DataState.Loading
        private set(value) {
            field = value
            stateSubject.onNext(value)
        }

    private val stateSubject = PublishSubject.create<DataState<GasPriceInfo>>()
    override val stateObservable: Observable<DataState<GasPriceInfo>>
        get() = stateSubject

    private val recommendedGasPriceSingle
        get() = recommendedGasPrice?.let { Single.just(it) }
            ?: gasPriceProvider.gasPriceSingle()
                .map { it }
                .doOnSuccess { gasPrice ->
                    val adjustedGasPrice = max(gasPrice.toLong(), minRecommendedGasPrice ?: 0)
                    recommendedGasPrice = adjustedGasPrice
                    syncGasPriceRange(adjustedGasPrice)
                }

    val defaultGasPriceRange: LongRange = 1_000_000_000..100_000_000_000
    var gasPriceRange: LongRange? = null
        private set

    override var isRecommendedGasPriceSelected = true
        private set

    init {
        if (initialGasPrice != null) {
            setGasPrice(initialGasPrice)
        } else {
            setRecommended()
        }
    }

    fun setRecommended() {
        isRecommendedGasPriceSelected = true

        state = DataState.Loading
        disposable?.dispose()

        recommendedGasPriceSingle
            .subscribeIO({ recommended ->
                state = DataState.Success(
                    GasPriceInfo(
                        gasPrice = GasPrice.Legacy(recommended),
                        warnings = listOf(),
                        errors = listOf()
                    )
                )
            }, {
                state = DataState.Error(it)
            }).let {
                disposable = it
            }
    }

    fun setGasPrice(value: Long) {
        isRecommendedGasPriceSelected = false

        state = DataState.Loading
        disposable?.dispose()

        recommendedGasPriceSingle
            .subscribeIO({ recommended ->
                val warnings = mutableListOf<Warning>()
                val errors = mutableListOf<Throwable>()

                if (value < riskOfStuckBound.calculate(recommended)) {
                    warnings.add(FeeSettingsWarning.RiskOfGettingStuck)
                }

                if (value >= overpricingBound.calculate(recommended)) {
                    warnings.add(FeeSettingsWarning.Overpricing)
                }

                state = DataState.Success(GasPriceInfo(GasPrice.Legacy(value), warnings, errors))
            }, {
                state = DataState.Error(it)
            }).let {
                disposable = it
            }
    }

    private fun syncGasPriceRange(recommendedGasPrice: Long) {
        val range = gasPriceRange

        val lowerBound = if (range == null || range.first > recommendedGasPrice) {
            gasPriceRangeConfig.lowerBound.calculate(recommendedGasPrice)
        } else {
            range.first
        }

        val upperBound = if (range == null || range.last < recommendedGasPrice) {
            gasPriceRangeConfig.upperBound.calculate(recommendedGasPrice)
        } else {
            range.last
        }

        gasPriceRange = lowerBound..upperBound
    }

}