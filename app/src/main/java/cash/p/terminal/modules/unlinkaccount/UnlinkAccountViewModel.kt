package cash.p.terminal.modules.unlinkaccount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.R
import cash.p.terminal.strings.helpers.TranslatableString

class UnlinkAccountViewModel(
    private val account: cash.p.terminal.wallet.Account,
    private val accountManager: cash.p.terminal.wallet.IAccountManager
) : ViewModel() {
    val accountName = account.name

    var confirmations by mutableStateOf<List<ConfirmationItem>>(listOf())
        private set
    var unlinkEnabled by mutableStateOf(false)
        private set
    var showDeleteWarning by mutableStateOf(false)
        private set

    val deleteButtonText = when {
            account.isWatchAccount -> R.string.ManageKeys_StopWatching
            else -> R.string.ManageKeys_Delete_FromPhone
        }

    init {
        if (account.isWatchAccount) {
            showDeleteWarning = true
        } else {
            confirmations = listOf(
                ConfirmationItem(ConfirmationType.ConfirmationRemove),
                ConfirmationItem(ConfirmationType.ConfirmationLos),
            )
        }

        updateUnlinkEnabledState()
    }

    fun toggleConfirm(item: ConfirmationItem) {
        val index = confirmations.indexOf(item)
        if (index != -1) {
            confirmations = confirmations.toMutableList().apply {
                this[index] = item.copy(confirmed = !item.confirmed)
            }

            updateUnlinkEnabledState()
        }
    }

    fun onUnlink() {
        accountManager.delete(account.id)
    }

    private fun updateUnlinkEnabledState() {
        unlinkEnabled = confirmations.none { !it.confirmed }
    }
}

enum class ConfirmationType(val title: TranslatableString) {
    ConfirmationRemove(TranslatableString.ResString(R.string.ManageAccount_Delete_ConfirmationRemove)),
    ConfirmationLos(TranslatableString.ResString(R.string.ManageAccount_Delete_ConfirmationLose))
}

data class ConfirmationItem(val confirmationType: ConfirmationType, val confirmed: Boolean = false)