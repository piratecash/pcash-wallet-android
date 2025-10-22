package cash.p.terminal.modules.settings.advancedsecurity.terms

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.ui_compose.entities.TermItem
import io.horizontalsystems.core.ViewModelUiState

class HiddenWalletTermsViewModel(
    private val localStorage: ILocalStorage,
    termTitles: Array<String>
) : ViewModelUiState<HiddenWalletTermsUiState>() {

    private val acceptedTerms = localStorage.hiddenWalletTermsAccepted.toMutableSet()

    private var terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = acceptedTerms.contains(index.toString())
        )
    }.toMutableList()

    private val allAcceptedBefore = acceptedTerms.size == termTitles.size

    override fun createState() = HiddenWalletTermsUiState(
        terms = terms.toList(),
        agreeEnabled = terms.all { it.checked },
        allAcceptedBefore = allAcceptedBefore
    )

    fun toggleCheckbox(id: Int) {
//        if (allAcceptedBefore) return // we don't allow unchecking if all were accepted before

        val index = terms.indexOfFirst { it.id == id }
        if (index != -1) {
            val term = terms[index]
            val newChecked = !term.checked
            terms[index] = term.copy(checked = newChecked)

            if (newChecked) {
                acceptedTerms.add(id.toString())
            } else {
                acceptedTerms.remove(id.toString())
            }
            localStorage.hiddenWalletTermsAccepted = acceptedTerms

            emitState()
        }
    }

    fun onAgreeClick() {
        
    }
}

data class HiddenWalletTermsUiState(
    val terms: List<TermItem>,
    val agreeEnabled: Boolean,
    val allAcceptedBefore: Boolean
)
