package cash.p.terminal.modules.transactions

import cash.p.terminal.core.converters.PendingTransactionConverter
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.PendingTransactionMatcher
import cash.p.terminal.core.managers.PendingTransactionRepository
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.getShortOutgoingTransactionRecord
import cash.p.terminal.modules.contacts.model.Contact
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class TransactionRecordRepository(
    private val adapterManager: TransactionAdapterManager,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    private val pendingRepository: PendingTransactionRepository,
    private val pendingConverter: PendingTransactionConverter,
    private val pendingTransactionMatcher: PendingTransactionMatcher,
    private val locallyCreatedTransactionRepository: LocallyCreatedTransactionRepository,
    private val dispatcherProvider: DispatcherProvider
) : ITransactionRecordRepository {

    @Volatile
    private var selectedFilterTransactionType: FilterTransactionType = FilterTransactionType.All

    private var selectedWallet: TransactionWallet? = null
    private var selectedBlockchain: Blockchain? = null
    private var contact: Contact? = null
    private var selectedSearchQuery: String? = null
    private val searchMatcher = TransactionRecordSearchMatcher()

    private val _itemsFlow = MutableSharedFlow<RecordsBatch>(extraBufferCapacity = 4)
    override val itemsFlow: SharedFlow<RecordsBatch> = _itemsFlow.asSharedFlow()

    @Volatile
    private var loadedPageNumber = 0

    private var allNormalLoaded = AtomicBoolean(false)
    private var allExtraLoaded = AtomicBoolean(false)

    // Copy-on-write: on every mutation a freshly built Map is published into this @Volatile ref
    // and the local mutable reference is dropped, so the published instance is never mutated again.
    // Readers take a single volatile read and iterate the snapshot — no CME possible.
    @Volatile
    private var adaptersMap: Map<TransactionWallet, TransactionAdapterWrapper> = emptyMap()

    @Volatile
    private var extraSwapAdaptersMap: Map<TransactionWallet, TransactionAdapterWrapper> = emptyMap()

    private val coroutineScope = CoroutineScope(dispatcherProvider.io)
    private var updatesJob: Job? = null
    private var loadingJob: Job? = null

    // Cache of last load request to avoid duplicate work
    @Volatile
    private var lastLoadRequest: Pair<Int, FilterContext>? = null

    @Volatile
    private var walletSetVersion = 0

    private var transactionWallets: List<TransactionWallet> = listOf()
    private var walletsGroupedBySource: List<TransactionWallet> = listOf()

    private val activeAdapters: List<TransactionAdapterWrapper>
        get() {
            val snapshot = adaptersMap
            return activeWallets().mapNotNull { snapshot[it] }
        }

    private val activeSwapExtraAdapters: List<TransactionAdapterWrapper>
        get() {
            val snapshot = extraSwapAdaptersMap
            return activeWallets().mapNotNull { snapshot[it] }
        }

    private fun activeWallets(): List<TransactionWallet> {
        val tmpSelectedWallet = selectedWallet
        val tmpSelectedBlockchain = selectedBlockchain
        return walletsGroupedBySource.filterBySelection(tmpSelectedWallet, tmpSelectedBlockchain)
    }

    /**
     * Captures current filter context for snapshot comparison.
     * Thread-safe: creates new instance with current values.
     */
    private fun getCurrentFilterContext() = FilterContext(
        transactionType = selectedFilterTransactionType,
        wallet = selectedWallet,
        blockchain = selectedBlockchain,
        contact = contact,
        searchQuery = selectedSearchQuery,
        walletSetVersion = walletSetVersion
    )

    private fun groupWalletsBySource(transactionWallets: List<TransactionWallet>): List<TransactionWallet> {
        val mergedWallets = mutableListOf<TransactionWallet>()

        transactionWallets.forEach { wallet ->
            when (wallet.source.blockchain.type) {
                BlockchainType.Bitcoin,
                BlockchainType.BitcoinCash,
                BlockchainType.ECash,
                BlockchainType.Litecoin,
                BlockchainType.Dogecoin,
                BlockchainType.PirateCash,
                BlockchainType.Cosanta,
                BlockchainType.Dash,
                BlockchainType.Monero,
                BlockchainType.Zcash -> mergedWallets.add(wallet)

                BlockchainType.Ethereum,
                BlockchainType.BinanceSmartChain,
                BlockchainType.Polygon,
                BlockchainType.Avalanche,
                BlockchainType.Optimism,
                BlockchainType.Base,
                BlockchainType.ZkSync,
                BlockchainType.ArbitrumOne,
                BlockchainType.Gnosis,
                BlockchainType.Fantom,
                BlockchainType.Solana,
                BlockchainType.Tron,
                BlockchainType.Stellar,
                BlockchainType.Ton -> {
                    if (mergedWallets.none { it.source == wallet.source }) {
                        mergedWallets.add(TransactionWallet(null, wallet.source, null))
                    }
                }

                is BlockchainType.Unsupported -> Unit
            }
        }
        return mergedWallets

    }

    override fun set(
        transactionWallets: List<TransactionWallet>,
        wallet: TransactionWallet?,
        transactionType: FilterTransactionType,
        blockchain: Blockchain?,
        contact: Contact?,
        searchQuery: String?,
    ): Boolean {
        val walletsChanged = updateWalletAdapters(transactionWallets, contact)
        val filtersChanged = updateSelectedFilters(
            wallet = wallet,
            blockchain = blockchain,
            transactionType = transactionType,
            contact = contact,
            searchQuery = searchQuery,
        )

        return walletsChanged || filtersChanged
    }

    override fun reloadItems() = reloadItemsFromStart()

    private fun updateWalletAdapters(
        transactionWallets: List<TransactionWallet>,
        contact: Contact?,
    ): Boolean {
        if (this.transactionWallets == transactionWallets && adaptersMap.isNotEmpty()) return false

        this.transactionWallets = transactionWallets
        walletSetVersion++
        walletsGroupedBySource = groupWalletsBySource(transactionWallets)
        adaptersMap = rebuildAdapters(adaptersMap, selectedFilterTransactionType, contact)
        buildExtraSwapAdapters(contact)
        return true
    }

    private fun updateSelectedFilters(
        wallet: TransactionWallet?,
        blockchain: Blockchain?,
        transactionType: FilterTransactionType,
        contact: Contact?,
        searchQuery: String?,
    ): Boolean {
        var changed = updateSelectedWallet(wallet)
        changed = updateSelectedBlockchain(blockchain) || changed
        changed = updateSelectedTransactionType(transactionType) || changed
        changed = updateSelectedContact(contact) || changed
        changed = updateSelectedSearchQuery(searchQuery) || changed
        return changed
    }

    private fun updateSelectedWallet(wallet: TransactionWallet?): Boolean {
        val changed = selectedWallet != wallet || wallet == null
        if (changed) selectedWallet = wallet
        return changed
    }

    private fun updateSelectedBlockchain(blockchain: Blockchain?): Boolean {
        if (selectedBlockchain == blockchain) return false
        selectedBlockchain = blockchain
        return true
    }

    private fun updateSelectedTransactionType(transactionType: FilterTransactionType): Boolean {
        if (transactionType == selectedFilterTransactionType) return false
        selectedFilterTransactionType = transactionType
        adaptersMap.values.forEach { it.setTransactionType(transactionType) }
        return true
    }

    private fun updateSelectedContact(contact: Contact?): Boolean {
        if (this.contact == contact) return false
        this.contact = contact
        updateContact(contact)
        return true
    }

    private fun updateSelectedSearchQuery(searchQuery: String?): Boolean {
        val normalizedSearchQuery = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        if (selectedSearchQuery == normalizedSearchQuery) return false
        selectedSearchQuery = normalizedSearchQuery
        return true
    }

    private fun rebuildAdapters(
        previousAdapters: Map<TransactionWallet, TransactionAdapterWrapper>,
        transactionType: FilterTransactionType,
        contact: Contact?,
    ): Map<TransactionWallet, TransactionAdapterWrapper> {
        val remainingAdapters = previousAdapters.toMutableMap()
        val newAdapters = mutableMapOf<TransactionWallet, TransactionAdapterWrapper>()
        activeTransactionWallets().forEach { transactionWallet ->
            val adapter = remainingAdapters.remove(transactionWallet)
                ?: createAdapterWrapper(transactionWallet, transactionType, contact)
            adapter?.let { newAdapters[transactionWallet] = it }
        }
        remainingAdapters.values.forEach(TransactionAdapterWrapper::clear)
        return newAdapters
    }

    private fun createAdapterWrapper(
        transactionWallet: TransactionWallet,
        transactionType: FilterTransactionType,
        contact: Contact?,
    ): TransactionAdapterWrapper? {
        return adapterManager.getAdapter(transactionWallet.source)?.let {
            TransactionAdapterWrapper(
                transactionsAdapter = it,
                transactionWallet = transactionWallet,
                transactionType = transactionType,
                contact = contact,
                pendingRepository = pendingRepository,
                pendingConverter = pendingConverter,
                pendingTransactionMatcher = pendingTransactionMatcher,
                locallyCreatedTransactionRepository = locallyCreatedTransactionRepository,
                dispatcherProvider = dispatcherProvider,
            )
        }
    }

    private fun activeTransactionWallets(): List<TransactionWallet> {
        return (transactionWallets + walletsGroupedBySource).distinct()
    }

    private fun buildExtraSwapAdapters(contact: Contact?) {
        extraSwapAdaptersMap = rebuildAdapters(
            previousAdapters = extraSwapAdaptersMap,
            transactionType = FilterTransactionType.Outgoing,
            contact = contact,
        )
    }

    private fun updateContact(contact: Contact?) {
        adaptersMap.values.forEach { it.setContact(contact) }
        extraSwapAdaptersMap.values.forEach { it.setContact(contact) }
    }

    private fun reloadItemsFromStart() {
        unsubscribeFromUpdates()
        allNormalLoaded.set(false)
        allExtraLoaded.set(false)
        loadedPageNumber = 1
        loadItems(loadedPageNumber)
        subscribeForUpdates()
    }

    override fun loadNext() {
        if (allNormalLoaded.get() &&
            (selectedFilterTransactionType != FilterTransactionType.Swap || allExtraLoaded.get())
        ) return
        loadItems(loadedPageNumber + 1)
    }

    override fun reload() {
        adaptersMap.values.forEach { it.reload() }
        reloadItemsFromStart()
    }

    override fun cancelPendingLoads() {
        loadingJob?.cancel()
        updatesJob?.cancel()
    }

    private fun unsubscribeFromUpdates() {
        updatesJob?.cancel()
    }

    private fun subscribeForUpdates() {
        val updateFlows = activeUpdateFlows()
        if (updateFlows.isEmpty()) return

        updatesJob = coroutineScope.launch {
            updateFlows
                .merge()
                .collect {
                    handleUpdates()
                }
        }
    }

    private fun activeUpdateFlows(): List<Flow<Unit>> = buildList {
        addAll(activeAdapters.map { it.updatedFlow })

        if (selectedFilterTransactionType == FilterTransactionType.Swap) {
            addAll(activeSwapExtraAdapters.map { it.updatedFlow })
            add(swapProviderTransactionsStorage.observeAll().map { Unit })
        }
    }

    @Synchronized
    private fun handleUpdates() {
        allNormalLoaded.set(false)
        allExtraLoaded.set(false)
        loadItems(loadedPageNumber)
    }

    private fun loadItems(page: Int) {
        // Capture current filter context for validation
        val requestContext = getCurrentFilterContext()
        val currentRequest = Pair(page, requestContext)

        // Optimization: Skip if exact same request is already in progress
        if (loadingJob?.isActive == true && lastLoadRequest == currentRequest) {
            return
        }

        // Cache this request for future comparison
        lastLoadRequest = currentRequest

        // Cancel previous load if it's a different request
        loadingJob?.cancel()

        val itemsCount = page * itemsPerPage
        val adapters = activeAdapters

        loadingJob = coroutineScope.launch {
            try {
                loadItemsForContext(page, itemsCount, requestContext, adapters)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun CoroutineScope.loadItemsForContext(
        page: Int,
        itemsCount: Int,
        requestContext: FilterContext,
        adapters: List<TransactionAdapterWrapper>,
    ) {
        val searchQuery = requestContext.searchQuery
        if (searchQuery == null) {
            loadRegularPage(page, itemsCount, requestContext, adapters)
        } else {
            loadSearchPage(page, itemsCount, requestContext, searchQuery, adapters)
        }
    }

    private suspend fun CoroutineScope.loadRegularPage(
        page: Int,
        itemsCount: Int,
        requestContext: FilterContext,
        adapters: List<TransactionAdapterWrapper>,
    ) {
        val records = loadAdapterRecords(
            adapters = adapters,
            limit = itemsCount,
            transactionType = requestContext.transactionType,
            contact = requestContext.contact,
        )
        if (!isActive || requestContext.isStale()) return

        val extraRecords = loadRegularSwapExtraRecords(itemsCount, requestContext)
        if (!isActive || requestContext.isStale()) return

        if (extraRecords.sourceRecordsCount < itemsCount) {
            allExtraLoaded.set(true)
        }
        handleRecords(records, extraRecords.records, page)
    }

    private suspend fun CoroutineScope.loadSearchPage(
        page: Int,
        itemsCount: Int,
        requestContext: FilterContext,
        query: String,
        adapters: List<TransactionAdapterWrapper>,
    ) {
        val result = loadSearchItems(
            expectedItemsCount = itemsCount,
            requestContext = requestContext,
            query = query,
            normalAdapters = adapters,
            extraAdapters = activeSearchExtraAdapters(requestContext),
        )
        if (!isActive || requestContext.isStale()) return

        allNormalLoaded.set(result.normalLoaded)
        allExtraLoaded.set(result.extraLoaded)

        // Mirrors loadNext's early-return guard: true when a further loadNext would be a no-op.
        val searchExhausted = result.normalLoaded &&
            (requestContext.transactionType != FilterTransactionType.Swap || result.extraLoaded)
        emitRecords(result.records, page, searchCompleted = true, searchExhausted = searchExhausted)
    }

    private suspend fun loadRegularSwapExtraRecords(
        itemsCount: Int,
        requestContext: FilterContext,
    ): ExtraRecordsResult {
        if (requestContext.transactionType != FilterTransactionType.Swap) {
            return ExtraRecordsResult(emptyList(), 0)
        }

        val records = loadAdapterRecords(
            adapters = activeSwapExtraAdapters,
            limit = itemsCount,
            transactionType = FilterTransactionType.Outgoing,
            contact = requestContext.contact,
        )
        return ExtraRecordsResult(
            records = records.filter(::isMatchingSwapProviderTransaction),
            sourceRecordsCount = records.size,
        )
    }

    private suspend fun loadAdapterRecords(
        adapters: List<TransactionAdapterWrapper>,
        limit: Int,
        transactionType: FilterTransactionType,
        contact: Contact?,
    ): List<TransactionRecord> = coroutineScope {
        adapters
            .map { wrapper ->
                async {
                    wrapper.get(
                        limit = limit,
                        requestedFilterType = transactionType,
                        requestedContact = contact
                    )
                }
            }
            .awaitAll()
            .flatten()
    }

    private fun activeSearchExtraAdapters(requestContext: FilterContext): List<TransactionAdapterWrapper> {
        return if (requestContext.transactionType == FilterTransactionType.Swap) {
            activeSwapExtraAdapters
        } else {
            emptyList()
        }
    }

    private fun FilterContext.isStale(): Boolean {
        return this != getCurrentFilterContext()
    }

    private suspend fun loadSearchItems(
        expectedItemsCount: Int,
        requestContext: FilterContext,
        query: String,
        normalAdapters: List<TransactionAdapterWrapper>,
        extraAdapters: List<TransactionAdapterWrapper>,
    ): SearchLoadResult {
        val normalSources = normalAdapters.map { SearchSourceState(it, requestContext.transactionType) }
        val extraSources = extraAdapters.map { SearchSourceState(it, FilterTransactionType.Outgoing) }
        val allSources = normalSources + extraSources
        val totalScannedCount = scanSearchSources(
            sources = allSources,
            normalSources = normalSources,
            extraSources = extraSources,
            expectedItemsCount = expectedItemsCount,
            requestContext = requestContext,
            query = query,
        )

        return SearchLoadResult(
            records = mergedSearchRecords(normalSources, extraSources, expectedItemsCount),
            normalLoaded = searchSourcesLoaded(normalSources, totalScannedCount),
            extraLoaded = extraSources.isEmpty() || searchSourcesLoaded(extraSources, totalScannedCount),
        )
    }

    private suspend fun scanSearchSources(
        sources: List<SearchSourceState>,
        normalSources: List<SearchSourceState>,
        extraSources: List<SearchSourceState>,
        expectedItemsCount: Int,
        requestContext: FilterContext,
        query: String,
    ): Int {
        var totalScannedCount = 0

        while (
            totalScannedCount < maxTotalScannedRecords &&
            sources.any { !it.finished }
        ) {
            val round = scanSearchRound(
                sources = sources,
                totalScannedCount = totalScannedCount,
                expectedItemsCount = expectedItemsCount,
                requestContext = requestContext,
                query = query,
            )
            totalScannedCount = round.totalScannedCount

            val mergedRecords = mergedSearchRecords(normalSources, extraSources, expectedItemsCount)
            if (mergedRecords.size >= expectedItemsCount || !round.progressed) {
                break
            }
        }

        return totalScannedCount
    }

    private suspend fun scanSearchRound(
        sources: List<SearchSourceState>,
        totalScannedCount: Int,
        expectedItemsCount: Int,
        requestContext: FilterContext,
        query: String,
    ): SearchRoundResult {
        var updatedTotalScannedCount = totalScannedCount
        var progressed = false

        sources.forEach { source ->
            val previousScannedCount = source.scannedCount
            scanSearchSource(
                source = source,
                totalScannedCount = updatedTotalScannedCount,
                expectedItemsCount = expectedItemsCount,
                requestContext = requestContext,
                query = query,
            )
            updatedTotalScannedCount += (source.scannedCount - previousScannedCount).coerceAtLeast(0)
            progressed = progressed || source.scannedCount > previousScannedCount
        }

        return SearchRoundResult(updatedTotalScannedCount, progressed)
    }

    private suspend fun scanSearchSource(
        source: SearchSourceState,
        totalScannedCount: Int,
        expectedItemsCount: Int,
        requestContext: FilterContext,
        query: String,
    ) {
        if (source.finished || totalScannedCount >= maxTotalScannedRecords) {
            return
        }

        val scanLimit = nextSourceScanLimit(source.scannedCount, totalScannedCount)
        if (scanLimit <= source.scannedCount) {
            source.finished = true
            return
        }

        // A failing source is treated like a timeout: mark it finished and keep scanning the
        // rest, so the search still reaches its terminal emission instead of stranding the UI
        // in a permanent "scanning" state.
        val page = try {
            withTimeoutOrNull(adapterCallTimeoutMs) {
                source.wrapper.search(
                    limit = expectedItemsCount,
                    scanLimit = scanLimit,
                    requestedFilterType = source.transactionType,
                    requestedContact = requestContext.contact,
                    query = query,
                    matcher = searchMatcher,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }

        if (page == null) {
            source.finished = true
            return
        }

        source.scannedCount = page.scannedCount
        source.records = searchSourceRecords(page.records, source, requestContext)
        source.finished = page.exhausted || source.scannedCount >= maxScannedRecordsPerSource
    }

    private fun nextSourceScanLimit(
        sourceScannedCount: Int,
        totalScannedCount: Int,
    ): Int {
        val remainingTotal = maxTotalScannedRecords - totalScannedCount
        return minOf(
            sourceScannedCount + searchBatchSize,
            maxScannedRecordsPerSource,
            sourceScannedCount + remainingTotal,
        )
    }

    private fun searchSourceRecords(
        records: List<TransactionRecord>,
        source: SearchSourceState,
        requestContext: FilterContext,
    ): List<TransactionRecord> {
        return if (source.transactionType == FilterTransactionType.Outgoing &&
            requestContext.transactionType == FilterTransactionType.Swap
        ) {
            records.filter(::isMatchingSwapProviderTransaction)
        } else {
            records
        }
    }

    private fun searchSourcesLoaded(
        sources: List<SearchSourceState>,
        totalScannedCount: Int,
    ): Boolean {
        return sources.all { it.finished } || totalScannedCount >= maxTotalScannedRecords
    }

    private fun mergedSearchRecords(
        normalSources: List<SearchSourceState>,
        extraSources: List<SearchSourceState>,
        expectedItemsCount: Int,
    ): List<TransactionRecord> {
        return (normalSources.flatMap { it.records } + extraSources.flatMap { it.records })
            .sortedDescending()
            .take(expectedItemsCount)
    }

    private fun emitRecords(
        records: List<TransactionRecord>,
        page: Int,
        searchCompleted: Boolean = false,
        searchExhausted: Boolean = false,
    ) {
        loadedPageNumber = page
        _itemsFlow.tryEmit(
            RecordsBatch(records.sortedDescending().take(page * itemsPerPage), searchCompleted, searchExhausted)
        )
    }

    override fun clear() {
        val previousAdapters = adaptersMap
        adaptersMap = emptyMap()
        previousAdapters.values.forEach(TransactionAdapterWrapper::clear)

        val previousExtraSwapAdapters = extraSwapAdaptersMap
        extraSwapAdaptersMap = emptyMap()
        previousExtraSwapAdapters.values.forEach(TransactionAdapterWrapper::clear)

        coroutineScope.cancel()
    }

    private fun handleRecords(
        records: List<TransactionRecord>,
        extraRecords: List<TransactionRecord>,
        page: Int
    ) {
        val expectedItemsCount = page * itemsPerPage

        val normalSortedRecords = records
            .sortedDescending()
            .take(expectedItemsCount)

        if (normalSortedRecords.size < expectedItemsCount) {
            allNormalLoaded.set(true)
        }

        val extraSortedRecords = extraRecords
            .sortedDescending()
            .take(expectedItemsCount)

        loadedPageNumber = page
        _itemsFlow.tryEmit(RecordsBatch((normalSortedRecords + extraSortedRecords).sortedDescending()))
    }

    private fun isMatchingSwapProviderTransaction(record: TransactionRecord): Boolean {
        // First check by outgoingRecordUid (fast, already matched)
        swapProviderTransactionsStorage.getByOutgoingRecordUid(record.uid)?.let {
            return true
        }

        // Fall back to matching by token, amount and timestamp
        val shortOutgoingTransactionRecord = record.getShortOutgoingTransactionRecord()
            ?: return false

        val token = shortOutgoingTransactionRecord.token
            ?: return false

        return swapProviderTransactionsStorage.getByCoinUidIn(
            coinUid = token.coin.uid,
            blockchainType = token.blockchainType.uid,
            amountIn = shortOutgoingTransactionRecord.amountOut,
            timestamp = shortOutgoingTransactionRecord.timestamp
        ) != null
    }

    companion object {
        const val itemsPerPage = 20
        private const val searchBatchSize = 100
        private const val maxScannedRecordsPerSource = 1000
        private const val maxTotalScannedRecords = 5000
        private const val adapterCallTimeoutMs = 3000L
    }

}

/**
 * Snapshot of filter context at the time of load request.
 * Used to detect filter changes during async loading and discard stale results.
 */
private data class FilterContext(
    val transactionType: FilterTransactionType,
    val wallet: TransactionWallet?,
    val blockchain: Blockchain?,
    val contact: Contact?,
    val searchQuery: String?,
    val walletSetVersion: Int
)

private data class SearchLoadResult(
    val records: List<TransactionRecord>,
    val normalLoaded: Boolean,
    val extraLoaded: Boolean,
)

private data class ExtraRecordsResult(
    val records: List<TransactionRecord>,
    val sourceRecordsCount: Int,
)

private data class SearchRoundResult(
    val totalScannedCount: Int,
    val progressed: Boolean,
)

private class SearchSourceState(
    val wrapper: TransactionAdapterWrapper,
    val transactionType: FilterTransactionType,
) {
    var records: List<TransactionRecord> = emptyList()
    var scannedCount: Int = 0
    var finished: Boolean = false
}
