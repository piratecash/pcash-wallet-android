package cash.p.terminal.modules.restoreaccount.duplicatewallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType.BitcoinAddress
import cash.p.terminal.wallet.AccountType.EvmAddress
import cash.p.terminal.wallet.AccountType.EvmPrivateKey
import cash.p.terminal.wallet.AccountType.HardwareCard
import cash.p.terminal.wallet.AccountType.HdExtendedKey
import cash.p.terminal.wallet.AccountType.Mnemonic
import cash.p.terminal.wallet.AccountType.MnemonicMonero
import cash.p.terminal.wallet.AccountType.SolanaAddress
import cash.p.terminal.wallet.AccountType.StellarAddress
import cash.p.terminal.wallet.AccountType.StellarSecretKey
import cash.p.terminal.wallet.AccountType.TonAddress
import cash.p.terminal.wallet.AccountType.TronAddress
import cash.p.terminal.wallet.AccountType.ZCashUfvKey
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class DuplicateWalletViewModel(
    private val accountToCopy: Account,
    private val accountManager: IAccountManager,
    private val accountFactory: IAccountFactory,
    private val moneroWalletUseCase: MoneroWalletUseCase,
    private val enabledWalletStorage: IEnabledWalletStorage,
    private val walletManager: IWalletManager,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val localStorage: ILocalStorage,
) : ViewModel() {

    private val passphraseValidator = PassphraseValidator()
    private val passcodeOld = (accountToCopy.type as? Mnemonic)?.passphrase.orEmpty()
    private var passphrase = passcodeOld
    private var passphraseConfirmation = passcodeOld

    var uiState by mutableStateOf(
        DuplicateWalletUiState(
            passphraseAvailable = accountToCopy.type is Mnemonic,
            passphraseEnabled = passcodeOld.isNotEmpty(),
            passcodeOld = passcodeOld,
            accountName = accountToCopy.name + " " + Translator.getString(R.string.copy_wallet_suffix),
        )
    )
        private set

    val passphraseTermsAgreed: Boolean
        get() = localStorage.passphraseTermsAgreed

    fun onPassphraseTermsAgreed() {
        if (!uiState.passphraseEnabled) {
            onTogglePassphrase(true)
        }
    }


    fun onEnterName(name: String) {
        uiState = uiState.copy(accountName = name)
        updateCreateButtonState()
    }

    fun onTogglePassphrase(enabled: Boolean) {
        uiState = uiState.copy(
            passphraseEnabled = enabled,
            passphraseState = null,
            passphraseConfirmState = null,
            passphraseError = null,
            createButtonEnabled = isCreateButtonEnabled()
        )
        updateCreateButtonState()
    }

    fun onChangePassphrase(v: String) {
        if (passphraseValidator.containsValidCharacters(v)) {
            uiState = uiState.copy(passphraseState = null)
            passphrase = v
        } else {
            uiState = uiState.copy(
                passphraseState = DataState.Error(
                    Exception(
                        Translator.getString(R.string.CreateWallet_Error_PassphraseForbiddenSymbols)
                    )
                )
            )
        }
        updateCreateButtonState()
    }

    fun onChangePassphraseConfirmation(v: String) {
        passphraseConfirmation = v
        uiState = uiState.copy(passphraseConfirmState = null)
        updateCreateButtonState()
    }

    private fun passphraseIsInvalid(): Boolean {
        if (uiState.passphraseState is DataState.Error) {
            return true
        }

        if (passphrase.isBlank()) {
            uiState = uiState.copy(
                passphraseState = DataState.Error(
                    Exception(
                        Translator.getString(R.string.CreateWallet_Error_EmptyPassphrase)
                    )
                )
            )
            return true
        }
        if (passphrase != passphraseConfirmation) {
            uiState = uiState.copy(
                passphraseConfirmState = DataState.Error(
                    Exception(
                        Translator.getString(R.string.CreateWallet_Error_InvalidConfirmation)
                    )
                )
            )
            return true
        }
        return false
    }

    private fun updateCreateButtonState() {
        uiState = uiState.copy(
            createButtonEnabled = isCreateButtonEnabled()
        )
    }

    private fun isCreateButtonEnabled(): Boolean = uiState.accountName.isNotBlank() &&
            (!uiState.passphraseEnabled || (passphrase == passphraseConfirmation))

    fun createAccount() {
        viewModelScope.launch {
            if (uiState.passphraseEnabled && passphraseIsInvalid()) return@launch

            uiState = uiState.copy(
                error = null,
                createButtonEnabled = false
            )

            val typeToCopy = accountToCopy.type
            val type = when (typeToCopy) {
                is Mnemonic -> Mnemonic(
                    words = typeToCopy.words,
                    passphrase = if (uiState.passphraseEnabled) passphrase else ""
                )

                is MnemonicMonero -> {
                    val createdType = moneroWalletUseCase.copyWalletFiles(typeToCopy)
                    if (createdType == null) {
                        uiState = uiState.copy(
                            error = Translator.getString(R.string.error_while_duplicating_wallect),
                            createButtonEnabled = true
                        )
                        return@launch
                    } else {
                        createdType
                    }
                }

                is BitcoinAddress,
                is EvmAddress,
                is SolanaAddress,
                is StellarAddress,
                is TonAddress,
                is TronAddress,
                is ZCashUfvKey,
                is EvmPrivateKey,
                is HardwareCard,
                is HdExtendedKey,
                is StellarSecretKey -> {
                    uiState = uiState.copy(
                        error = Translator.getString(R.string.unsupported_duplicate_wallet)
                    )
                    return@launch
                }
            }

            val newAccount = accountFactory.account(
                name = uiState.accountName,
                type = type,
                origin = accountToCopy.origin,
                backedUp = accountToCopy.isBackedUp,
                fileBackedUp = accountToCopy.isFileBackedUp
            )
            copyAccount(newAccount)
        }
    }

    private suspend fun copyAccount(newAccount: Account) =
        withContext(Dispatchers.IO + CoroutineExceptionHandler { _, exception ->
            Timber.e(exception, "Failed to copy account")
            uiState = uiState.copy(
                error = exception.message,
                createButtonEnabled = true
            )
        }) {
            val wallets = enabledWalletStorage.enabledWallets(accountToCopy.id)
                .map {
                    EnabledWallet(
                        tokenQueryId = it.tokenQueryId,
                        accountId = newAccount.id,
                        coinName = it.coinName,
                        coinCode = it.coinCode,
                        coinDecimals = it.coinDecimals,
                        coinImage = it.coinImage
                    )
                }

            wallets.forEach {
                val tokenQuery = TokenQuery.fromId(it.tokenQueryId) ?: return@forEach
                val settings =
                    restoreSettingsManager.settings(accountToCopy, tokenQuery.blockchainType)
                restoreSettingsManager.save(settings, newAccount, tokenQuery.blockchainType)
            }

            accountManager.save(newAccount)
            walletManager.saveEnabledWallets(wallets)

            uiState = uiState.copy(
                error = null,
                createButtonEnabled = false,
                closeScreen = true
            )
        }
}

data class DuplicateWalletUiState(
    val accountName: String,
    val passphraseAvailable: Boolean,
    val passcodeOld: String,
    val passphraseState: DataState.Error? = null,
    val passphraseConfirmState: DataState.Error? = null,
    val passphraseEnabled: Boolean = false,
    val error: String? = null,
    val passphraseError: String? = null,
    val createButtonEnabled: Boolean = true,
    val closeScreen: Boolean = false
)
