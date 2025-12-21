package cash.p.terminal.modules.manageaccount.recoveryphrase

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.utils.MoneroWalletSeedConverter
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecoveryPhraseViewModel(
    account: Account,
    recoveryPhraseType: RecoveryPhraseFragment.RecoveryPhraseType,
    private val seedPhraseQrCrypto: SeedPhraseQrCrypto,
    private val localStorage: ILocalStorage,
    private val restoreSettingsManager: RestoreSettingsManager
) : ViewModel() {

    val safetyRulesAgreed: Boolean
        get() = localStorage.safetyRulesAgreed
    val words: List<String>
    private val seed: ByteArray?
    private val moneroHeight: Long?  // Restore height for Monero accounts
    val passphrase: String?
    val wordsNumbered: List<RecoveryPhraseModule.WordNumbered>

    // Cached encrypted QR content - regenerated on demand
    var encryptedSeedQrContent by mutableStateOf("")
        private set

    // Error state for QR generation failure
    var qrGenerationError by mutableStateOf(false)
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
                    // Get Monero height from restore settings
                    val settings = restoreSettingsManager.settings(account, BlockchainType.Monero)
                    moneroHeight = settings.birthdayHeight
                } else {
                    words = (account.type as AccountType.Mnemonic).words
                    seed = (account.type as AccountType.Mnemonic).seed
                    passphrase = (account.type as AccountType.Mnemonic).passphrase
                    moneroHeight = null
                }
                wordsNumbered = words.mapIndexed { index, word ->
                    RecoveryPhraseModule.WordNumbered(word, index + 1)
                }
            }

            is AccountType.MnemonicMonero -> {
                val moneroType = account.type as AccountType.MnemonicMonero
                words = moneroType.words
                moneroHeight = moneroType.height
                wordsNumbered = words.mapIndexed { index, word ->
                    RecoveryPhraseModule.WordNumbered(word, index + 1)
                }
                seed = null
                passphrase = null
            }

            else -> {
                words = listOf()
                seed = null
                moneroHeight = null
                passphrase = null
                wordsNumbered = listOf()
            }
        }
        // Generate initial encrypted QR content
        regenerateEncryptedQrContent()
    }

    /**
     * Regenerates the encrypted QR content with fresh time-based encryption.
     * Call this when hiding the QR code so next reveal has valid encryption.
     */
    fun regenerateEncryptedQrContent() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val encrypted = seedPhraseQrCrypto.encrypt(words, passphrase ?: "", moneroHeight)
                encryptedSeedQrContent = encrypted
                qrGenerationError = false
            } catch (e: Exception) {
                qrGenerationError = true
            }
        }
    }

    fun onQrGenerationErrorShown() {
        qrGenerationError = false
    }
}
