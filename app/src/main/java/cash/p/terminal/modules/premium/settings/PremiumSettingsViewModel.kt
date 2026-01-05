package cash.p.terminal.modules.premium.settings

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.feature.logging.domain.usecase.LogLoginAttemptUseCase
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.launch

internal class PremiumSettingsViewModel(
    private val localStorage: ILocalStorage,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val logLoginAttemptUseCase: LogLoginAttemptUseCase,
) : ViewModelUiState<PremiumSettingsUiState>() {
    private var checkEnabled = localStorage.recipientAddressContractCheckEnabled
    private var showLoggingAlert = false

    init {
        checkLoggingAlert()
    }

    private fun checkLoggingAlert() {
        viewModelScope.launch {
            showLoggingAlert = logLoginAttemptUseCase.shouldShowLoggingAlert()
            emitState()
        }
    }

    override fun createState() = PremiumSettingsUiState(
        checkEnabled = checkEnabled && checkPremiumUseCase.getPremiumType().isPremium(),
        showAlertIcon = showLoggingAlert
    )

    fun setAddressContractChecking(enabled: Boolean) {
        localStorage.recipientAddressContractCheckEnabled = enabled
        checkEnabled = enabled

        emitState()
    }
}


internal data class PremiumSettingsUiState(
    val checkEnabled: Boolean,
    val showAlertIcon: Boolean = false
)