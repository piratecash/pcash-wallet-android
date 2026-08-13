package cash.p.terminal.modules.unlinkaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.usecase.DeleteAccountUseCase
import cash.p.terminal.wallet.Account

object UnlinkAccountModule {
    class Factory(private val account: Account) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UnlinkAccountViewModel(account, getKoinInstance<DeleteAccountUseCase>()) as T
        }
    }
}
