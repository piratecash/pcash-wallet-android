package cash.p.terminal.modules.createaccount.passphraseterms

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.ui_compose.entities.TermItem
import io.horizontalsystems.core.ViewModelUiState

class PassphraseTermsViewModel(
    termTitles: Array<String>,
    private val localStorage: ILocalStorage
) : ViewModelUiState<PassphraseTermsUiState>() {

    private val alreadyAgreed = localStorage.passphraseTermsAgreed

    private var terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = alreadyAgreed
        )
    }.toMutableList()

    override fun createState() = PassphraseTermsUiState(
        terms = terms.toList(),
        agreeEnabled = terms.all { it.checked },
        alreadyAgreed = alreadyAgreed
    )

    fun toggleCheckbox(id: Int) {
        if (alreadyAgreed) return

        val index = terms.indexOfFirst { it.id == id }
        if (index != -1) {
            val term = terms[index]
            terms[index] = term.copy(checked = !term.checked)
            emitState()
        }
    }

    fun agree() {
        localStorage.passphraseTermsAgreed = true
    }
}

data class PassphraseTermsUiState(
    val terms: List<TermItem>,
    val agreeEnabled: Boolean,
    val alreadyAgreed: Boolean
)
