package cash.p.terminal.modules.restoreaccount.restoremnemonic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.hdwalletkit.Language

object RestoreMnemonicModule {

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RestoreMnemonicViewModel(
                App.accountFactory,
                App.wordsManager,
                App.thirdKeyboardStorage,
            ) as T
        }
    }

    data class UiState(
        val passphraseEnabled: Boolean,
        val passphraseError: String?,
        val invalidWordRanges: List<IntRange>,
        val error: String?,
        val accountType: cash.p.terminal.wallet.AccountType?,
        val wordSuggestions: WordSuggestions?,
        val language: Language,
    )

    data class WordItem(val word: String, val range: IntRange)
    data class State(val allItems: List<WordItem>, val invalidItems: List<WordItem>)
    data class WordSuggestions(val wordItem: WordItem, val options: List<String>)

}
