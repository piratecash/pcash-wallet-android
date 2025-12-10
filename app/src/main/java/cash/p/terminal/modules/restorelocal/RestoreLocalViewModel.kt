package cash.p.terminal.modules.restorelocal

import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import cash.p.terminal.R
import cash.p.terminal.core.IAccountFactory
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.modules.backuplocal.BackupLocalModule
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
    private var backupV3: BackupLocalModule.BackupV3? = null
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

                // Check for V3 format first
                backupV3 = backupJsonString?.let { backupProvider.parseV3Backup(it) }

                if (backupV3 == null) {
                    // Legacy format parsing
                    fullBackup = try {
                        val backup = gson.fromJson(backupJsonString, FullBackup::class.java)
                        backup.settings.language // if single walletBackup it will throw exception
                        backup
                    } catch (ex: Exception) {
                        null
                    }

                    walletBackup = gson.fromJson(backupJsonString, WalletBackup::class.java)
                    manualBackup = walletBackup?.manualBackup ?: false
                }
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
            backupV3 != null -> {
                backupV3?.let { restoreV3Backup(it) }
            }

            fullBackup != null -> {
                fullBackup?.let { showFullBackupItems(it) }
            }

            walletBackup != null -> {
                walletBackup?.let { restoreSingleWallet(it, accountName) }
            }
        }
    }

    private fun restoreV3Backup(backup: BackupLocalModule.BackupV3): Job {
        showButtonSpinner = true
        emitState()

        return viewModelScope.launch(Dispatchers.IO) {
            try {
                val gson = GsonBuilder()
                    .disableHtmlEscaping()
                    .enableComplexMapKeySerialization()
                    .create()

                // Get inner JSON, cached key, and kdfParams (single Scrypt call)
                val (innerJson, cachedKey, cachedKdfParams) = backupProvider.unwrapV3FormatWithKey(backup, passphrase)

                // Try to parse as FullBackup first
                val parsedFullBackup = try {
                    val fb = gson.fromJson(innerJson, FullBackup::class.java)
                    fb.settings.language // throws if not FullBackup
                    fb
                } catch (ex: Exception) {
                    null
                }

                if (parsedFullBackup != null) {
                    // Use cached key to avoid Scrypt calls for each wallet
                    val decrypted = backupProvider.decryptedFullBackupWithKey(parsedFullBackup, cachedKey, cachedKdfParams, passphrase)

                    val backupItems = backupProvider.fullBackupItems(decrypted)
                    val backupViewItems = backupViewItemFactory.backupViewItems(backupItems)

                    walletBackupViewItems = backupViewItems.first
                    otherBackupViewItems = backupViewItems.second
                    decryptedFullBackup = decrypted
                    showBackupItems = true
                } else {
                    // Parse as single wallet backup
                    val parsedWalletBackup = gson.fromJson(innerJson, WalletBackup::class.java)
                    manualBackup = parsedWalletBackup.manualBackup

                    // Use cached key to avoid another Scrypt call (with kdfParams check)
                    val type = backupProvider.accountTypeWithKey(parsedWalletBackup, cachedKey, cachedKdfParams, passphrase)

                    if (parsedWalletBackup.enabledWallets.isNullOrEmpty()) {
                        showSelectCoins = type
                    } else {
                        backupProvider.restoreSingleWalletBackup(type, accountName, parsedWalletBackup)
                        restored = true
                    }
                }
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
