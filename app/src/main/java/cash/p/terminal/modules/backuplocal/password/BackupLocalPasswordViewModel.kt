package cash.p.terminal.modules.backuplocal.password

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.managers.DeniableEncryptionManager
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.backuplocal.fullbackup.BackupProvider
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.PassphraseValidator
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.ViewModelUiState
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
    private val pinComponent: IPinComponent,
) : ViewModelUiState<BackupLocalPasswordModule.UiState>() {

    private var passphrase = ""
    private var passphraseConfirmation = ""

    private var passphraseState: DataState.Error? = null
    private var passphraseConfirmState: DataState.Error? = null
    private var showButtonSpinner = false
    private var closeScreen = false
    private var error: String? = null

    private var backupData: ByteArray? = null

    // Duress backup state
    private var duressBackupEnabled = false
    private var duressPassphrase = ""
    private var duressPassphraseConfirmation = ""
    private var duressPassphraseState: DataState.Error? = null
    private var duressPassphraseConfirmState: DataState.Error? = null

    // Duress availability
    val pinEnabled: Boolean get() = pinComponent.isPinSet
    val duressBackupAvailable: Boolean get() = pinComponent.isDuressPinSet()

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
        backupData = backupData,
        closeScreen = closeScreen,
        error = error,
        duressBackupEnabled = duressBackupEnabled,
        duressBackupAvailable = duressBackupAvailable,
        pinEnabled = pinEnabled,
        duressPassphraseState = duressPassphraseState,
        duressPassphraseConfirmState = duressPassphraseConfirmState
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

    fun onChangeDuressPassphrase(v: String) {
        if (passphraseValidator.containsValidCharacters(v)) {
            duressPassphraseState = null
            duressPassphrase = v
        } else {
            duressPassphraseState = DataState.Error(
                Exception(
                    Translator.getString(R.string.CreateWallet_Error_PassphraseForbiddenSymbols)
                )
            )
        }
        emitState()
    }

    fun onChangeDuressPassphraseConfirmation(v: String) {
        duressPassphraseConfirmState = null
        duressPassphraseConfirmation = v
        emitState()
    }

    fun onDuressBackupToggle(enabled: Boolean) {
        duressBackupEnabled = enabled
        if (!enabled) {
            // Clear duress fields when disabled
            duressPassphrase = ""
            duressPassphraseConfirmation = ""
            duressPassphraseState = null
            duressPassphraseConfirmState = null
        }
        emitState()
    }

    fun onSaveClick() {
        validatePassword()
        val mainValid = passphraseState == null && passphraseConfirmState == null
        val duressValid = !duressBackupEnabled ||
                (duressPassphraseState == null && duressPassphraseConfirmState == null)

        if (mainValid && duressValid) {
            showButtonSpinner = true
            emitState()
            saveAccount()
        }
    }

    fun backupFinished() {
        backupData = null
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
                    // Mark main accounts as backed up
                    type.accountIds.forEach { accountId ->
                        accountManager.account(accountId)?.let { account ->
                            if (!account.isFileBackedUp) {
                                accountManager.update(account.copy(isFileBackedUp = true))
                            }
                        }
                    }
                    // Mark duress accounts as backed up
                    if (duressBackupEnabled) {
                        try {
                            val duressLevel = pinComponent.getDuressLevel()
                            accountManager.accountsAtLevel(duressLevel).forEach { account ->
                                if (!account.isFileBackedUp) {
                                    accountManager.update(account.copy(isFileBackedUp = true))
                                }
                            }
                        } catch (_: Exception) {
                            // Ignore errors
                        }
                    }
                }
            }

            // Clear the backup required flow since backup is now done
            accountManager.onHandledBackupRequiredNewAccount()

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
        backupData = null
        showButtonSpinner = false
        emitState()
    }

    private fun saveAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupData = when (type) {
                    is BackupType.FullBackup -> {
                        val duressAccountIds = if (duressBackupEnabled) {
                            tryOrNull {
                                val duressLevel = pinComponent.getDuressLevel()
                                accountManager.accountsAtLevel(duressLevel).map { it.id }
                            }
                        } else {
                            null
                        }
                        val duressPassword = if (duressBackupEnabled) duressPassphrase else null

                        backupProvider.createFullBackupV4Binary(
                            accountIds1 = type.accountIds,
                            passphrase1 = passphrase,
                            accountIds2 = duressAccountIds,
                            passphrase2 = duressPassword
                        )
                    }

                    is BackupType.SingleWalletBackup -> {
                        val account = accountManager.account(type.accountId)
                            ?: throw Exception("Account is NULL")

                        backupProvider.createSingleWalletBackupV4Binary(
                            account = account.copy(isFileBackedUp = true),
                            passphrase = passphrase
                        )
                    }
                }
            } catch (collision: DeniableEncryptionManager.PasswordCollisionException) {
                error = Translator.getString(R.string.local_backup_password_collision)
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

        // Check main password is not empty
        if (passphrase.isEmpty()) {
            passphraseState = DataState.Error(
                Exception(Translator.getString(R.string.local_backup_error_empty_password))
            )
        }

        // Check confirmation matches
        if (passphrase != passphraseConfirmation) {
            passphraseConfirmState = DataState.Error(
                Exception(Translator.getString(R.string.CreateWallet_Error_InvalidConfirmation))
            )
        }

        // Validate duress passwords if enabled
        if (duressBackupEnabled) {
            duressPassphraseState = null
            duressPassphraseConfirmState = null

            // Check duress password is not empty
            if (duressPassphrase.isEmpty()) {
                duressPassphraseState = DataState.Error(
                    Exception(Translator.getString(R.string.local_backup_error_empty_password))
                )
            }

            // Check duress password is not the same as main password
            if (duressPassphrase.isNotEmpty() && duressPassphrase == passphrase) {
                duressPassphraseState = DataState.Error(
                    Exception(Translator.getString(R.string.local_backup_password_collision))
                )
            }

            // Check duress confirmation matches
            if (duressPassphrase != duressPassphraseConfirmation) {
                duressPassphraseConfirmState = DataState.Error(
                    Exception(Translator.getString(R.string.CreateWallet_Error_InvalidConfirmation))
                )
            }
        }

        emitState()
    }
}
