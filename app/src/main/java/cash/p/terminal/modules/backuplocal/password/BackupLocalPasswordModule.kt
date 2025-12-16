package cash.p.terminal.modules.backuplocal.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.ui_compose.entities.DataState
import io.horizontalsystems.core.CoreApp

object BackupLocalPasswordModule {

    class Factory(private val backupType: BackupType) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BackupLocalPasswordViewModel(
                backupType,
                PassphraseValidator(),
                App.accountManager,
                App.backupProvider,
                CoreApp.pinComponent,
            ) as T
        }
    }

    data class UiState(
        val passphraseState: DataState.Error?,
        val passphraseConfirmState: DataState.Error?,
        val showButtonSpinner: Boolean,
        val backupData: ByteArray?,
        val closeScreen: Boolean,
        val error: String?,
        // Duress backup fields
        val duressBackupEnabled: Boolean = false,
        val duressBackupAvailable: Boolean = false,
        val pinEnabled: Boolean = false,
        val duressPassphraseState: DataState.Error? = null,
        val duressPassphraseConfirmState: DataState.Error? = null
    )
}