package cash.p.terminal.modules.transactionInfo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.wallet.transaction.TransactionSource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class TransactionInfoViewModel(
    private val service: TransactionInfoService,
    private val factory: TransactionInfoViewItemFactory,
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    private val balanceHiddenManager: BalanceHiddenManager by inject(BalanceHiddenManager::class.java)
    val balanceHidden: Boolean
        get() = balanceHiddenManager.balanceHidden

    val source: TransactionSource by service::source
    val transactionRecord by service::transactionRecord

    var viewItems by mutableStateOf<List<List<TransactionInfoViewItem>>>(listOf())
        private set

    init {
        viewModelScope.launch {
            combine(
                contactsRepository.contactsFlow,
                service.transactionInfoItemFlow
            ) { _, transactionInfoItem ->
                factory.getViewItemSections(transactionInfoItem)
            }.collect { items ->
                viewItems = items
            }
        }

        viewModelScope.launch {
            service.start()
        }
    }

    fun getRawTransaction(): String? = service.getRawTransaction()

    fun toggleBalanceVisibility() {
        balanceHiddenManager.toggleBalanceHidden()
    }
}
