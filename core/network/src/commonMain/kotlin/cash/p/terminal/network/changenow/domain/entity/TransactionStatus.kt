package cash.p.terminal.network.changenow.domain.entity

import java.math.BigDecimal

class TransactionStatus(
    val status: TransactionStatusEnum,
    val payinAddress: String,
    val payoutAddress: String,
    val fromCurrency: String,
    val toCurrency: String,
    val id: String,
    val updatedAt: String,
    val amountReceive: BigDecimal? = null
)

enum class TransactionStatusEnum {
    NEW,
    WAITING,
    CONFIRMING,
    EXCHANGING,
    SENDING,
    FINISHED,
    FAILED,
    REFUNDED,
    VERIFYING,
    CREATED_OR_WAIT_USER,
    UNKNOWN
}

fun String.toStatus() = try {
    TransactionStatusEnum.valueOf(this.uppercase())
} catch (e: IllegalArgumentException) {
    TransactionStatusEnum.UNKNOWN
}
