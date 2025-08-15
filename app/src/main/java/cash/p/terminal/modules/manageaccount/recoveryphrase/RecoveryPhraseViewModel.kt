package cash.p.terminal.modules.manageaccount.recoveryphrase

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.core.utils.MoneroWalletSeedConverter
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType

class RecoveryPhraseViewModel(
    account: Account,
    recoveryPhraseType: RecoveryPhraseFragment.RecoveryPhraseType
) : ViewModel() {
    val words: List<String>
    private val seed: ByteArray?

    var passphrase by mutableStateOf<String?>("")
        private set

    var wordsNumbered by mutableStateOf<List<RecoveryPhraseModule.WordNumbered>>(listOf())
        private set

    init {
        when (account.type) {
            is AccountType.Mnemonic -> {
                if (recoveryPhraseType == RecoveryPhraseFragment.RecoveryPhraseType.Monero) {
                    words = MoneroWalletSeedConverter.getLegacySeedFromBip39(
                        words = (account.type as AccountType.Mnemonic).words,
                        passphrase = (account.type as AccountType.Mnemonic).passphrase
                    )
                    seed = null
                    passphrase = null
                } else {
                    words = (account.type as AccountType.Mnemonic).words
                    seed = (account.type as AccountType.Mnemonic).seed
                    passphrase = (account.type as AccountType.Mnemonic).passphrase
                }
                wordsNumbered = words.mapIndexed { index, word ->
                    RecoveryPhraseModule.WordNumbered(word, index + 1)
                }
            }

            is AccountType.MnemonicMonero -> {
                words = (account.type as AccountType.MnemonicMonero).words
                wordsNumbered = words.mapIndexed { index, word ->
                    RecoveryPhraseModule.WordNumbered(word, index + 1)
                }
                seed = null
                passphrase = null
            }

            else -> {
                words = listOf()
                seed = null
            }
        }
    }
}
