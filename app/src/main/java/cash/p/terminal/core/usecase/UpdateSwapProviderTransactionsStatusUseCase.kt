package cash.p.terminal.core.usecase

import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.changenow.domain.entity.toStatus
import cash.p.terminal.network.data.EncodedSecrets.getKoin
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import cash.p.terminal.wallet.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.koin.core.qualifier.named
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class UpdateSwapProviderTransactionsStatusUseCase(
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage
) {
    suspend operator fun invoke(
        token: Token,
        address: String
    ): Boolean = withContext(Dispatchers.IO) {
        val changed = AtomicBoolean(false)
        swapProviderTransactionsStorage.getAll(
            token = token,
            address = address,
            statusesExcluded = SwapProviderTransaction.FINISHED_STATUSES,
            limit = 10
        ).map { transaction ->
            async {
                getTransactionStatus(transaction)?.let { status ->
                    if (status.name.lowercase() != transaction.status) {
                        changed.set(true)
                        swapProviderTransactionsStorage.save(
                            transaction.copy(
                                status = status.name.lowercase()
                            )
                        )
                    }
                }
            }
        }.awaitAll()
        changed.get()
    }

    suspend fun updateTransactionStatus(
        transactionId: String
    ): TransactionStatusEnum? = withContext(Dispatchers.IO) {
        swapProviderTransactionsStorage.getTransaction(transactionId)?.let { transaction ->
            if (!transaction.isFinished()) {
                getTransactionStatus(transaction)?.let { status ->
                    if (status.name.lowercase() != transaction.status) {
                        swapProviderTransactionsStorage.save(
                            transaction.copy(
                                status = status.name.lowercase()
                            )
                        )
                    }
                }
            }
            swapProviderTransactionsStorage.getTransaction(transactionId)?.status?.toStatus()
        }
    }

    private suspend fun getTransactionStatus(
        transaction: SwapProviderTransaction
    ): TransactionStatusEnum? = try {
        getSwapProviderTransactionStatusRepository(transaction.provider)
            ?.getTransactionStatus(
                transactionId = transaction.transactionId,
                destinationAddress = transaction.addressOut
            ) ?: run {
            Timber.d("Transaction status repository not found for provider: ${transaction.provider}")
            null
        }
    } catch (e: Throwable) {
        Timber.d("Failed to get transaction status for id: ${transaction.transactionId}")
        e.printStackTrace()
        null
    }

    private fun getSwapProviderTransactionStatusRepository(provider: SwapProvider): SwapProviderTransactionStatusRepository? {
        return try {
            getKoin().get(named(provider))
        } catch (e: Exception) {
            null
        }
    }
}
