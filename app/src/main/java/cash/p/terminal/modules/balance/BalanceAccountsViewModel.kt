package cash.p.terminal.modules.balance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import cash.z.ecc.android.sdk.ext.collectWith

class BalanceAccountsViewModel(accountManager: IAccountManager) : ViewModel() {

    var balanceScreenState by mutableStateOf<BalanceScreenState?>(null)
        private set

    init {
        accountManager.activeAccountStateFlow.collectWith(viewModelScope) {
            handleAccount(it)
        }
    }

    private fun handleAccount(activeAccountState: ActiveAccountState) {
        when (activeAccountState) {
            ActiveAccountState.NotLoaded -> {}
            is ActiveAccountState.ActiveAccount -> {
                balanceScreenState = if (activeAccountState.account != null) {
                    BalanceScreenState.HasAccount(
                        AccountViewItem(
                            isWatchAccount = activeAccountState.account!!.isWatchAccount,
                            isCoinManagerEnabled = activeAccountState.account?.type !is AccountType.MnemonicMonero,
                            name = activeAccountState.account!!.name,
                            id = activeAccountState.account!!.id,
                            type = activeAccountState.account!!.type
                        )
                    )
                } else {
                    BalanceScreenState.NoAccount
                }
            }
        }
    }
}

data class AccountViewItem(
    val isWatchAccount: Boolean,
    val isCoinManagerEnabled: Boolean,
    val name: String = "",
    val id: String,
    val type: AccountType
)

sealed class BalanceScreenState() {
    class HasAccount(val accountViewItem: AccountViewItem) : BalanceScreenState()
    object NoAccount : BalanceScreenState()
}
