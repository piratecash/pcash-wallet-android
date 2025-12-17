package cash.p.terminal.network.swaprepository

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import java.math.BigDecimal
import java.time.Instant

fun String.parseIsoTimestamp(): Long? = try {
    Instant.parse(this).toEpochMilli()
} catch (e: Exception) {
    null
}

data class SwapProviderTransactionStatusResult(
    val status: TransactionStatusEnum,
    val amountOutReal: BigDecimal? = null,
    val finishedAt: Long? = null
)

interface SwapProviderTransactionStatusRepository {
    suspend fun getTransactionStatus(
        transactionId: String,
        destinationAddress: String,
    ): SwapProviderTransactionStatusResult
}