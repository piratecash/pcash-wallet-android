package cash.p.terminal.modules.manageaccount.recoveryphrase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.wallet.Account

object RecoveryPhraseModule {
    class Factory(
        private val account: Account,
        private val recoveryPhraseType: RecoveryPhraseFragment.RecoveryPhraseType
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecoveryPhraseViewModel(account, recoveryPhraseType) as T
        }
    }

    data class WordNumbered(val word: String, val number: Int)

}
