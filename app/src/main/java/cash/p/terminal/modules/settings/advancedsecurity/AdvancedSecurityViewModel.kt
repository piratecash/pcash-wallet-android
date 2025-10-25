package cash.p.terminal.modules.settings.advancedsecurity

import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.ViewModelUiState

class AdvancedSecurityViewModel(
    private val pinComponent: IPinComponent
) : ViewModelUiState<AdvancedSecurityUiState>() {

    override fun createState() = AdvancedSecurityUiState(
        isSecureResetPinSet = pinComponent.isSecureResetPinSet()
    )

    fun onSecureResetEnabled() {
        emitState()
    }

    fun onSecureResetDisabled() {
        pinComponent.disableSecureResetPin()
        emitState()
    }
}

data class AdvancedSecurityUiState(
    val isSecureResetPinSet: Boolean
)
