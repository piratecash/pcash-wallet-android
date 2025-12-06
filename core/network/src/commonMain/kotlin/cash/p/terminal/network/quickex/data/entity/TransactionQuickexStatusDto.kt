package cash.p.terminal.network.quickex.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal class TransactionQuickexStatusDto(
    val orderId: Long,
    val createdAt: String,
    val orderEvents: List<OrderEventDto>,
    val completed: Boolean,
)

@Serializable
internal class OrderEventDto(
    val kind: String,
    val createdAt: String
)