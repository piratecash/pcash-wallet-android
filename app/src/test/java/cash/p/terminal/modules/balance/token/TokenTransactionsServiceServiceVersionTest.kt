package cash.p.terminal.modules.balance.token

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.ITransactionRecordRepository
import cash.p.terminal.modules.transactions.NftMetadataService
import cash.p.terminal.modules.transactions.TransactionSyncStateRepository
import cash.p.terminal.modules.transactions.TransactionsRateRepository
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.transaction.TransactionSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * Proves the serviceVersion guard in [TokenTransactionsService] is necessary.
 *
 * Reproduces the race: a batch emitted for filter A is being processed by
 * handleUpdatedRecords() (blocked on the synchronous NFT-metadata read) when the user
 * switches the filter. setTransactionType() bumps serviceVersion and clears the list.
 * When the stale batch resumes, the guard must discard it instead of writing filter-A
 * records over the freshly cleared list.
 *
 * Detection: getLastBlockInfo(source) is reached only after a batch passes the guard and
 * enters the item-building block. If stale batch A leaks, getLastBlockInfo is invoked with
 * sourceA; the guard prevents that. Batch B (current filter) is emitted afterwards as a
 * barrier — the sequential collector processes it only after batch A's handler returns, so
 * waiting for batch B guarantees batch A is fully drained before the assertion.
 */
class TokenTransactionsServiceServiceVersionTest : KoinTest {

    private val repository = mockk<ITransactionRecordRepository>(relaxed = true)
    private val rateRepository = mockk<TransactionsRateRepository>(relaxed = true)
    private val syncStateRepository = mockk<TransactionSyncStateRepository>(relaxed = true)
    private val adapterManager = mockk<TransactionAdapterManager>(relaxed = true)
    private val nftMetadataService = mockk<NftMetadataService>(relaxed = true)
    private val spamManager = mockk<SpamManager>(relaxed = true)
    private val wallet = mockk<Wallet>(relaxed = true)

    private lateinit var repositoryItemsFlow: MutableSharedFlow<List<TransactionRecord>>

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
        // emptyMap never contains wallet.transactionSource -> handleInitialization() never runs
        every { adapterManager.adaptersReadyFlow } returns MutableStateFlow(emptyMap())
        every { nftMetadataService.assetsBriefMetadataFlow } returns MutableStateFlow(emptyMap())
        every { spamManager.shouldHide(any()) } returns false
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun handleUpdatedRecords_filterSwitchedWhileProcessing_guardDiscardsStaleBatch() = runBlocking {
        val sourceA = mockk<TransactionSource>(relaxed = true)
        val sourceB = mockk<TransactionSource>(relaxed = true)
        val recordA = mockRecord("a1", sourceA)
        val recordB = mockRecord("b1", sourceB)

        val metadataReached = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val batchBReached = CountDownLatch(1)
        var metadataCall = 0

        every { nftMetadataService.assetsBriefMetadata(any()) } answers {
            if (metadataCall++ == 0) {
                metadataReached.countDown()
                proceed.await(5, TimeUnit.SECONDS)
            }
            emptyMap()
        }

        val getLastBlockSources = Collections.synchronizedList(mutableListOf<TransactionSource>())
        coEvery { syncStateRepository.getLastBlockInfo(any()) } answers {
            val source = firstArg<TransactionSource>()
            getLastBlockSources.add(source)
            if (source == sourceB) batchBReached.countDown()
            null
        }

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )
        service.start()

        // Ensure the itemsFlow collector is subscribed before emitting (replay = 0)
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }

        // Batch A arrives and blocks inside handleUpdatedRecords on the metadata read
        repositoryItemsFlow.emit(listOf(recordA))
        assertTrue(
            "handleUpdatedRecords did not reach the metadata read",
            metadataReached.await(5, TimeUnit.SECONDS)
        )

        // User switches filter while batch A is mid-flight: bumps serviceVersion, clears list
        service.setTransactionType(FilterTransactionType.Incoming)

        // Release the stale batch A
        proceed.countDown()

        // Emit batch B (current filter) as a barrier: the sequential collector runs it only
        // after batch A's handler has fully returned.
        repositoryItemsFlow.emit(listOf(recordB))
        assertTrue(
            "Batch B was never processed",
            batchBReached.await(5, TimeUnit.SECONDS)
        )

        // Batch A's handler has now completed. The guard must have discarded it, so
        // getLastBlockInfo was never invoked with sourceA.
        assertFalse(
            "Stale batch A leaked past the serviceVersion guard",
            getLastBlockSources.contains(sourceA)
        )

        service.clear()
    }

    @Test
    fun handleUpdatedRecords_nonEmptyBatch_publishesItemsBeforeMarkingLoaded() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val record = mockRecord("r1", source)

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )

        // getLastBlockInfo runs while building items inside _transactionItems.update {}. Capture
        // whether records were already marked loaded at that point: if so, the loaded flag flipped
        // before the items were published — the ordering that flashes "no transactions".
        var recordsLoadedDuringBuild: Boolean? = null
        val buildReached = CountDownLatch(1)
        coEvery { syncStateRepository.getLastBlockInfo(any()) } answers {
            recordsLoadedDuringBuild = service.recordsLoadedFlow.value
            buildReached.countDown()
            null
        }

        service.start()
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }

        repositoryItemsFlow.emit(listOf(record))
        assertTrue("Item building was never reached", buildReached.await(5, TimeUnit.SECONDS))

        // Items must be published before the loaded flag flips.
        assertEquals(false, recordsLoadedDuringBuild)

        // Sanity: the flag is still set once the batch is fully processed.
        waitUntil { service.recordsLoadedFlow.value }
        assertTrue(service.recordsLoadedFlow.value)

        service.clear()
    }

    @Test
    fun handleUpdatedRecords_initialEmptyClearThenRealBatch_clearDoesNotMarkLoaded() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val record = mockRecord("r1", source)

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )

        // Captured while the real first page is being built (inside _transactionItems.update, before
        // the loaded flag is set). The repository emits a synthetic empty list first to clear the UI
        // before the page loads; if that clear marked records as loaded, this would be true — the
        // ordering that flashes "no transactions" on an already-synced wallet during init.
        var recordsLoadedWhenRealBatchBuilt: Boolean? = null
        val buildReached = CountDownLatch(1)
        coEvery { syncStateRepository.getLastBlockInfo(any()) } answers {
            recordsLoadedWhenRealBatchBuilt = service.recordsLoadedFlow.value
            buildReached.countDown()
            null
        }

        service.start()
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }

        // 1) synthetic empty clear, 2) real first page
        repositoryItemsFlow.emit(emptyList())
        repositoryItemsFlow.emit(listOf(record))

        assertTrue("Item building was never reached", buildReached.await(5, TimeUnit.SECONDS))

        // The clear must not have marked records as loaded.
        assertEquals(false, recordsLoadedWhenRealBatchBuilt)

        service.clear()
    }

    @Test
    fun handleInitialization_typeSelectedBeforeInit_initKeepsSelectedTypeNotAll() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val adapter = mockk<ITransactionsAdapter>(relaxed = true)
        every { wallet.transactionSource } returns source

        // Controllable readiness: empty until we publish, so handleInitialization stays gated
        // on adaptersReadyFlow.first() until the user has already picked a filter.
        val adaptersFlow = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(emptyMap())
        every { adapterManager.adaptersReadyFlow } returns adaptersFlow

        val setTypes = Collections.synchronizedList(mutableListOf<FilterTransactionType>())
        every { repository.set(any(), any(), any(), any(), any()) } answers {
            setTypes.add(thirdArg())
        }

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )
        service.start()

        // User taps Incoming before the transaction adapter is ready (init not run yet).
        service.setTransactionType(FilterTransactionType.Incoming)

        // Adapter becomes ready -> handleInitialization runs.
        adaptersFlow.value = mapOf(source to adapter)

        // Wait for handleInitialization to issue its set().
        waitUntil { setTypes.isNotEmpty() }

        // handleInitialization must load exactly the user's selection: one set() with Incoming,
        // never a reset to All.
        assertEquals(listOf(FilterTransactionType.Incoming), setTypes.toList())

        service.clear()
    }

    @Test
    fun setTransactionType_calledBeforeInit_recordsLoadedStaysFalseUntilRealBatch() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val adapter = mockk<ITransactionsAdapter>(relaxed = true)
        val record = mockRecord("r1", source)
        every { wallet.transactionSource } returns source

        // Controllable readiness: empty until published, so handleInitialization stays gated.
        val adaptersFlow = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(emptyMap())
        every { adapterManager.adaptersReadyFlow } returns adaptersFlow

        // Mirrors the real repository.set(): always emits a synthetic clear, then a load result.
        // With no active adapters (pre-init) the result is empty; once adapters are ready it
        // returns the real page.
        every { repository.set(any(), any(), any(), any(), any()) } answers {
            repositoryItemsFlow.tryEmit(emptyList())
            if (adaptersFlow.value.isEmpty()) {
                repositoryItemsFlow.tryEmit(emptyList())
            } else {
                repositoryItemsFlow.tryEmit(listOf(record))
            }
        }

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )

        // Captured while the real first page is being built (inside _transactionItems.update, before
        // the loaded flag is set). Any pre-init empty batch must not have flipped it true by then.
        var recordsLoadedWhenRealBatchBuilt: Boolean? = null
        val buildReached = CountDownLatch(1)
        coEvery { syncStateRepository.getLastBlockInfo(any()) } answers {
            recordsLoadedWhenRealBatchBuilt = service.recordsLoadedFlow.value
            buildReached.countDown()
            null
        }

        service.start()
        waitUntil { repositoryItemsFlow.subscriptionCount.value >= 1 }

        // User taps a filter before init: a pre-init set() would emit two empty batches here.
        service.setTransactionType(FilterTransactionType.Incoming)

        // Adapter becomes ready -> handleInitialization issues the first real set().
        adaptersFlow.value = mapOf(source to adapter)

        assertTrue("Real batch was never built", buildReached.await(5, TimeUnit.SECONDS))

        // The pre-init empty batches must not have marked records as loaded before the real page.
        assertEquals(false, recordsLoadedWhenRealBatchBuilt)

        service.clear()
    }

    @Test
    fun handleInitialization_typeChangedDuringInitialLoad_reloadsRacedType() = runBlocking {
        val source = mockk<TransactionSource>(relaxed = true)
        val adapter = mockk<ITransactionsAdapter>(relaxed = true)
        every { wallet.transactionSource } returns source

        // Adapter ready from the start so handleInitialization runs immediately.
        every { adapterManager.adaptersReadyFlow } returns
            MutableStateFlow(mapOf(source to adapter))

        val initSetStarted = CountDownLatch(1)
        val releaseInitSet = CountDownLatch(1)
        val incomingSetReached = CountDownLatch(1)
        var setCall = 0
        every { repository.set(any(), any(), any(), any(), any()) } answers {
            val type = thirdArg<FilterTransactionType>()
            if (setCall++ == 0) {
                // First set() is the initial load; block it to keep the init window open.
                initSetStarted.countDown()
                releaseInitSet.await(5, TimeUnit.SECONDS)
            }
            if (type == FilterTransactionType.Incoming) incomingSetReached.countDown()
        }

        val service = TokenTransactionsService(
            wallet = wallet,
            rateRepository = rateRepository,
            transactionSyncStateRepository = syncStateRepository,
            transactionAdapterManager = adapterManager,
            nftMetadataService = nftMetadataService,
            spamManager = spamManager,
        )
        service.start()

        // The initial load is now in progress (blocked inside set()), before initialized = true.
        assertTrue("Initial load never started", initSetStarted.await(5, TimeUnit.SECONDS))

        // User switches filter during the initial load: adapters are ready, init not yet finished.
        service.setTransactionType(FilterTransactionType.Incoming)

        // Let the initial load finish.
        releaseInitSet.countDown()

        // After init, the service must reconcile the data layer to the raced selection.
        assertTrue(
            "Init did not reload the filter selected during the initial load",
            incomingSetReached.await(5, TimeUnit.SECONDS)
        )

        service.clear()
    }

    private fun mockRecord(uid: String, source: TransactionSource) =
        mockk<TransactionRecord>(relaxed = true) {
            every { this@mockk.uid } returns uid
            every { this@mockk.source } returns source
            every { mainValue } returns null
        }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
    }
}
