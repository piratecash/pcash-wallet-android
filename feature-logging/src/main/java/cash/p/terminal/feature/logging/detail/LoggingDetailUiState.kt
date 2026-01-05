package cash.p.terminal.feature.logging.detail

import cash.p.terminal.feature.logging.history.LoginRecordViewItem

data class LoggingDetailUiState(
    val records: List<LoginRecordViewItem> = emptyList(),
    val selectedId: Long = 0L,
    val closeScreen: Boolean = false
) {
    val currentRecord: LoginRecordViewItem?
        get() = records.find { it.id == selectedId }
}
