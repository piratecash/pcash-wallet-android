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

data class SwapProviderStatusRequest(
    val transactionId: String,
    val destinationAddress: String,
    val inboundTxHash: String? = null,
)

interface SwapProviderTransactionStatusRepository {
    /** A `null` result means "status unchanged" — the caller must not overwrite the stored status. */
    suspend fun getTransactionStatus(
        request: SwapProviderStatusRequest,
    ): SwapProviderTransactionStatusResult?
}