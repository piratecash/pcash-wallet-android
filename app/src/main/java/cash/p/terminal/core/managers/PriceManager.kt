package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PriceManager(
    private val storage: ILocalStorage
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var priceChangeInterval: PriceChangeInterval = storage.priceChangeInterval
        private set

    val displayDiffOptionType: DisplayDiffOptionType
        get() = storage.displayDiffOptionType

    val displayPricePeriod: DisplayPricePeriod
        get() = storage.displayDiffPricePeriod

    val displayDiffOptionTypeFlow: StateFlow<DisplayDiffOptionType>
        get() = storage.displayDiffOptionTypeFlow

    val displayPricePeriodFlow: StateFlow<DisplayPricePeriod>
        get() = storage.displayDiffPricePeriodFlow

    val priceChangeIntervalFlow: StateFlow<PriceChangeInterval>
        get() = storage.priceChangeIntervalFlow

    init {
        coroutineScope.launch {
            storage.priceChangeIntervalFlow.collect {
                priceChangeInterval = it
            }
        }
    }

}
