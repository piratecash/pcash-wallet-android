package cash.p.terminal.modules.walletconnect

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccountTypeNotSupportedViewModel(
    input: AccountTypeNotSupportedDialog.Input,
    accountManager: IAccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AccountTypeNotSupportedUiState(
            iconResId = input.iconResId,
            titleResId = input.titleResId,
            accountTypeDescription = accountManager.activeAccount?.type?.description ?: "n/a",
            connectionLabel = input.connectionLabel
        )
    )
    val uiState: StateFlow<AccountTypeNotSupportedUiState> = _uiState.asStateFlow()
}

data class AccountTypeNotSupportedUiState(
    @DrawableRes val iconResId: Int,
    @StringRes val titleResId: Int,
    val accountTypeDescription: String,
    val connectionLabel: String
)
