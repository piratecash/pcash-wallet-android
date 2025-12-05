package cash.p.terminal.network.quickex.domain.entity

data class TransactionQuickexStatus(
    val orderId: Long,
    val createdAt: String,
    val orderEvents: List<OrderEvent>,
    val completed: Boolean,
)

data class OrderEvent(
    val kind: OrderEventKind?,
    val createdAt: String
)

enum class OrderEventKind {
    WITHDRAWAL_COMPLETED,
    FUNDS_WITHDRAWAL_START,
    DEPOSIT_REGISTERED,
    INCOMING_FUNDS_DETECTED,
    CREATION_END,
    AMLBOT_AML_FROZEN_BY_LIQUIDITY_PROVIDER,
    REFUND_REQUESTED,
    REFUND_COMPLETED,
}
