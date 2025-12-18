package cash.p.terminal.modules.transactionInfo

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.PendingTransactionMatcher
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase
import cash.p.terminal.entities.nft.NftAssetBriefMetadata
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.monero.MoneroTransactionRecord
import cash.p.terminal.entities.transactionrecords.nftUids
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.NftMetadataService
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.modules.transactions.toUniversalStatus
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

class TransactionInfoService(
    initialTransactionRecord: TransactionRecord,
    private val userSwapTransactionId: String?,
    private val adapter: ITransactionsAdapter,
    private val marketKit: MarketKitWrapper,
    private val currencyManager: CurrencyManager,
    private val nftMetadataService: NftMetadataService,
    private val updateSwapProviderTransactionsStatusUseCase: UpdateSwapProviderTransactionsStatusUseCase,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    transactionStatusUrl: Pair<String, String>?
) {
    private val balanceHiddenManager: BalanceHiddenManager by inject(BalanceHiddenManager::class.java)
    private val pendingTransactionMatcher: PendingTransactionMatcher by inject(
        PendingTransactionMatcher::class.java
    )

    private val mutex = Mutex()

    private var _transactionRecord = initialTransactionRecord
    val transactionRecord: TransactionRecord get() = _transactionRecord

    val transactionHash: String get() = transactionRecord.transactionHash
    val source: TransactionSource get() = transactionRecord.source

    private val _transactionInfoItemFlow = MutableStateFlow<TransactionInfoItem?>(null)
    val transactionInfoItemFlow = _transactionInfoItemFlow.filterNotNull()

    // Fetch swap provider transaction data
    private val swapTransaction = userSwapTransactionId?.let {
        swapProviderTransactionsStorage.getTransaction(it)
    }

    private fun getCoinCode(coinUid: String): String? {
        return marketKit.allCoins().find { it.uid == coinUid }?.code
    }

    var transactionInfoItem = TransactionInfoItem(
        record = transactionRecord,
        externalStatus = TransactionStatus.Pending,
        lastBlockInfo = adapter.lastBlockInfo,
        explorerData = TransactionInfoModule.ExplorerData(
            adapter.explorerTitle,
            adapter.getTransactionUrl(transactionRecord.transactionHash)
        ),
        rates = mapOf(),
        nftMetadata = mapOf(),
        hideAmount = balanceHiddenManager.balanceHidden,
        transactionStatusUrl = transactionStatusUrl,
        swapAmountOut = swapTransaction?.amountOut,
        swapAmountOutReal = swapTransaction?.amountOutReal,
        swapAmountIn = swapTransaction?.amountIn,
        swapCoinCodeOut = swapTransaction?.coinUidOut?.let { getCoinCode(it) },
        swapCoinCodeIn = swapTransaction?.coinUidIn?.let { getCoinCode(it) }
    )
        private set(value) {
            field = value
            _transactionInfoItemFlow.update { value }
        }

    private val coinUidsForRates: List<String>
        get() {
            val coinUids = mutableListOf<String?>()

            val txCoinTypes = when (val tx = transactionRecord) {
                is StellarTransactionRecord -> listOf(tx.mainValue?.coinUid, tx.fee?.coinUid)
                is TonTransactionRecord -> buildList {
                    add(tx.mainValue?.coinUid)
                    add(tx.fee.coinUid)

                    tx.actions.forEach { action ->
                        when (val actionType = action.type) {
                            is TonTransactionRecord.Action.Type.Burn -> {
                                add(actionType.value.coinUid)
                            }

                            is TonTransactionRecord.Action.Type.ContractCall -> {
                                add(actionType.value.coinUid)
                            }

                            is TonTransactionRecord.Action.Type.Mint -> {
                                add(actionType.value.coinUid)
                            }

                            is TonTransactionRecord.Action.Type.Receive -> {
                                add(actionType.value.coinUid)
                            }

                            is TonTransactionRecord.Action.Type.Send -> {
                                add(actionType.value.coinUid)
                            }

                            is TonTransactionRecord.Action.Type.Swap -> {
                                add(actionType.valueIn.coinUid)
                                add(actionType.valueOut.coinUid)
                            }

                            is TonTransactionRecord.Action.Type.ContractDeploy,
                            is TonTransactionRecord.Action.Type.Unsupported -> Unit
                        }
                    }
                }

                is EvmTransactionRecord -> {
                    when (transactionRecord.transactionRecordType) {
                        TransactionRecordType.EVM_INCOMING -> {
                            listOf(tx.value!!.coinUid)
                        }

                        TransactionRecordType.EVM_APPROVE,
                        TransactionRecordType.EVM_OUTGOING -> {
                            listOf(tx.fee?.coinUid, tx.value!!.coinUid)
                        }

                        TransactionRecordType.EVM_UNKNOWN_SWAP,
                        TransactionRecordType.EVM_SWAP -> {
                            listOf(
                                tx.fee,
                                tx.valueIn,
                                tx.valueOut
                            ).map { it?.coinUid }
                        }

                        TransactionRecordType.EVM_CONTRACT_CALL,
                        TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL -> {
                            val tempCoinUidList = mutableListOf<String>()
                            tempCoinUidList.addAll(tx.incomingEvents!!.map { it.value.coinUid })
                            tempCoinUidList.addAll(tx.outgoingEvents!!.map { it.value.coinUid })
                            tempCoinUidList
                        }

                        else -> emptyList()
                    }
                }

                is BitcoinTransactionRecord -> {
                    when (transactionRecord.transactionRecordType) {
                        TransactionRecordType.BITCOIN_INCOMING -> {
                            listOf(tx.mainValue.coinUid)
                        }

                        TransactionRecordType.BITCOIN_OUTGOING -> {
                            listOf(
                                tx.fee,
                                tx.mainValue
                            ).map { it?.coinUid }
                        }

                        else -> emptyList()
                    }
                }

                is MoneroTransactionRecord -> {
                    when (transactionRecord.transactionRecordType) {
                        TransactionRecordType.MONERO_INCOMING -> {
                            listOf(tx.mainValue.coinUid)
                        }

                        TransactionRecordType.MONERO_OUTGOING -> {
                            listOf(
                                tx.fee,
                                tx.mainValue
                            ).map { it.coinUid }
                        }

                        else -> emptyList()
                    }
                }

                is SolanaTransactionRecord -> {
                    when (transactionRecord.transactionRecordType) {
                        TransactionRecordType.SOLANA_INCOMING -> {
                            listOf(tx.mainValue?.coinUid)
                        }

                        TransactionRecordType.SOLANA_OUTGOING -> {
                            listOf(tx.fee?.coinUid, tx.mainValue?.coinUid)
                        }

                        TransactionRecordType.SOLANA_UNKNOWN -> {
                            val tempCoinUidList = mutableListOf<String>()
                            tx.incomingSolanaTransfers?.map { it.value.coinUid }
                                ?.let { tempCoinUidList.addAll(it) }
                            tx.outgoingSolanaTransfers?.map { it.value.coinUid }
                                ?.let { tempCoinUidList.addAll(it) }
                            tempCoinUidList
                        }

                        else -> emptyList()
                    }
                }

                is TronTransactionRecord -> {
                    when (transactionRecord.transactionRecordType) {
                        TransactionRecordType.TRON_APPROVE,
                        TransactionRecordType.TRON_OUTGOING ->
                            listOf(tx.value?.coinUid, tx.fee?.coinUid)

                        TransactionRecordType.TRON_INCOMING ->
                            listOf(tx.value?.coinUid)

                        TransactionRecordType.TRON_CONTRACT_CALL,
                        TransactionRecordType.TRON_EXTERNAL_CONTRACT_CALL -> {
                            val tempCoinUidList = mutableListOf<String>()
                            tempCoinUidList.addAll(tx.incomingEvents!!.map { it.value.coinUid })
                            tempCoinUidList.addAll(tx.outgoingEvents!!.map { it.value.coinUid })
                            tempCoinUidList
                        }

                        else -> emptyList()
                    }
                }

                is PendingTransactionRecord -> {
                    listOf(tx.mainValue.coinUid)
                }

                else -> emptyList()
            }

            (transactionRecord as? EvmTransactionRecord)?.let { transactionRecord ->
                if (!transactionRecord.foreignTransaction) {
                    coinUids.add(transactionRecord.fee?.coinUid)
                }
            }

            (transactionRecord as? TronTransactionRecord)?.let { transactionRecord ->
                if (!transactionRecord.foreignTransaction) {
                    coinUids.add(transactionRecord.fee?.coinUid)
                }
            }

            coinUids.addAll(txCoinTypes)

            return coinUids.filterNotNull().filter { it.isNotBlank() }.distinct()
        }

    suspend fun updateRecord(newRecord: TransactionRecord) {
        _transactionRecord = newRecord
        handleRecordUpdate(newRecord)
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        handleLastBlockUpdate(getUserSwapTransactionStatus())
        _transactionInfoItemFlow.update { transactionInfoItem }

        launch {
            adapter.getTransactionRecordsFlow(null, FilterTransactionType.All, null)
                .collect { transactionRecords ->
                    val record = transactionRecords.find { it == transactionRecord }

                    if (record != null) {
                        handleRecordUpdate(record)
                    }

                    if (_transactionRecord is PendingTransactionRecord) {
                        val matchedReal = findMatchingRealTransaction(
                            pending = _transactionRecord as PendingTransactionRecord,
                            allRecords = transactionRecords
                        )

                        if (matchedReal != null) {
                            updateRecord(matchedReal)
                        }
                    }
                }
        }

        launch {
            adapter.lastBlockUpdatedFlowable.asFlow()
                .collect {
                    handleLastBlockUpdate(getUserSwapTransactionStatus())
                }
        }

        launch {
            nftMetadataService.assetsBriefMetadataFlow.collect {
                handleNftMetadata(it)
            }
        }

        launch {
            balanceHiddenManager.balanceHiddenFlow.collect {
                mutex.withLock {
                    transactionInfoItem = transactionInfoItem.copy(hideAmount = it)
                }
            }
        }

        fetchRates()
        fetchNftMetadata()
    }

    private fun findMatchingRealTransaction(
        pending: PendingTransactionRecord,
        allRecords: List<TransactionRecord>
    ): TransactionRecord? {
        return allRecords.firstOrNull { real ->
            real !is PendingTransactionRecord &&
                    pendingTransactionMatcher.calculateMatchScore(pending, real).isMatch
        }
    }


    private suspend fun getUserSwapTransactionStatus(): TransactionStatus? =
        userSwapTransactionId?.let { userSwapTransactionId ->
            updateSwapProviderTransactionsStatusUseCase.updateTransactionStatus(userSwapTransactionId)
                .toUniversalStatus()
        }

    private suspend fun fetchNftMetadata() {
        val nftUids = transactionRecord.nftUids
        val assetsBriefMetadata = nftMetadataService.assetsBriefMetadata(nftUids)

        handleNftMetadata(assetsBriefMetadata)

        if (nftUids.subtract(assetsBriefMetadata.keys).isNotEmpty()) {
            nftMetadataService.fetch(nftUids)
        }
    }

    private suspend fun fetchRates() = withContext(Dispatchers.IO) {
        val coinUids = coinUidsForRates
        val timestamp = transactionRecord.timestamp

        val rates = coinUids.mapNotNull { coinUid ->
            try {
                marketKit
                    .coinHistoricalPriceSingle(
                        coinUid,
                        currencyManager.baseCurrency.code,
                        timestamp
                    ).takeIf { it != BigDecimal.ZERO }?.let {
                        Pair(coinUid, CurrencyValue(currencyManager.baseCurrency, it))
                    }
            } catch (error: Exception) {
                null
            }
        }.toMap()

        handleRates(rates)
    }

    private suspend fun handleLastBlockUpdate(externalStatus: TransactionStatus?) {
        mutex.withLock {
            transactionInfoItem = transactionInfoItem.copy(
                lastBlockInfo = adapter.lastBlockInfo,
                externalStatus = externalStatus
            )
        }
    }

    private suspend fun handleRecordUpdate(transactionRecord: TransactionRecord) {
        mutex.withLock {
            transactionInfoItem = transactionInfoItem.copy(record = transactionRecord)
        }
    }

    private suspend fun handleRates(rates: Map<String, CurrencyValue>) {
        mutex.withLock {
            transactionInfoItem = transactionInfoItem.copy(rates = rates)
        }
    }

    private suspend fun handleNftMetadata(nftMetadata: Map<NftUid, NftAssetBriefMetadata>) {
        mutex.withLock {
            transactionInfoItem = transactionInfoItem.copy(nftMetadata = nftMetadata)
        }
    }

    fun getRawTransaction(): String? {
        return adapter.getRawTransaction(transactionRecord.transactionHash)
    }

}