package cash.p.terminal.modules.balance.token

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.ITransactionRecordRepository
import cash.p.terminal.modules.transactions.NftMetadataService
import cash.p.terminal.modules.transactions.RecordsBatch
import cash.p.terminal.modules.transactions.SearchScanState
import cash.p.terminal.modules.transactions.TransactionSyncStateRepository
import cash.p.terminal.modules.transactions.TransactionsRateRepository
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.transaction.TransactionSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proves the in-app transaction search added to [TokenTransactionsService]: [setSearchQuery]
 * preserves the current filter/wallet, drives the deep scan by calling [TokenTransactionsService.loadNext]
 * while the repository reports the scan is not yet exhausted (mirroring the existing hidden-records
 * deep-scan logic in handleUpdatedRecords), and only flips [TokenTransactionsService.searchScanStateFlow]
 * to [SearchScanState.Finished] on a genuine terminal batch - never on an interim all-spam page.
 */
class TokenTransactionsServiceSearchTest : KoinTest {

    private val repository = mockk<ITransactionRecordRepository>(relaxed = true)
    private val rateRepository = mockk<TransactionsRateRepository>(relaxed = true)
    private val syncStateRepository = mockk<TransactionSyncStateRepository>(relaxed = true)
    private val adapterManager = mockk<TransactionAdapterManager>(relaxed = true)
    private val nftMetadataService = mockk<NftMetadataService>(relaxed = true)
    private val spamManager = mockk<SpamManager>(relaxed = true)
    private val wallet = mockk<Wallet>(relaxed = true)

    private lateinit var repositoryItemsFlow: MutableSharedFlow<RecordsBatch>

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(module { single { repository } })
    }

    @Before
    fun setUp() {
        repositoryItemsFlow = MutableSharedFlow(replay = 0, extraBufferCapacity = 8)
        every { repository.itemsFlow } returns repositoryItemsFlow
        every { rateRepository.dataExpiredFlow } returns MutableSharedFlow()
        every { rateRepository.historicalRateFlow } returns MutableSharedFlow()
        every { rateRepository.getHistoricalRate(any()) } returns null
        every { syncStateRepository.lastBlockInfoFlow } returns MutableSharedFlow()
        every { syncStateRepository.syncingFlow } returns MutableStateFlow(false)
        every { nftMetadataService.assetsBriefMetadataFlow } returns MutableStateFlow(emptyMap())
        every { spamManager.shouldHide(any()) } returns false
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    private fun readyService(source: TransactionSource): TokenTransactionsService {
        val adapter = mockk<ITransactionsAdapter>(relaxed = true)
        every { wallet.transactionSource } returns source
        every { adapterManager.adaptersReadyFlow } returns MutableStateFlow(mapOf(source to adapter))

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )
        service.start()
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }
        return service
    }

    @Test
    fun setSearchQuery_matchDeeperThanFirstPage_loadsNextThenSurfacesMatch() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val spamRecord = mockRecord("spam-1", source)
        val matchRecord = mockRecord("match-1", source)
        every { spamManager.shouldHide(spamRecord) } returns true

        val loadNextCalled = CountDownLatch(1)
        every { repository.loadNext() } answers { loadNextCalled.countDown() }

        val service = readyService(source)

        service.setSearchQuery("needle")
        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)

        // First page: only spam, repository says more could still be found -> must page deeper.
        repositoryItemsFlow.emit(RecordsBatch(listOf(spamRecord), searchCompleted = true, searchExhausted = false))
        assertTrue(
            "loadNext was not called for a non-exhausted all-spam page",
            loadNextCalled.await(5, TimeUnit.SECONDS)
        )
        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)
        assertTrue(service.transactionItemsFlow.value.isEmpty())

        // Deeper page: the real match, scan now exhausted.
        repositoryItemsFlow.emit(RecordsBatch(listOf(matchRecord), searchCompleted = true, searchExhausted = true))
        waitUntil { service.transactionItemsFlow.value.isNotEmpty() }

        assertEquals(listOf("match-1"), service.transactionItemsFlow.value.map { it.record.uid })
        assertEquals(SearchScanState.Finished, service.searchScanStateFlow.value)

        service.clear()
    }

    @Test
    fun setSearchQuery_matchDeeperThanFirstSearchBatch_chainsLoadNextUntilMatch() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val spamRecord1 = mockRecord("spam-1", source)
        val spamRecord2 = mockRecord("spam-2", source)
        val matchRecord = mockRecord("match-1", source)
        every { spamManager.shouldHide(spamRecord1) } returns true
        every { spamManager.shouldHide(spamRecord2) } returns true

        val loadNextCallCount = AtomicInteger(0)
        every { repository.loadNext() } answers { loadNextCallCount.incrementAndGet(); Unit }

        val service = readyService(source)

        service.setSearchQuery("needle")

        repositoryItemsFlow.emit(RecordsBatch(listOf(spamRecord1), searchCompleted = true, searchExhausted = false))
        assertTrue("loadNext was not called after the first spam-only page", waitUntilTrue { loadNextCallCount.get() >= 1 })
        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)

        repositoryItemsFlow.emit(RecordsBatch(listOf(spamRecord2), searchCompleted = true, searchExhausted = false))
        assertTrue("loadNext was not called after the second spam-only page", waitUntilTrue { loadNextCallCount.get() >= 2 })
        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)
        assertTrue(service.transactionItemsFlow.value.isEmpty())

        repositoryItemsFlow.emit(RecordsBatch(listOf(matchRecord), searchCompleted = true, searchExhausted = true))
        waitUntil { service.transactionItemsFlow.value.isNotEmpty() }

        assertEquals(listOf("match-1"), service.transactionItemsFlow.value.map { it.record.uid })
        assertEquals(SearchScanState.Finished, service.searchScanStateFlow.value)

        service.clear()
    }

    @Test
    fun setSearchQuery_swapFilterActive_passesTypeAndQueryTogetherAndSurfacesMatch() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val capturedCalls = Collections.synchronizedList(mutableListOf<Pair<FilterTransactionType, String?>>())
        every { repository.set(any(), any(), any(), any(), any(), any()) } answers {
            capturedCalls.add(thirdArg<FilterTransactionType>() to arg<String?>(5))
            false
        }

        val service = readyService(source)

        service.setTransactionType(FilterTransactionType.Swap)
        service.setSearchQuery("provider")

        assertTrue(
            "repository.set was never called with Swap type and the active search query together",
            waitUntilTrue { capturedCalls.any { it.first == FilterTransactionType.Swap && it.second == "provider" } }
        )

        val swapMatch = mockRecord("swap-match", source)
        repositoryItemsFlow.emit(RecordsBatch(listOf(swapMatch), searchCompleted = true, searchExhausted = true))
        waitUntil { service.transactionItemsFlow.value.isNotEmpty() }

        assertEquals(listOf("swap-match"), service.transactionItemsFlow.value.map { it.record.uid })
        assertEquals(SearchScanState.Finished, service.searchScanStateFlow.value)

        service.clear()
    }

    @Test
    fun setTransactionType_duringActiveSearch_preservesQueryAndResetsToScanning() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val capturedCalls = Collections.synchronizedList(mutableListOf<Pair<FilterTransactionType, String?>>())
        every { repository.set(any(), any(), any(), any(), any(), any()) } answers {
            capturedCalls.add(thirdArg<FilterTransactionType>() to arg<String?>(5))
            false
        }

        val service = readyService(source)

        service.setSearchQuery("needle")
        assertTrue(
            "repository.set was never called with the search query",
            waitUntilTrue { capturedCalls.any { it.second == "needle" } }
        )

        val match = mockRecord("m1", source)
        repositoryItemsFlow.emit(RecordsBatch(listOf(match), searchCompleted = true, searchExhausted = true))
        waitUntil { service.searchScanStateFlow.value == SearchScanState.Finished }

        // User switches the filter while the search query is still active.
        service.setTransactionType(FilterTransactionType.Incoming)

        // The scan state must immediately read Scanning again - a stale Finished must never leak -
        // and the reload must carry the same query forward instead of silently dropping it.
        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)
        assertTrue(service.transactionItemsFlow.value.isEmpty())
        assertTrue(
            "repository.set was never called with Incoming type and the preserved search query",
            waitUntilTrue {
                capturedCalls.any { it.first == FilterTransactionType.Incoming && it.second == "needle" }
            }
        )

        service.clear()
    }

    @Test
    fun setSearchQuery_noMatchFound_staysScanningUntilExhaustedThenFinishesEmpty() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val spamRecord = mockRecord("spam-1", source)
        every { spamManager.shouldHide(spamRecord) } returns true

        val service = readyService(source)

        service.setSearchQuery("nothing-matches")
        assertEquals(SearchScanState.Scanning, service.searchScanStateFlow.value)

        // Mid-scan: an all-spam, not-yet-exhausted page must not surface a "finished, empty" result.
        repositoryItemsFlow.emit(RecordsBatch(listOf(spamRecord), searchCompleted = true, searchExhausted = false))
        waitUntil { service.recordsLoadedFlow.value }
        assertEquals(
            "an interim all-spam page must not end the scan while more could still be found",
            SearchScanState.Scanning,
            service.searchScanStateFlow.value
        )
        assertTrue(service.transactionItemsFlow.value.isEmpty())

        // Scan exhausted with nothing but spam: this IS the final answer - only now flip to Finished.
        repositoryItemsFlow.emit(RecordsBatch(emptyList(), searchCompleted = true, searchExhausted = true))
        waitUntil { service.searchScanStateFlow.value == SearchScanState.Finished }

        assertTrue(service.transactionItemsFlow.value.isEmpty())

        service.clear()
    }

    private fun waitUntilTrue(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        waitUntil(timeoutMs, condition)
        return condition()
    }
}
