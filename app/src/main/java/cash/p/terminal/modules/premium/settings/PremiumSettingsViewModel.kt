package cash.p.terminal.modules.premium.settings

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.premium.domain.PremiumResult
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import io.horizontalsystems.core.ViewModelUiState

internal class PremiumSettingsViewModel(
    private val localStorage: ILocalStorage,
    private val checkPremiumUseCase: CheckPremiumUseCase
) : ViewModelUiState<PremiumSettingsUiState>() {
    private var checkEnabled = localStorage.recipientAddressContractCheckEnabled

    override fun createState() = PremiumSettingsUiState(
        checkEnabled = checkEnabled && checkPremiumUseCase.isAnyPremium()
    )

    fun setAddressContractChecking(enabled: Boolean): PremiumResult {
        if(!checkPremiumUseCase.isAnyPremium()) {
            return PremiumResult.NeedPremium
        }
        localStorage.recipientAddressContractCheckEnabled = enabled
        checkEnabled = enabled

        emitState()
        return PremiumResult.Success
    }
}


internal data class PremiumSettingsUiState(
    val checkEnabled: Boolean
)