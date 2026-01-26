package cash.p.terminal.feature.logging.history

data class LoginRecordViewItem(
    val id: Long,
    val photoPath: String?,
    val isSuccessful: Boolean,
    val isDuressMode: Boolean,
    val walletName: String?,
    val formattedTime: String,
    val relativeTime: String
)
