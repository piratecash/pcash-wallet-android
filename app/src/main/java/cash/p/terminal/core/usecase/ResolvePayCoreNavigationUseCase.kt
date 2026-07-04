package cash.p.terminal.core.usecase

import cash.p.terminal.modules.transactions.TransactionItem

class ResolvePayCoreNavigationUseCase(
    private val resolveTransactionItem: ResolveTransactionItemUseCase,
) {
    suspend operator fun invoke(date: Long, recordUid: String?): PayCoreNavigationTarget {
        val item = recordUid?.let { resolveTransactionItem(it) }
        return if (item != null) {
            PayCoreNavigationTarget.OpenTransactionInfo(item)
        } else {
            PayCoreNavigationTarget.OpenPayCoreDetail(date)
        }
    }
}

sealed class PayCoreNavigationTarget {
    data class OpenTransactionInfo(val transactionItem: TransactionItem) : PayCoreNavigationTarget()
    data class OpenPayCoreDetail(val date: Long) : PayCoreNavigationTarget()
}
