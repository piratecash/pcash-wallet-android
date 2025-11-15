package cash.p.terminal.modules.transactions

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.converters.PendingTransactionConverter
import cash.p.terminal.core.managers.CoinManager
import cash.p.terminal.core.managers.PendingTransactionRepository
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.PendingTransactionEntity
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class TransactionAdapterWrapper(
    private val transactionsAdapter: ITransactionsAdapter,
    val transactionWallet: TransactionWallet,
    private var transactionType: FilterTransactionType,
    private var contact: Contact?,
    private val pendingRepository: PendingTransactionRepository,
    private val pendingConverter: PendingTransactionConverter
) : Clearable {
    // Use MutableSharedFlow for updates
    private val _updatedFlow = MutableSharedFlow<Unit>(replay = 0)
    val updatedFlow: SharedFlow<Unit> get() = _updatedFlow.asSharedFlow()

    // Use StateFlow for transaction records
    private val _transactionRecords = MutableStateFlow<List<TransactionRecord>>(emptyList())
    private val coinManager: CoinManager by inject(CoinManager::class.java)

    // Use StateFlow for allLoaded flag - this is more consistent than MutableSharedFlow
    private val _allLoaded = MutableStateFlow(false)

    // Use SupervisorJob to prevent child failures from cancelling the entire scope
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updatesJob: Job? = null

    val address: String?
        get() = contact
            ?.addresses
            ?.find { it.blockchain == transactionWallet.source.blockchain }
            ?.address

    init {
        subscribeForUpdates()
    }

    fun reload() {
        coroutineScope.launch {
            _transactionRecords.update { emptyList() }
            _allLoaded.value = false
            subscribeForUpdates()
        }
    }

    fun setTransactionType(transactionType: FilterTransactionType) {
        this.transactionType = transactionType
        coroutineScope.launch {
            _transactionRecords.update { emptyList() }
            _allLoaded.value = false
            subscribeForUpdates()
        }
    }

    fun setContact(contact: Contact?) {
        this.contact = contact
        coroutineScope.launch {
            _transactionRecords.update { emptyList() }
            _allLoaded.value = false
            subscribeForUpdates()
        }
    }

    private fun subscribeForUpdates() {
        updatesJob?.cancel()

        if (contact != null && address == null) return

        updatesJob = coroutineScope.launch {
            val walletId = transactionWallet.source.account.id

            combine(
                transactionsAdapter.getTransactionRecordsFlow(
                    transactionWallet.token,
                    transactionType,
                    address
                ).onStart { emit(emptyList()) },
                pendingRepository.getActivePendingFlow(walletId).onStart { emit(emptyList()) }
            ) { _, pendingEntities ->
                // Convert pending entities to records
                getPending(pendingEntities)
            }.collect { _ ->
                _transactionRecords.update { emptyList() }
                _allLoaded.value = false
                _updatedFlow.emit(Unit)
            }
        }
    }

    suspend fun get(limit: Int): List<TransactionRecord> = when {
        _transactionRecords.value.size >= limit || _allLoaded.value -> _transactionRecords.value.take(
            limit
        )

        contact != null && address == null -> emptyList()
        else -> {
            val currentRecords = _transactionRecords.value
            val numberOfRecordsToRequest = limit - currentRecords.size
            val receivedRecords = transactionsAdapter.getTransactions(
                from = currentRecords.lastOrNull(),
                token = transactionWallet.token,
                limit = numberOfRecordsToRequest,
                transactionType = transactionType,
                address = address
            )

            // Use StateFlow's value setter for atomic update
            _allLoaded.value = receivedRecords.size < numberOfRecordsToRequest

            // Merge with pending transactions
            val mergedRecords = mergePendingAndReal(currentRecords + receivedRecords)
            _transactionRecords.value = mergedRecords

            mergedRecords
        }
    }

    private fun getPending(pendingEntities: List<PendingTransactionEntity>): List<TransactionRecord> {
        return if (transactionWallet.token != null) {
            pendingEntities
                .filter { it.coinUid == transactionWallet.token.coin.uid }
                .map { pendingConverter.convert(it, transactionWallet.token) }
        } else {
            pendingEntities
                .mapNotNull {
                    val token = tryOrNull {
                        coinManager.getToken(
                            TokenQuery(
                                BlockchainType.fromUid(it.blockchainTypeUid),
                                TokenType.fromId(it.tokenTypeId!!)!!
                            )
                        )
                    } ?: return@mapNotNull null
                    pendingConverter.convert(it, token)
                }
        }
    }

    private suspend fun mergePendingAndReal(realRecords: List<TransactionRecord>): List<TransactionRecord> {
        val walletId = transactionWallet.source.account.id

        return try {
            val pendingEntities = pendingRepository.getPendingForWallet(walletId)
            val pendingRecords = getPending(pendingEntities)

            // Merge and sort by timestamp descending
            (realRecords + pendingRecords).sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            // If something fails, return real records only
            realRecords
        }
    }

    override fun clear() {
        coroutineScope.cancel()
    }
}