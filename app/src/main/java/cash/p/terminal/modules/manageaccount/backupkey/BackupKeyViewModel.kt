package cash.p.terminal.modules.manageaccount.backupkey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhraseModule
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager

class BackupKeyViewModel(
    accountId: String,
    accountManager: IAccountManager
) : ViewModel() {

    val account: Account? = accountManager.account(accountId)

    var passphrase by mutableStateOf("")
        private set

    var showPassphraseBlock by mutableStateOf(true)
        private set

    var wordsNumbered by mutableStateOf<List<RecoveryPhraseModule.WordNumbered>>(listOf())
        private set

    var accountNotFound by mutableStateOf(false)
        private set

    init {
        if (account == null) {
            accountNotFound = true
        } else {
            showPassphraseBlock = account.type is AccountType.Mnemonic

            when (val type = account.type) {
                is AccountType.Mnemonic -> {
                    wordsNumbered = type.words.mapIndexed { index, word ->
                        RecoveryPhraseModule.WordNumbered(word, index + 1)
                    }
                    passphrase = type.passphrase
                }
                is AccountType.MnemonicMonero -> {
                    wordsNumbered = type.words.mapIndexed { index, word ->
                        RecoveryPhraseModule.WordNumbered(word, index + 1)
                    }
                    passphrase = type.password
                }
                else -> Unit
            }
        }
    }
}
