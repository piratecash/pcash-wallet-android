package cash.p.terminal.modules.displayoptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class DisplayOptionsViewModel(
    private val accountManager: IAccountManager,
    private val localStorage: ILocalStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisplayOptionsUiState(
        isRoundingAmountMainPage = localStorage.isRoundingAmountMainPage,
        isCoinManagerEnabled = true
    ))
    val uiState: StateFlow<DisplayOptionsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val pricePeriod = localStorage.displayDiffPricePeriod
            val displayDiffOptionType = localStorage.displayDiffOptionType

            _uiState.value = DisplayOptionsUiState(
                isCoinManagerEnabled = accountManager.activeAccount?.type !is AccountType.MnemonicMonero,
                isRoundingAmountMainPage = localStorage.isRoundingAmountMainPage,
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

    fun onRoundingAmountMainPageToggled(enabled: Boolean) {
        localStorage.isRoundingAmountMainPage = enabled
        updateUiState { it.copy(isRoundingAmountMainPage = enabled) }
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
    val isCoinManagerEnabled : Boolean,
    val isRoundingAmountMainPage: Boolean,
    val pricePeriod: DisplayPricePeriod = DisplayPricePeriod.ONE_DAY,
    val displayDiffOptionType: DisplayDiffOptionType = DisplayDiffOptionType.BOTH,
)
