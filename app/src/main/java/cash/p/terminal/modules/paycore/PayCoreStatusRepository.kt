package cash.p.terminal.modules.paycore

import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusResult
import timber.log.Timber

class PayCoreStatusRepository(
    private val apiService: PayCoreApiService,
    private val storage: SwapProviderTransactionsStorage,
) : SwapProviderTransactionStatusRepository {

    private companion object {
        val DEFAULT_NETWORK_TYPE = PayCoreTicker.USDT_ERC20
    }

    override suspend fun getTransactionStatus(
        transactionId: String,
        destinationAddress: String,
    ): SwapProviderTransactionStatusResult {
        val transaction = storage.getTransaction(transactionId)
            ?: return SwapProviderTransactionStatusResult(status = TransactionStatusEnum.WAITING)

        val isPayment = PayCoreAssets.isRub(transaction.coinUidIn)
        val networkType = resolveNetworkType(transaction, isPayment)

        val response = apiService.getTransactionStatus(transaction.transactionId, networkType)
        return SwapProviderTransactionStatusResult(status = resolveStatus(response))
    }

    private fun resolveNetworkType(
        transaction: SwapProviderTransaction?,
        isPayment: Boolean,
    ): PayCoreTicker {
        val blockchainTypeUid = when {
            transaction == null -> null
            isPayment -> transaction.blockchainTypeOut
            else -> transaction.blockchainTypeIn
        }

        return blockchainTypeUid?.let(PayCoreNetworkMapper::payCoreNetworkTypeFromBlockchainTypeUid)
            ?: DEFAULT_NETWORK_TYPE
    }

    private fun resolveStatus(response: PayCoreTransactionStatusResponse): TransactionStatusEnum {
        return when (val status = response.transactionStatus) {
            "Calculated" -> TransactionStatusEnum.NEW
            "Pending" -> TransactionStatusEnum.WAITING
            "Paid" -> TransactionStatusEnum.EXCHANGING
            "Exchanged" -> TransactionStatusEnum.SENDING
            "Completed" -> TransactionStatusEnum.FINISHED
            "Expired" -> TransactionStatusEnum.FAILED
            null -> TransactionStatusEnum.WAITING
            else -> {
                Timber.w("Unknown PayCore transaction_status: %s — treating as FAILED", status)
                TransactionStatusEnum.FAILED
            }
        }
    }
}
