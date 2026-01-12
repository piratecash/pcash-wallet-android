package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.factories.TransferEventFactory
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.storage.SpamAddressStorage
import cash.p.terminal.entities.SpamAddress
import cash.p.terminal.entities.SpamScanState
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.ethereumkit.core.hexStringToByteArrayOrNull
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal

class SpamManager(
    private val localStorage: ILocalStorage,
    private val spamAddressStorage: SpamAddressStorage,
    private val transactionAdapterManager: TransactionAdapterManager,

) {
    private val transferEventFactory = TransferEventFactory()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        coroutineScope.launch {
            transactionAdapterManager.adaptersReadyFlow.collect {
                subscribeToAdapters(transactionAdapterManager)
            }
        }
    }

    var hideSuspiciousTx = localStorage.hideSuspiciousTransactions
        private set

    private fun subscribeToAdapters(transactionAdapterManager: TransactionAdapterManager) {
        transactionAdapterManager.adaptersReadyFlow.value.forEach { (transactionSource, transactionsAdapter) ->
            subscribeToAdapter(transactionSource, transactionsAdapter)
        }
    }

    private fun subscribeToAdapter(source: TransactionSource, adapter: ITransactionsAdapter) {
        coroutineScope.launch {
            adapter.transactionsStateUpdatedFlowable.asFlow().collect {
                sync(source)
            }
        }
    }

    fun updateFilterHideSuspiciousTx(hide: Boolean) {
        localStorage.hideSuspiciousTransactions = hide
        hideSuspiciousTx = hide
    }

    fun find(address: String): SpamAddress? {
        return spamAddressStorage.findByAddress(address)
    }

    companion object {

        private fun handleSpamAddresses(events: List<TransferEvent>): List<String> {
            val spamTokenSenders = mutableListOf<String>()
            val nativeSenders = mutableListOf<String>()
            var totalNativeTransactionValue: TransactionValue? = null

            events.forEach { event ->
                if (event.value is TransactionValue.CoinValue && event.value.token.type == TokenType.Native) {
                    val totalNativeValue =
                        totalNativeTransactionValue?.decimalValue ?: BigDecimal.ZERO
                    totalNativeTransactionValue = TransactionValue.CoinValue(
                        event.value.token,
                        event.value.value + totalNativeValue
                    )
                    event.address?.let { nativeSenders.add(it) }
                } else {
                    if (event.address != null && isSpam(event.value)) {
                        spamTokenSenders.add(event.address)
                    }
                }
            }

            if (totalNativeTransactionValue != null && isSpam(totalNativeTransactionValue!!) && nativeSenders.isNotEmpty()) {
                spamTokenSenders.addAll(nativeSenders)
            }

            return spamTokenSenders
        }

        private fun isSpam(transactionValue: TransactionValue): Boolean {
            val spamCoinLimits = AppConfigProvider.spamCoinValueLimits
            val value = transactionValue.decimalValue?.abs()

            var limit: BigDecimal = BigDecimal.ZERO
            when (transactionValue) {
                is TransactionValue.CoinValue -> {
                    limit = spamCoinLimits[transactionValue.coinCode] ?: BigDecimal.ZERO
                }

                is TransactionValue.JettonValue -> {
                    limit = spamCoinLimits[transactionValue.coinCode] ?: BigDecimal.ZERO
                }

                is TransactionValue.NftValue -> {
                    if (transactionValue.value > BigDecimal.ZERO)
                        return false
                }

                is TransactionValue.RawValue,
                is TransactionValue.TokenValue -> {
                    return true
                }
            }

            return limit > value
        }

        fun isSpam(events: List<TransferEvent>): Boolean {
            return handleSpamAddresses(events).isNotEmpty()
        }
    }

    private suspend fun sync(source: TransactionSource) {
        withContext(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Timber.d("SpamManager sync error: $e")
        }) {
            val adapter = transactionAdapterManager?.getAdapter(source) ?: run {
                return@withContext
            }
            val spamScanState =
                spamAddressStorage.getSpamScanState(source.blockchain.type, source.account.id)
            val transactions = adapter.getTransactionsAfter(spamScanState?.lastSyncedTransactionId)
            val lastSyncedTransactionId = handle(transactions, source)
            lastSyncedTransactionId?.let {
                spamAddressStorage.save(
                    SpamScanState(
                        source.blockchain.type,
                        source.account.id,
                        lastSyncedTransactionId
                    )
                )
            }
        }
    }

    private fun handle(transactions: List<TransactionRecord>, source: TransactionSource): String? {
        val txWithEvents =
            transactions.map { Pair(it.transactionHash, transferEventFactory.transferEvents(it)) }

        val spamAddresses = mutableListOf<SpamAddress>()

        txWithEvents.forEach { (hash, events) ->
            val hashByteArray = hash.hexStringToByteArrayOrNull() ?: return@forEach
            if (events.isEmpty()) return@forEach

            val result = handleSpamAddresses(events)
            if (result.isNotEmpty()) {
                result.forEach { address ->
                    spamAddresses.add(
                        SpamAddress(
                            hashByteArray,
                            address,
                            null,
                            source.blockchain.type
                        )
                    )
                }
            }
        }

        try {
            spamAddressStorage.save(spamAddresses)
        } catch (_: Throwable) {
        }

        val sortedTransactions = transactions.sortedWith(
            compareBy<TransactionRecord> { it.timestamp }
                .thenBy { it.transactionIndex }
                .thenBy { it.transactionHash }
        )

        return sortedTransactions.lastOrNull()?.transactionHash
    }
}
