package cash.p.terminal.modules.settings.advancedsecurity.terms

import cash.p.terminal.ui_compose.entities.TermItem
import io.horizontalsystems.core.ViewModelUiState

class HiddenWalletTermsViewModel(
    termTitles: Array<String>
) : ViewModelUiState<HiddenWalletTermsUiState>() {

    private var terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = false
        )
    }.toMutableList()

    override fun createState() = HiddenWalletTermsUiState(
        terms = terms.toList(),
        agreeEnabled = terms.all { it.checked }
    )

    fun toggleCheckbox(id: Int) {

        val index = terms.indexOfFirst { it.id == id }
        if (index != -1) {
            val term = terms[index]
            val newChecked = !term.checked
            terms[index] = term.copy(checked = newChecked)

            emitState()
        }
    }
}

data class HiddenWalletTermsUiState(
    val terms: List<TermItem>,
    val agreeEnabled: Boolean
)
