package cash.p.terminal.ui_compose.entities

data class TermItem(
    val id: Int,
    val title: String,
    val description: String? = null,
    val checked: Boolean,
)