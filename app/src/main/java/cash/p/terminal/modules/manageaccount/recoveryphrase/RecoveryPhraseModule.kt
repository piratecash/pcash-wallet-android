package cash.p.terminal.modules.manageaccount.recoveryphrase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.wallet.Account
import org.koin.java.KoinJavaComponent.inject

object RecoveryPhraseModule {
    class Factory(
        private val account: Account,
        private val recoveryPhraseType: RecoveryPhraseFragment.RecoveryPhraseType
    ) : ViewModelProvider.Factory {
        private val seedPhraseQrCrypto: SeedPhraseQrCrypto by inject(SeedPhraseQrCrypto::class.java)
        private val localStorage: ILocalStorage by inject(ILocalStorage::class.java)
        private val restoreSettingsManager: RestoreSettingsManager by inject(RestoreSettingsManager::class.java)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecoveryPhraseViewModel(
                account,
                recoveryPhraseType,
                seedPhraseQrCrypto,
                localStorage,
                restoreSettingsManager
            ) as T
        }
    }

    data class WordNumbered(val word: String, val number: Int)

}
