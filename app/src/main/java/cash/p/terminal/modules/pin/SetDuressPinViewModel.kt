package cash.p.terminal.modules.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.managers.DefaultUserManager

class SetDuressPinViewModel(
    private val input: SetDuressPinFragment.Input?,
    private val userManager: DefaultUserManager,
) : ViewModel() {

    fun onDuressPinSet() {
        val accountIds = input?.accountIds
        if (!accountIds.isNullOrEmpty()) {
            userManager.allowAccountsForDuress(accountIds)
        }
    }

    class Factory(private val input: SetDuressPinFragment.Input?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SetDuressPinViewModel(input, App.userManager) as T
        }
    }

}
