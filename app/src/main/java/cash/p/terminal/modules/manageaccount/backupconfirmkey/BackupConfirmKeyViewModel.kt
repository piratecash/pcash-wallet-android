package cash.p.terminal.modules.manageaccount.backupconfirmkey

import cash.p.terminal.R
import cash.p.terminal.core.IRandomProvider
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager

class BackupConfirmKeyViewModel(
    private val account: Account,
    private val accountManager: IAccountManager,
    private val randomProvider: IRandomProvider
) : ViewModelUiState<BackupConfirmUiState>() {

    private val wordsIndexed: List<Pair<Int, String>>
    private var hiddenWordItems = listOf<HiddenWordItem>()
    private var wordOptions = listOf<WordOption>()
    private var currentHiddenWordItemIndex = -1
    private var confirmed = false
    private var error: Throwable? = null

    init {
        if (account.type is AccountType.Mnemonic) {
            wordsIndexed = (account.type as AccountType.Mnemonic).words.mapIndexed { index, s ->
                Pair(index, s)
            }

            reset()
            emitState()
        } else if (account.type is AccountType.MnemonicMonero) {
            wordsIndexed = (account.type as AccountType.MnemonicMonero).words.mapIndexed { index, s ->
                Pair(index, s)
            }

            reset()
            emitState()
        } else {
            wordsIndexed = listOf()
        }
    }

    override fun createState() = BackupConfirmUiState(
        hiddenWordItems = hiddenWordItems,
        wordOptions = wordOptions,
        currentHiddenWordItemIndex = currentHiddenWordItemIndex,
        confirmed = confirmed,
        error = error
    )

    private fun reset() {
        val wordsCountToGuess = when (wordsIndexed.size) {
            12 -> 2
            15, 18, 21 -> 3
            25,
            24 -> 4
            else -> 2
        }

        val shuffled = wordsIndexed.shuffled().take(12)
        val randomNumbers = randomProvider.getRandomNumbers(wordsCountToGuess, shuffled.size)

        hiddenWordItems = randomNumbers.map { number ->
            val wordIndexed = shuffled[number]
            HiddenWordItem(
                index = wordIndexed.first,
                word = wordIndexed.second,
                isRevealed = false
            )
        }
        wordOptions = shuffled.map {
            WordOption(it.second, true)
        }
        currentHiddenWordItemIndex = 0
    }

    fun onSelectWord(wordOption: WordOption) {
        val hiddenWordItem = hiddenWordItems[currentHiddenWordItemIndex]
        if (hiddenWordItem.word != wordOption.word) {
            reset()
            error = Exception(cash.p.terminal.strings.helpers.Translator.getString(R.string.BackupConfirmKey_Error_InvalidWord))
        } else {
            hiddenWordItems = hiddenWordItems.toMutableList().apply {
                set(currentHiddenWordItemIndex, hiddenWordItem.copy(isRevealed = true))
            }

            val indexOfWordOption = wordOptions.indexOf(wordOption)
            wordOptions = wordOptions.toMutableList().apply {
                set(indexOfWordOption, wordOption.copy(enabled = false))
            }

            if (currentHiddenWordItemIndex != hiddenWordItems.lastIndex) {
                currentHiddenWordItemIndex++
            } else {
                accountManager.update(account.copy(isBackedUp = true))
                confirmed = true
            }
        }

        emitState()
    }

    fun onErrorShown() {
        error = null
        emitState()
    }
}

data class BackupConfirmUiState(
    val hiddenWordItems: List<HiddenWordItem>,
    val wordOptions: List<WordOption>,
    val currentHiddenWordItemIndex: Int,
    val confirmed: Boolean,
    val error: Throwable?
)

data class WordOption(val word: String, val enabled: Boolean)

data class HiddenWordItem(
    val index: Int,
    val word: String,
    val isRevealed: Boolean
) {
    override fun toString() = when {
        isRevealed -> "${index + 1}. $word"
        else -> "${index + 1}."
    }
}
