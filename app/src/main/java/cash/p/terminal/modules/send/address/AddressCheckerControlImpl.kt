package cash.p.terminal.modules.send.address

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase

internal class AddressCheckerControlImpl(
    private val localStorage: ILocalStorage,
    checkPremiumUseCase: CheckPremiumUseCase
): AddressCheckerControl {
    override var uiState by mutableStateOf(
        AddressCheckerUIState(
            addressCheckByBaseEnabled = localStorage.recipientAddressBaseCheckEnabled,
            addressCheckSmartContractEnabled = localStorage.recipientAddressContractCheckEnabled &&
                    checkPremiumUseCase.getPremiumType().isPremium()
        )
    )

    override fun onCheckBaseAddressClick(enabled: Boolean) {
        localStorage.recipientAddressBaseCheckEnabled = enabled
        uiState = uiState.copy(
            addressCheckByBaseEnabled = enabled
        )
    }

    override fun onCheckSmartContractAddressClick(enabled: Boolean) {
        localStorage.recipientAddressContractCheckEnabled = enabled
        uiState = uiState.copy(
            addressCheckSmartContractEnabled = enabled
        )
    }
}