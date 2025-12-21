package cash.p.terminal.modules.manageaccount.safetyrules

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.ui_compose.entities.TermItem
import io.horizontalsystems.core.ViewModelUiState

class SafetyRulesViewModel(
    private val mode: SafetyRulesModule.SafetyRulesMode,
    termTitles: List<String>,
    private val localStorage: ILocalStorage
) : ViewModelUiState<SafetyRulesUiState>() {

    private val alreadyAgreed = localStorage.safetyRulesAgreed

    // In COPY_CONFIRM mode or if already agreed, checkboxes are pre-checked
    private val preChecked = alreadyAgreed

    private var terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = preChecked
        )
    }.toMutableList()

    override fun createState() = SafetyRulesUiState(
        terms = terms.toList(),
        agreeEnabled = terms.all { it.checked },
        mode = mode,
        alreadyAgreed = alreadyAgreed
    )

    fun toggleCheckbox(id: Int) {
        // Only allow toggling if not already agreed and not in COPY_CONFIRM mode
        if (preChecked) return

        val index = terms.indexOfFirst { it.id == id }
        if (index != -1) {
            val term = terms[index]
            terms[index] = term.copy(checked = !term.checked)
            emitState()
        }
    }

    fun agree() {
        localStorage.safetyRulesAgreed = true
    }
}

data class SafetyRulesUiState(
    val terms: List<TermItem>,
    val agreeEnabled: Boolean,
    val mode: SafetyRulesModule.SafetyRulesMode,
    val alreadyAgreed: Boolean
)
