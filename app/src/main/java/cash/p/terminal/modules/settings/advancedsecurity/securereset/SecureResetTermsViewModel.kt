package cash.p.terminal.modules.settings.advancedsecurity.securereset

import cash.p.terminal.core.IBackupManager
import cash.p.terminal.ui_compose.entities.TermItem
import io.horizontalsystems.core.ViewModelUiState

class SecureResetTermsViewModel(
    termTitles: Array<String>,
    private val backupManager: IBackupManager
) : ViewModelUiState<SecureResetTermsUiState>() {

    private var terms = termTitles.mapIndexed { index, title ->
        TermItem(
            id = index,
            title = title,
            checked = false
        )
    }.toMutableList()

    override fun createState() = SecureResetTermsUiState(
        terms = terms.toList(),
        agreeEnabled = terms.all { it.checked },
        allBackedUp = backupManager.allBackedUp
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

data class SecureResetTermsUiState(
    val terms: List<TermItem>,
    val agreeEnabled: Boolean,
    val allBackedUp: Boolean
)
