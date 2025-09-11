package cash.p.terminal.modules.send.address

interface AddressCheckerControl {
    val uiState: AddressCheckerUIState

    fun onCheckBaseAddressClick(enabled: Boolean)

    fun onCheckSmartContractAddressClick(enabled: Boolean)
}

data class AddressCheckerUIState(
    val addressCheckByBaseEnabled: Boolean,
    val addressCheckSmartContractEnabled: Boolean,
)