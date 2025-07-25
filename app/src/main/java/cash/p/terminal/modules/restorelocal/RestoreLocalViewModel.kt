package cash.p.terminal.modules.restorelocal

import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import cash.p.terminal.R
import cash.p.terminal.core.IAccountFactory
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.modules.backuplocal.BackupLocalModule.WalletBackup
import cash.p.terminal.modules.backuplocal.fullbackup.BackupProvider
import cash.p.terminal.modules.backuplocal.fullbackup.BackupViewItemFactory
import cash.p.terminal.modules.backuplocal.fullbackup.DecryptedFullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.FullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.RestoreException
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsViewModel.OtherBackupViewItem
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsViewModel.WalletBackupViewItem
import cash.p.terminal.modules.restorelocal.RestoreLocalModule.UiState
import cash.p.terminal.strings.helpers.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RestoreLocalViewModel(
    private val backupJsonString: String?,
    private val accountFactory: IAccountFactory,
    private val backupProvider: BackupProvider,
    private val backupViewItemFactory: BackupViewItemFactory,
    fileName: String?,
) : ViewModelUiState<UiState>() {

    private var passphrase = ""
    private var passphraseState: DataState.Error? = null
    private var showButtonSpinner = false
    private var walletBackup: WalletBackup? = null
    private var fullBackup: FullBackup? = null
    private var parseError: Exception? = null
    private var showSelectCoins: cash.p.terminal.wallet.AccountType? = null
    private var manualBackup = false
    private var restored = false

    private var decryptedFullBackup: DecryptedFullBackup? = null
    private var walletBackupViewItems: List<WalletBackupViewItem> = emptyList()
    private var otherBackupViewItems: List<OtherBackupViewItem> = emptyList()
    private var showBackupItems = false

    val accountName by lazy {
        fileName?.let { name ->
            return@lazy name
                .replace(".json", "")
                .replace("UW_Backup_", "")
                .replace("_", " ")
        }
        accountFactory.getNextAccountName()
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gson = GsonBuilder()
                    .disableHtmlEscaping()
                    .enableComplexMapKeySerialization()
                    .create()

                fullBackup = try {
                    val backup = gson.fromJson(backupJsonString, FullBackup::class.java)
                    backup.settings.language // if single walletBackup it will throw exception
                    backup
                } catch (ex: Exception) {
                    null
                }

                walletBackup = gson.fromJson(backupJsonString, WalletBackup::class.java)
                manualBackup = walletBackup?.manualBackup ?: false
            } catch (e: Exception) {
                parseError = e
                emitState()
            }
        }
    }

    override fun createState() = UiState(
        passphraseState = passphraseState,
        showButtonSpinner = showButtonSpinner,
        parseError = parseError,
        showSelectCoins = showSelectCoins,
        manualBackup = manualBackup,
        restored = restored,
        walletBackupViewItems = walletBackupViewItems,
        otherBackupViewItems = otherBackupViewItems,
        showBackupItems = showBackupItems
    )

    fun onChangePassphrase(v: String) {
        passphrase = v
        passphraseState = null
        emitState()
    }

    fun onImportClick() {
        when {
            fullBackup != null -> {
                fullBackup?.let { showFullBackupItems(it) }
            }

            walletBackup != null -> {
                walletBackup?.let { restoreSingleWallet(it, accountName) }
            }
        }
    }

    private fun showFullBackupItems(it: FullBackup): Job {
        showButtonSpinner = true
        emitState()

        return viewModelScope.launch(Dispatchers.IO) {
            try {
                val decrypted = backupProvider.decryptedFullBackup(it, passphrase)
                val backupItems = backupProvider.fullBackupItems(decrypted)
                val backupViewItems = backupViewItemFactory.backupViewItems(backupItems)

                walletBackupViewItems = backupViewItems.first
                otherBackupViewItems = backupViewItems.second
                decryptedFullBackup = decrypted
                showBackupItems = true
            } catch (keyException: RestoreException.EncryptionKeyException) {
                parseError = keyException
            } catch (invalidPassword: RestoreException.InvalidPasswordException) {
                passphraseState = DataState.Error(Exception(Translator.getString(R.string.ImportBackupFile_Error_InvalidPassword)))
            } catch (e: Exception) {
                parseError = e
            }

            withContext(Dispatchers.Main) {
                showButtonSpinner = false
                emitState()
            }
        }
    }

    fun shouldShowReplaceWarning(): Boolean {
        return backupProvider.shouldShowReplaceWarning(decryptedFullBackup)
    }

    fun restoreFullBackup() {
        decryptedFullBackup?.let { restoreFullBackup(it) }
    }

    private fun restoreFullBackup(decryptedFullBackup: DecryptedFullBackup) {
        showButtonSpinner = true
        emitState()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupProvider.restoreFullBackup(decryptedFullBackup, passphrase)
                restored = true
            } catch (keyException: RestoreException.EncryptionKeyException) {
                parseError = keyException
            } catch (invalidPassword: RestoreException.InvalidPasswordException) {
                passphraseState = DataState.Error(Exception(Translator.getString(R.string.ImportBackupFile_Error_InvalidPassword)))
            } catch (e: Exception) {
                parseError = e
            }

            showButtonSpinner = false
            withContext(Dispatchers.Main) {
                emitState()
            }
        }
    }

    @Throws
    private fun restoreSingleWallet(backup: WalletBackup, accountName: String) {
        showButtonSpinner = true
        emitState()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val type = backupProvider.accountType(backup, passphrase)
                if (backup.enabledWallets.isNullOrEmpty()) {
                    showSelectCoins = type
                } else {
                    backupProvider.restoreSingleWalletBackup(type, accountName, backup)
                    restored = true
                }
            } catch (keyException: RestoreException.EncryptionKeyException) {
                parseError = keyException
            } catch (invalidPassword: RestoreException.InvalidPasswordException) {
                passphraseState = DataState.Error(Exception(Translator.getString(R.string.ImportBackupFile_Error_InvalidPassword)))
            } catch (e: Exception) {
                parseError = e
            }
            showButtonSpinner = false
            withContext(Dispatchers.Main) {
                emitState()
            }
        }
    }

    fun onSelectCoinsShown() {
        showSelectCoins = null
        emitState()
    }

    fun onBackupItemsShown() {
        showBackupItems = false
        emitState()
    }

}
