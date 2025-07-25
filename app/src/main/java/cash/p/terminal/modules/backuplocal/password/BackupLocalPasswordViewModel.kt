package cash.p.terminal.modules.backuplocal.password

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R

import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.modules.backuplocal.fullbackup.BackupProvider
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.wallet.PassphraseValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed class BackupType {
    class SingleWalletBackup(val accountId: String) : BackupType()
    class FullBackup(val accountIds: List<String>) : BackupType()
}

class BackupLocalPasswordViewModel(
    private val type: BackupType,
    private val passphraseValidator: PassphraseValidator,
    private val accountManager: IAccountManager,
    private val backupProvider: BackupProvider,
) : ViewModelUiState<BackupLocalPasswordModule.UiState>() {

    private var passphrase = ""
    private var passphraseConfirmation = ""

    private var passphraseState: DataState.Error? = null
    private var passphraseConfirmState: DataState.Error? = null
    private var showButtonSpinner = false
    private var closeScreen = false
    private var error: String? = null

    private var backupJson: String? = null

    var backupFileName: String = "PCash_wallet_backup.json"
        private set

    init {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
        val currentDateTime = LocalDateTime.now().format(formatter)

        when (type) {
            is BackupType.SingleWalletBackup -> {
                val account = accountManager.account(type.accountId)
                if (account == null) {
                    error = "Account is NULL"

                } else {
                    val walletName = account.name.replace(" ", "_")
                    backupFileName = "PCash_wallet_backup_${walletName}_${currentDateTime}.json"
                }
            }

            is BackupType.FullBackup -> {
                backupFileName = "PCash_all_wallets_backup_${currentDateTime}.json"
            }
        }

        emitState()
    }

    override fun createState() = BackupLocalPasswordModule.UiState(
        passphraseState = passphraseState,
        passphraseConfirmState = passphraseConfirmState,
        showButtonSpinner = showButtonSpinner,
        backupJson = backupJson,
        closeScreen = closeScreen,
        error = error
    )

    fun onChangePassphrase(v: String) {
        if (passphraseValidator.containsValidCharacters(v)) {
            passphraseState = null
            passphrase = v
        } else {
            passphraseState = DataState.Error(
                Exception(
                    Translator.getString(R.string.CreateWallet_Error_PassphraseForbiddenSymbols)
                )
            )
        }
        emitState()
    }

    fun onChangePassphraseConfirmation(v: String) {
        passphraseConfirmState = null
        passphraseConfirmation = v
        emitState()
    }

    fun onSaveClick() {
        validatePassword()
        if (passphraseState == null && passphraseConfirmState == null) {
            showButtonSpinner = true
            emitState()
            saveAccount()
        }
    }

    fun backupFinished() {
        backupJson = null
        showButtonSpinner = false
        emitState()
        viewModelScope.launch {
            when (type) {
                is BackupType.SingleWalletBackup -> {
                    accountManager.account(type.accountId)?.let { account ->
                        if (!account.isFileBackedUp) {
                            accountManager.update(account.copy(isFileBackedUp = true))
                        }
                    }
                }

                is BackupType.FullBackup -> {
                    // FullBackup doesn't change account's backup state
                }
            }
            delay(1700) //Wait for showing Snackbar (SHORT duration ~ 1500ms)
            closeScreen = true
            emitState()
        }
    }

    fun closeScreenCalled() {
        closeScreen = false
        emitState()
    }

    fun accountErrorIsShown() {
        error = null
        emitState()
    }

    fun backupCanceled() {
        backupJson = null
        showButtonSpinner = false
        emitState()
    }

    private fun saveAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupJson = when (type) {
                    is BackupType.FullBackup -> {
                        backupProvider.createFullBackup(
                            accountIds = type.accountIds,
                            passphrase = passphrase
                        )
                    }

                    is BackupType.SingleWalletBackup -> {
                        val account = accountManager.account(type.accountId) ?: throw Exception("Account is NULL")
                        backupProvider.createWalletBackup(
                            account = account.copy(isFileBackedUp = true),
                            passphrase = passphrase
                        )
                    }
                }
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
            }

            withContext(Dispatchers.Main) {
                emitState()
            }
        }
    }

    private fun validatePassword() {
        passphraseState = null
        passphraseConfirmState = null

        if (passphrase != passphraseConfirmation) {
            passphraseConfirmState = DataState.Error(
                Exception(Translator.getString(R.string.CreateWallet_Error_InvalidConfirmation))
            )
        }

        emitState()
    }
}
