package cash.p.terminal.modules.enablecoin.restoresettings

import cash.p.terminal.ui_compose.components.RestoreHeightMode
import cash.p.terminal.ui_compose.components.isNewWallet
import cash.p.terminal.ui_compose.components.isSelected

data class BirthdayHeightConfigUiState(
    val birthdayHeight: String,
    val mode: RestoreHeightMode? = null,
    val closeWithResult: TokenConfig? = null,
    val errorHeight: String? = null,
) {
    val restoreAsNew: Boolean get() = mode.isNewWallet
    val doneEnabled: Boolean get() = mode.isSelected
}
