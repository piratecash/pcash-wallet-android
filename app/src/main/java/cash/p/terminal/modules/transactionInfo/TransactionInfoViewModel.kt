package cash.p.terminal.modules.transactionInfo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.transaction.TransactionSource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class TransactionInfoViewModel(
    private val service: TransactionInfoService,
    private val factory: TransactionInfoViewItemFactory,
    private val contactsRepository: ContactsRepository,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val pendingTransactionRepository: PendingTransactionRepository
) : ViewModel() {

    private val balanceHiddenManager: IBalanceHiddenManager by inject(IBalanceHiddenManager::class.java)
    private val poisonAddressManager: PoisonAddressManager by inject(PoisonAddressManager::class.java)
    val balanceHidden: Boolean
        get() = balanceHiddenManager.isTransactionInfoHidden(service.transactionRecord.uid, service.walletUid)

    val source: TransactionSource by service::source
    val transactionRecord by service::transactionRecord

    var viewItems by mutableStateOf<List<List<TransactionInfoViewItem>>>(listOf())
        private set

    init {
        viewModelScope.launch {
            combine(
                contactsRepository.contactsFlow,
                service.transactionInfoItemFlow,
                poisonAddressManager.poisonDbChangedFlow.onStart { emit(Unit) },
            ) { _, transactionInfoItem, _ ->
                val updatedItem = transactionInfoItem.copy(
                    poisonStatus = service.computePoisonStatus(transactionInfoItem.record)
                )
                factory.getViewItemSections(updatedItem)
            }.collect { items ->
                viewItems = items
            }
        }

        viewModelScope.launch {
            service.start()
        }
    }

    val isPending: Boolean
        get() = transactionRecord is PendingTransactionRecord

    fun deletePendingTransaction() {
        if (!isPending) return
        viewModelScope.launch {
            pendingTransactionRepository.deleteById(transactionRecord.uid)
        }
    }

    fun getRawTransaction(): String? = service.getRawTransaction()

    fun toggleBalanceVisibility() {
        balanceHiddenManager.toggleTransactionInfoHidden(service.transactionRecord.uid)
    }
}
