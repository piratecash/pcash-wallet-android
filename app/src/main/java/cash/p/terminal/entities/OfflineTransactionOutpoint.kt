package cash.p.terminal.entities

import kotlinx.serialization.Serializable

@Serializable
data class OfflineTransactionOutpoint(
    val transactionHash: String,
    val outputIndex: Long,
)
