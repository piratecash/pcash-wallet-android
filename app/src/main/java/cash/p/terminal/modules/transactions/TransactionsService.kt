package cash.p.terminal.modules.transactions

import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.nft.NftAssetBriefMetadata
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.nftUids
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.CurrencyValue
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class TransactionsService(
    private val rateRepository: TransactionsRateRepository,
    private val transactionSyncStateRepository: TransactionSyncStateRepository,
    private val contactsRepository: ContactsRepository,
    private val nftMetadataService: NftMetadataService,
    private val spamManager: SpamManager,
) : Clearable {

    private val transactionRecordRepository: ITransactionRecordRepository by inject(
        ITransactionRecordRepository::class.java
    )
    private val itemsSubject = BehaviorSubject.create<List<TransactionItem>>()
    val itemsObservable: Observable<List<TransactionItem>> get() = itemsSubject

    val syncingObservable get() = transactionSyncStateRepository.syncingObservable

    private val transactionItems = CopyOnWriteArrayList<TransactionItem>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun start() {
        coroutineScope.launch {
            transactionRecordRepository.itemsObservable.asFlow().collect {
                handleUpdatedRecords(it)
            }
        }
        coroutineScope.launch {
            rateRepository.dataExpiredObservable.asFlow().collect {
                handleUpdatedHistoricalRates()
            }
        }
        coroutineScope.launch {
            rateRepository.historicalRateObservable.asFlow().collect {
                handleUpdatedHistoricalRate(it.first, it.second)
            }
        }
        coroutineScope.launch {
            transactionSyncStateRepository.lastBlockInfoObservable.asFlow()
                .collect { (source, lastBlockInfo) ->
                    handleLastBlockInfo(source, lastBlockInfo)
                }
        }
        coroutineScope.launch {
            nftMetadataService.assetsBriefMetadataFlow.collect {
                handle(it)
            }
        }
        coroutineScope.launch {
            contactsRepository.contactsFlow.collect {
                handleContactsUpdate()
            }
        }
    }

    fun set(
        transactionWallets: List<TransactionWallet>,
        transactionWallet: TransactionWallet?,
        filterTransactionType: FilterTransactionType,
        blockchain: Blockchain?,
        contact: Contact?
    ) {
        transactionRecordRepository.set(
            transactionWallets,
            transactionWallet,
            filterTransactionType,
            blockchain,
            contact
        )

        transactionSyncStateRepository.setTransactionWallets(transactionWallets)
    }

    @Synchronized
    private fun handleContactsUpdate() {
        val tmpList = mutableListOf<TransactionItem>()
        transactionItems.forEach {
            tmpList.add(it.copy())
        }

        transactionItems.clear()
        transactionItems.addAll(tmpList)

        itemsSubject.onNext(transactionItems)
    }

    @Synchronized
    private fun handle(assetBriefMetadataMap: Map<NftUid, NftAssetBriefMetadata>) {
        var updated = false
        transactionItems.forEachIndexed { index, item ->
            val tmpMetadata = item.nftMetadata.toMutableMap()
            item.record.nftUids.forEach { nftUid ->
                assetBriefMetadataMap[nftUid]?.let {
                    tmpMetadata[nftUid] = it
                }
            }
            transactionItems[index] = item.copy(nftMetadata = tmpMetadata)
            updated = true
        }

        if (updated) {
            itemsSubject.onNext(transactionItems)
        }
    }

    @Synchronized
    private fun handleLastBlockInfo(source: TransactionSource, lastBlockInfo: LastBlockInfo) {
        var updated = false
        transactionItems.forEachIndexed { index, item ->
            if (item.record.source == source && item.record.changedBy(
                    item.lastBlockInfo,
                    lastBlockInfo
                )
            ) {
                transactionItems[index] = item.copy(lastBlockInfo = lastBlockInfo)
                updated = true
            }
        }

        if (updated) {
            itemsSubject.onNext(transactionItems)
        }
    }

    @Synchronized
    private fun handleUpdatedHistoricalRate(key: HistoricalRateKey, rate: CurrencyValue) {
        var updated = false
        for (i in 0 until transactionItems.size) {
            val item = transactionItems[i]

            item.record.mainValue?.let { mainValue ->
                mainValue.decimalValue?.let { decimalValue ->
                    if (mainValue.coin?.uid == key.coinUid && item.record.timestamp == key.timestamp) {
                        val currencyValue = CurrencyValue(rate.currency, decimalValue * rate.value)

                        transactionItems[i] = item.copy(currencyValue = currencyValue)
                        updated = true
                    }
                }
            }
        }

        if (updated) {
            itemsSubject.onNext(transactionItems)
        }
    }

    @Synchronized
    private fun handleUpdatedHistoricalRates() {
        for (i in 0 until transactionItems.size) {
            val item = transactionItems[i]
            val currencyValue = getCurrencyValue(item.record)

            transactionItems[i] = item.copy(currencyValue = currencyValue)
        }

        itemsSubject.onNext(transactionItems)
    }

    private suspend fun handleUpdatedRecords(transactionRecords: List<TransactionRecord>) {
        val tmpList = mutableListOf<TransactionItem>()

        val nftUids = transactionRecords.nftUids
        val nftMetadata = nftMetadataService.assetsBriefMetadata(nftUids)

        val missingNftUids = nftUids.subtract(nftMetadata.keys)
        if (missingNftUids.isNotEmpty()) {
            coroutineScope.launch {
                nftMetadataService.fetch(missingNftUids)
            }
        }

        val newRecords = mutableListOf<TransactionRecord>()
        transactionRecords.forEach { record ->
            var transactionItem = transactionItems.find { it.record == record }
            if (transactionItem == null) {
                newRecords.add(record)
            }

            if (record.spam && spamManager.hideSuspiciousTx) return@forEach

            transactionItem = if (transactionItem == null) {
                val lastBlockInfo = transactionSyncStateRepository.getLastBlockInfo(record.source)
                val currencyValue = getCurrencyValue(record)

                TransactionItem(record, currencyValue, lastBlockInfo, nftMetadata)
            } else {
                transactionItem.copy(record = record)
            }

            tmpList.add(transactionItem)
        }

        if (newRecords.isNotEmpty() && newRecords.all { it.spam }) {
            loadNext()
        }

        transactionItems.clear()
        transactionItems.addAll(tmpList)
        itemsSubject.onNext(transactionItems)
    }

    private fun getCurrencyValue(record: TransactionRecord): CurrencyValue? {
        val decimalValue = record.mainValue?.decimalValue ?: return null
        val coinUid = record.mainValue?.coin?.uid ?: return null

        return rateRepository.getHistoricalRate(HistoricalRateKey(coinUid, record.timestamp))
            ?.let { rate -> CurrencyValue(rate.currency, decimalValue * rate.value) }
    }

    override fun clear() {
        transactionRecordRepository.clear()
        rateRepository.clear()
        transactionSyncStateRepository.clear()
        coroutineScope.cancel()
    }

    private val executorService = Executors.newCachedThreadPool()

    fun refreshList() {
        itemsSubject.onNext(transactionItems)
    }

    fun reload() {
        executorService.submit {
            transactionRecordRepository.reload()
        }
    }

    fun loadNext() {
        executorService.submit {
            transactionRecordRepository.loadNext()
        }
    }

    fun fetchRateIfNeeded(recordUid: String) {
        executorService.submit {
            transactionItems.find { it.record.uid == recordUid }?.let { transactionItem ->
                if (transactionItem.currencyValue == null) {
                    transactionItem.record.mainValue?.coin?.uid?.let { coinUid ->
                        rateRepository.fetchHistoricalRate(
                            HistoricalRateKey(
                                coinUid,
                                transactionItem.record.timestamp
                            )
                        )
                    }
                }
            }
        }
    }

    fun getTransactionItem(recordUid: String): TransactionItem? {
        return transactionItems.find { it.record.uid == recordUid }
    }
}
