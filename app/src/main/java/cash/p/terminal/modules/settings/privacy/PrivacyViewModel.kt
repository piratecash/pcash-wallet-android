package cash.p.terminal.modules.settings.privacy

import cash.p.terminal.core.ILocalStorage
import io.horizontalsystems.core.ViewModelUiState

class PrivacyViewModel(private val localStorage: ILocalStorage) :
    ViewModelUiState<PrivacyUiState>() {
    override fun createState() = PrivacyUiState(
        uiStatsEnabled = localStorage.shareCrashDataEnabled
    )
}

data class PrivacyUiState(val uiStatsEnabled: Boolean)
