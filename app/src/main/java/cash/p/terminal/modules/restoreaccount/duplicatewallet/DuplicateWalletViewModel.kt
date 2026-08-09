package cash.p.terminal.modules.restoreaccount.duplicatewallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.CurationResult
import cash.p.terminal.core.managers.DeclinedToken
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.curatedEnabledWallets
import cash.p.terminal.core.managers.storedToken
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.modules.backuplocal.fullbackup.WalletDeclinedTokens
import cash.p.terminal.modules.declinedtokens.DeclinedTokensReview
import cash.p.terminal.modules.declinedtokens.DeclinedTokensReviewHost
import cash.p.terminal.modules.declinedtokens.DeclinedTokensStage
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType.BitcoinAddress
import cash.p.terminal.wallet.AccountType.EvmAddress
import cash.p.terminal.wallet.AccountType.EvmPrivateKey
import cash.p.terminal.wallet.AccountType.HardwareCard
import cash.p.terminal.wallet.AccountType.HdExtendedKey
import cash.p.terminal.wallet.AccountType.TrezorDevice
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
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import kotlinx.coroutines.CancellationException
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
    private val marketKit: MarketKitWrapper,
) : ViewModel(), DeclinedTokensReviewHost {

    private val passphraseValidator = PassphraseValidator()
    private val passcodeOld = (accountToCopy.type as? Mnemonic)?.passphrase.orEmpty()
    private var passphrase = passcodeOld
    private var passphraseConfirmation = passcodeOld
    private var pendingAccount: Account? = null

    var uiState by mutableStateOf(
        DuplicateWalletUiState(
            passphraseAvailable = accountToCopy.type is Mnemonic,
            passphraseEnabled = passcodeOld.isNotEmpty(),
            passcodeOld = passcodeOld,
            accountName = accountFactory.getUniqueName(
                accountToCopy.name + " " + Translator.getString(R.string.copy_wallet_suffix)
            ),
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
                is TrezorDevice,
                is HdExtendedKey,
                is StellarSecretKey -> {
                    uiState = uiState.copy(
                        error = Translator.getString(R.string.unsupported_duplicate_wallet)
                    )
                    return@launch
                }
            }

            val finalName = accountFactory.getUniqueName(uiState.accountName)
            val newAccount = accountFactory.account(
                name = finalName,
                type = type,
                origin = accountToCopy.origin,
                backedUp = accountToCopy.isBackedUp,
                fileBackedUp = accountToCopy.isFileBackedUp
            )
            copyAccount(newAccount, approved = null)
        }
    }

    private suspend fun copyAccount(
        newAccount: Account,
        approved: Set<String>?
    ) = withContext(Dispatchers.IO) {
        val curation = curatedWalletsToCopy(newAccount.id, approved.orEmpty())
            ?: return@withContext

        if (approved == null && curation.declined.isNotEmpty()) {
            requestTokenReview(newAccount, curation.declined)
            return@withContext
        }

        writeCopiedAccount(newAccount, curation.enabled)
    }

    /** Parks the account and surfaces declined rows for approval — nothing is persisted until reviewed. */
    private fun requestTokenReview(
        newAccount: Account,
        declined: List<DeclinedToken>
    ) {
        pendingAccount = newAccount
        uiState = uiState.copy(
            tokenReview = DeclinedTokensReview(
                listOf(WalletDeclinedTokens(newAccount.id, newAccount.name, declined))
            )
        )
    }

    private suspend fun writeCopiedAccount(newAccount: Account, wallets: List<EnabledWallet>) {
        wallets.forEach {
            val tokenQuery = TokenQuery.fromId(it.tokenQueryId) ?: return@forEach
            val settings = restoreSettingsManager.settings(accountToCopy, tokenQuery.blockchainType)
            restoreSettingsManager.save(settings, newAccount, tokenQuery.blockchainType)
        }

        // Not caught: these writes aren't atomic, so letting a failure propagate beats retrying into a duplicate account.
        accountManager.save(newAccount)
        walletManager.saveEnabledWallets(wallets)

        uiState = uiState.copy(
            error = null,
            createButtonEnabled = false,
            closeScreen = true
        )
    }

    /** Metadata comes from the catalog, never the caller's row. Stored decimals are trusted: the rows are this device's own. */
    private suspend fun curatedWalletsToCopy(
        newAccountId: String,
        approved: Set<String>
    ): CurationResult? = try {
        val sourceWallets = enabledWalletStorage.enabledWallets(accountToCopy.id)
        marketKit.curatedEnabledWallets(
            accountId = newAccountId,
            storedTokens = sourceWallets.map {
                storedToken(
                    tokenQueryId = it.tokenQueryId,
                    trustedDecimals = true,
                    coinName = it.coinName,
                    coinCode = it.coinCode,
                    decimals = it.coinDecimals
                )
            },
            approvedTokenQueryIds = approved
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Failed to look up curated tokens to copy")
        uiState = uiState.copy(
            error = e.message,
            createButtonEnabled = true
        )
        null
    }

    override val tokenReview: DeclinedTokensReview?
        get() = uiState.tokenReview

    override fun onReviewTokens() {
        uiState = uiState.copy(tokenReview = uiState.tokenReview?.copy(stage = DeclinedTokensStage.Select))
    }

    /** Single-wallet flow, so `approvals.values.firstOrNull()` is always the whole review's answer. */
    override fun onApproveTokens(approvals: Map<String, Set<String>>) {
        val account = pendingAccount
        uiState = uiState.copy(tokenReview = null)
        pendingAccount = null
        account ?: return
        viewModelScope.launch {
            copyAccount(account, approved = approvals.values.firstOrNull().orEmpty())
        }
    }

    override fun onDismissTokenReview() {
        uiState = uiState.copy(tokenReview = null, createButtonEnabled = true)
        pendingAccount = null
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
    val closeScreen: Boolean = false,
    val tokenReview: DeclinedTokensReview? = null
)
