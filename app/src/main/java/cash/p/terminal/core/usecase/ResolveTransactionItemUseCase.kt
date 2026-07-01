package cash.p.terminal.core.usecase

import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.TransactionItem
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ResolveTransactionItemUseCase(
    private val transactionAdapterManager: TransactionAdapterManager,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend operator fun invoke(
        recordUid: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): TransactionItem? = withContext(dispatcherProvider.io) {
        findRecord(recordUid)?.let { return@withContext enrich(recordUid, it) }

        withTimeoutOrNull(timeoutMs) {
            transactionAdapterManager.adaptersReadyFlow.first { findRecord(recordUid) != null }
            findRecord(recordUid)?.let { enrich(recordUid, it) }
        }
    }

    private suspend fun findRecord(recordUid: String): TransactionRecord? {
        for ((_, adapter) in transactionAdapterManager.adaptersReadyFlow.value) {
            val record = tryOrNull {
                adapter.getTransactions(null, null, ADAPTER_LOOKUP_LIMIT, FilterTransactionType.All, null)
            }?.firstOrNull { it.uid == recordUid }
            if (record != null) return record
        }
        return null
    }

    private fun enrich(recordUid: String, record: TransactionRecord): TransactionItem {
        val item = TransactionItem(
            record = record,
            currencyValue = null,
            lastBlockInfo = null,
            nftMetadata = emptyMap(),
        )
        val swapTx = swapProviderTransactionsStorage.getByOutgoingRecordUid(recordUid)
            ?: swapProviderTransactionsStorage.getByIncomingRecordUid(recordUid)
        return if (swapTx != null) {
            item.copy(
                changeNowTransactionId = swapTx.transactionId,
                transactionStatusUrl = swapTx.toStatusUrl(),
            )
        } else {
            item
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        const val ADAPTER_LOOKUP_LIMIT = 100
    }
}
