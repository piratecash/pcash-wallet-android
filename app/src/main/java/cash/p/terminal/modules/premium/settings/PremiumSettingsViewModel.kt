package cash.p.terminal.modules.premium.settings

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import io.horizontalsystems.core.ViewModelUiState

internal class PremiumSettingsViewModel(
    private val localStorage: ILocalStorage,
    private val checkPremiumUseCase: CheckPremiumUseCase
) : ViewModelUiState<PremiumSettingsUiState>() {
    private var checkEnabled = localStorage.recipientAddressContractCheckEnabled

    override fun createState() = PremiumSettingsUiState(
        checkEnabled = checkEnabled && checkPremiumUseCase.getPremiumType().isPremium()
    )

    fun setAddressContractChecking(enabled: Boolean) {
        localStorage.recipientAddressContractCheckEnabled = enabled
        checkEnabled = enabled

        emitState()
    }
}


internal data class PremiumSettingsUiState(
    val checkEnabled: Boolean
)