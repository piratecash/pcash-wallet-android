package cash.p.terminal.network.swaprepository

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum

interface SwapProviderTransactionStatusRepository {
    suspend fun getTransactionStatus(
        transactionId: String,
        destinationAddress: String,
    ): TransactionStatusEnum
}