package cash.p.terminal.modules.manageaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.modules.balance.HeaderNote
import cash.p.terminal.wallet.Account

object ManageAccountModule {
    class Factory(private val account: Account) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ManageAccountViewModel(account, App.accountManager) as T
        }
    }

    data class ViewState(
        val title: String,
        val newName: String,
        val canSave: Boolean,
        val closeScreen: Boolean,
        val headerNote: HeaderNote,
        val keyActions: List<KeyAction>,
        val backupActions: List<BackupItem>,
        val signedHashes: Int?
    )

    enum class KeyAction {
        RecoveryPhrase,
        RecoveryPhraseMonero,
        PublicKeys,
        PrivateKeys,
        ResetToFactorySettings,
        ChangeAccessCode,
        AccessCodeRecovery,
        ForgotAccessCode,
        ViewKey,
        SpendKey
    }

    sealed class BackupItem{
        class ManualBackup(val showAttention: Boolean, val completed: Boolean = false) : BackupItem()
        class LocalBackup(val showAttention: Boolean) : BackupItem()
        class InfoText(val textRes: Int) : BackupItem()
    }
}
