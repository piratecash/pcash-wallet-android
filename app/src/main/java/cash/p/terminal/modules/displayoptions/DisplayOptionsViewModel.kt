package cash.p.terminal.modules.displayoptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ILocalStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class DisplayOptionsViewModel(
    private val localStorage: ILocalStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisplayOptionsUiState())
    val uiState: StateFlow<DisplayOptionsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val pricePeriod = localStorage.displayDiffPricePeriod
            val displayDiffOptionType = localStorage.displayDiffOptionType

            _uiState.value = DisplayOptionsUiState(
                pricePeriod = pricePeriod,
                displayDiffOptionType = displayDiffOptionType,
            )
        }
    }

    fun onPricePeriodChanged(period: DisplayPricePeriod) {
        viewModelScope.launch {
            localStorage.displayDiffPricePeriod = period
            updateUiState { it.copy(pricePeriod = period) }
        }
    }

    fun onPercentChangeToggled(enabled: Boolean) {
        val currentState = _uiState.value
        updateDisplayOption(
            showPriceChangeEnabled = currentState.displayDiffOptionType.hasPriceChange,
            showPercentagePriceChangeEnabled = enabled
        )
    }

    fun onPriceChangeToggled(enabled: Boolean) {
        val currentState = _uiState.value
        updateDisplayOption(
            showPriceChangeEnabled = enabled,
            showPercentagePriceChangeEnabled = currentState.displayDiffOptionType.hasPercentChange
        )
    }

    private fun updateDisplayOption(
        showPriceChangeEnabled: Boolean,
        showPercentagePriceChangeEnabled: Boolean
    ) {
        viewModelScope.launch {
            val newOption = DisplayDiffOptionType.fromFlags(showPriceChangeEnabled, showPercentagePriceChangeEnabled)
            localStorage.displayDiffOptionType = newOption
            updateUiState { it.copy(displayDiffOptionType = newOption) }
        }
    }

    private inline fun updateUiState(update: (DisplayOptionsUiState) -> DisplayOptionsUiState) {
        _uiState.value = update(_uiState.value)
    }
}

internal data class DisplayOptionsUiState(
    val pricePeriod: DisplayPricePeriod = DisplayPricePeriod.ONE_DAY,
    val displayDiffOptionType: DisplayDiffOptionType = DisplayDiffOptionType.BOTH,
)
