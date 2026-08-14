package cash.p.terminal.core.adapters.zcash

import android.database.sqlite.SQLiteDatabaseCorruptException
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.wallet.AdapterState
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.WalletInitMode
import cash.z.ecc.android.sdk.block.processor.CompactBlockProcessor
import cash.z.ecc.android.sdk.exception.CompactBlockProcessorException
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.PercentDecimal
import cash.z.ecc.android.sdk.model.TransactionOverview
import cash.z.ecc.android.sdk.model.ZcashNetwork
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for ZcashAdapter database corruption detection and recovery.
 *
 * The shared harness lives in [ZcashAdapterTestFixture]: all adapter coroutines (recovery,
 * status/start/restart jobs, the subscriber scope) run on its single `StandardTestDispatcher`
 * shared with `runTest`, so every wait below is driven deterministically via the virtual-time
 * scheduler instead of real timeouts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterCorruptionRecoveryTest : ZcashAdapterTestFixture() {

    private var capturedProcessorErrorHandler: ((Throwable?) -> Boolean)? = null
    private var capturedCriticalErrorHandler: ((Throwable?) -> Boolean)? = null

    override fun stubSynchronizer() {
        val processorSlot = slot<(Throwable?) -> Boolean>()
        every { mockSynchronizer.onProcessorErrorHandler = capture(processorSlot) } answers {
            capturedProcessorErrorHandler = processorSlot.captured
        }
        val criticalSlot = slot<(Throwable?) -> Boolean>()
        every { mockSynchronizer.onCriticalErrorHandler = capture(criticalSlot) } answers {
            capturedCriticalErrorHandler = criticalSlot.captured
        }
    }

    override fun stubSynchronizerCompanion() {
        coEvery { Synchronizer.erase(any(), any(), any()) } returns true
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } returns mockSynchronizer
    }

    private fun createAdapter(baseDelayMs: Long, maxDelayMs: Long): ZcashAdapter {
        return ZcashAdapter(
            context, wallet, restoreSettings, null, localStorage, backgroundManager,
            singleUseAddressManager, TestDispatcherProvider(dispatcher, appScope),
            baseDelayMs, maxDelayMs
        )
    }

    // --- onProcessorErrorHandler ---

    @Test
    fun onProcessorError_corruptionDetected_triggersRecovery() = runTest(dispatcher) {
        adapter = createAdapter()

        val result = capturedProcessorErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )

        assertFalse("Should return false to signal abort", result ?: true)
        advanceUntilIdle()
        coVerify { Synchronizer.erase(any(), ZcashNetwork.Mainnet, "zcash_test") }
        verifyRecoveryResubscribed()
    }

    @Test
    fun onProcessorError_rustDatabaseMalformed_triggersRecovery() = runTest(dispatcher) {
        adapter = createAdapter()

        val rustError = RuntimeException(
            "Rust error while scanning blocks (limit 10): " +
                    "The underlying datasource produced the following error: " +
                    "database disk image is malformed"
        )
        val result = capturedProcessorErrorHandler?.invoke(
            CompactBlockProcessorException.FailedSynchronizationException(
                "unable to resolve the error after 5 correction attempts",
                CompactBlockProcessorException.FailedScanException(rustError),
            )
        )

        assertFalse("Should detect Rust database malformed error", result ?: true)
        advanceUntilIdle()
        coVerify { Synchronizer.erase(any(), ZcashNetwork.Mainnet, "zcash_test") }
        verifyRecoveryResubscribed()
    }

    @Test
    fun onProcessorError_nonCorruptionError_doesNotTriggerRecovery() = runTest(dispatcher) {
        adapter = createAdapter()

        val result = capturedProcessorErrorHandler?.invoke(RuntimeException("some other error"))

        assertTrue("Should return true to signal retry", result ?: false)
        advanceUntilIdle()
        coVerify(exactly = 0) { Synchronizer.erase(any(), any(), any()) }
    }

    // --- onCriticalErrorHandler ---

    @Test
    fun onCriticalError_corruptionDetected_triggersRecovery() = runTest(dispatcher) {
        adapter = createAdapter()

        val result = capturedCriticalErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )

        assertFalse("Should return false to signal abort", result ?: true)
        advanceUntilIdle()
        coVerify { Synchronizer.erase(any(), ZcashNetwork.Mainnet, "zcash_test") }
        verifyRecoveryResubscribed()
    }

    @Test
    fun onCriticalError_wrappedCorruption_triggersRecovery() = runTest(dispatcher) {
        adapter = createAdapter()

        val corruption = SQLiteDatabaseCorruptException("database disk image is malformed")
        val wrapped = RuntimeException("flow failed", corruption)
        val result = capturedCriticalErrorHandler?.invoke(wrapped)

        assertFalse("Should detect wrapped corruption", result ?: true)
        advanceUntilIdle()
        coVerify { Synchronizer.erase(any(), ZcashNetwork.Mainnet, "zcash_test") }
        verifyRecoveryResubscribed()
    }

    // --- Flow-level catch ---

    @Test
    fun flowCorruption_triggersRecovery() = runTest(dispatcher) {
        val corruptFlow = flow<List<TransactionOverview>> {
            throw SQLiteDatabaseCorruptException("database disk image is malformed")
        }
        // One-shot corruption: the first collection throws (triggering recovery), then the recovered
        // synchronizer resubscribes to a healthy flow. Otherwise recovery re-subscribes to the same
        // permanently corrupt flow and loops forever, hanging advanceUntilIdle().
        var allTransactionsReads = 0
        every { mockSynchronizer.allTransactions } answers {
            if (allTransactionsReads++ == 0) corruptFlow else allTransactionsFlow
        }

        adapter = createAdapter()
        adapter.start()
        advanceUntilIdle()

        coVerify { Synchronizer.erase(any(), ZcashNetwork.Mainnet, "zcash_test") }
        verifySynchronizerNew()
        verify(atLeast = 2) { mockSynchronizer.processorInfo }
    }

    // --- Recovery correctness ---

    @Test
    fun recovery_usesRestoreWalletMode() = runTest(dispatcher) {
        var capturedInitMode: WalletInitMode? = null
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } answers {
            for (a in args) {
                if (a is WalletInitMode) capturedInitMode = a
            }
            mockSynchronizer
        }

        adapter = createAdapter()

        capturedProcessorErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )
        advanceUntilIdle()

        coVerify {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
        assertEquals(WalletInitMode.RestoreWallet, capturedInitMode)
        verify(atLeast = 1) { mockSynchronizer.processorInfo }
    }

    @Test
    fun recovery_retryAfterFailedNew_preservesRestoreWalletMode() = runTest(dispatcher) {
        val capturedModes = mutableListOf<WalletInitMode>()
        var newCallCount = 0
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } answers {
            newCallCount++
            for (a in args) {
                if (a is WalletInitMode) capturedModes.add(a)
            }
            if (newCallCount == 1) throw IllegalStateException("Another synchronizer with SynchronizerKey")
            mockSynchronizer
        }

        adapter = createAdapter()

        capturedProcessorErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )
        advanceUntilIdle()

        coVerify(atLeast = 2) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
        assertTrue("Should have at least 2 attempts", capturedModes.size >= 2)
        assertTrue(
            "All attempts must use RestoreWallet",
            capturedModes.all { it == WalletInitMode.RestoreWallet }
        )
        verify(atLeast = 1) { mockSynchronizer.processorInfo }
    }

    @Test
    fun recovery_newCancellation_doesNotRetryOrResubscribe() = runTest(dispatcher) {
        var newCallCount = 0
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } coAnswers {
            newCallCount++
            throw CancellationException("Synchronizer.new cancelled")
        }

        adapter = createAdapter()

        capturedProcessorErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )
        // Cancellation from Synchronizer.new propagates straight out of createNewSynchronizer()
        // (no retry delay on that path), so a full idle-drain deterministically proves whether a
        // second attempt happened - no real-time guard window needed.
        advanceUntilIdle()

        coVerify { Synchronizer.erase(any(), any(), any()) }
        coVerify(exactly = 1) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
        assertEquals(
            "Cancellation from Synchronizer.new must not be treated as retryable failure",
            1,
            newCallCount
        )
        verify(exactly = 0) { mockSynchronizer.processorInfo }
    }

    @Test
    fun recovery_usesOriginalBirthdayFromRestoreSettings() = runTest(dispatcher) {
        var capturedBirthday: BlockHeight? = null
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } answers {
            // Find birthday by checking all args for BlockHeight type
            for (i in 0 until args.size) {
                val a = args[i]
                if (a is BlockHeight) {
                    capturedBirthday = a
                    break
                }
            }
            mockSynchronizer
        }

        adapter = createAdapter()

        capturedProcessorErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )
        advanceUntilIdle()

        coVerify {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
        assertEquals(2000000L, capturedBirthday?.value)
        verify(atLeast = 1) { mockSynchronizer.processorInfo }
    }

    // --- Erase failure ---

    @Test
    fun recovery_failedErase_setsNotSyncedState() = runTest(dispatcher) {
        coEvery {
            Synchronizer.erase(any(), any(), any())
        } throws IllegalStateException("synchronizer still active")

        adapter = createAdapter()

        capturedProcessorErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )
        // eraseWithRetry() backs off 1s/2s/3s between its 3 bounded attempts; advanceUntilIdle()
        // deterministically fast-forwards through all of them.
        advanceUntilIdle()

        // All 3 erase retries must have completed - this means recovery has finished.
        coVerify(exactly = 3) { Synchronizer.erase(any(), any(), any()) }
        // Synchronizer.new should NOT be called after failed erase
        coVerify(exactly = 0) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
        assertTrue(
            "Should be NotSynced after failed erase",
            adapter.balanceState is AdapterState.NotSynced
        )
    }

    @Test
    fun recovery_eraseCancellation_doesNotRetryOrCreateSynchronizer() = runTest(dispatcher) {
        var eraseCallCount = 0
        coEvery {
            Synchronizer.erase(any(), any(), any())
        } coAnswers {
            eraseCallCount++
            throw CancellationException("erase cancelled")
        }

        adapter = createAdapter()

        capturedProcessorErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )
        // Cancellation is rethrown immediately by eraseWithRetry() (no backoff delay on that
        // path), so idle-draining the scheduler is a deterministic proof no retry occurred.
        advanceUntilIdle()

        coVerify(exactly = 1) { Synchronizer.erase(any(), any(), any()) }
        assertEquals(
            "Cancellation from erase must not be treated as retryable IllegalStateException",
            1,
            eraseCallCount
        )
        coVerify(exactly = 0) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
    }

    // --- Concurrency guard ---

    @Test
    fun recovery_concurrentCorruptions_onlyOneRecoveryRuns() = runTest(dispatcher) {
        val eraseStarted = CompletableDeferred<Unit>()
        val releaseErase = CompletableDeferred<Unit>()
        coEvery {
            Synchronizer.erase(any(), any(), any())
        } coAnswers {
            eraseStarted.complete(Unit)
            releaseErase.await()
            true
        }

        adapter = createAdapter()

        val error = SQLiteDatabaseCorruptException("database disk image is malformed")
        capturedProcessorErrorHandler?.invoke(error)
        advanceUntilIdle()
        assertTrue("Recovery must start before the second corruption is reported", eraseStarted.isCompleted)

        capturedCriticalErrorHandler?.invoke(error)
        advanceUntilIdle()

        coVerify(exactly = 1) { Synchronizer.erase(any(), any(), any()) }
        releaseErase.complete(Unit)
        advanceUntilIdle()
        verifyRecoveryResubscribed()
    }

    @Test
    fun recovery_stopAfterNewSynchronizer_doesNotResubscribeClosedSynchronizer() = runTest(dispatcher) {
        val getAccountsStarted = CompletableDeferred<Unit>()
        val releaseGetAccounts = CompletableDeferred<Unit>()
        val recoverySynchronizer = createMockSynchronizer().also { synchronizer ->
            coEvery { synchronizer.getAccounts() } coAnswers {
                getAccountsStarted.complete(Unit)
                releaseGetAccounts.await()
                emptyList()
            }
        }
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } returns recoverySynchronizer

        adapter = createAdapter()

        capturedProcessorErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )
        advanceUntilIdle()
        assertTrue("Recovery synchronizer must be installed before stop()", getAccountsStarted.isCompleted)

        adapter.stop()
        releaseGetAccounts.complete(Unit)
        advanceUntilIdle()

        verify(atLeast = 1) { recoverySynchronizer.close() }
        verify(exactly = 0) { recoverySynchronizer.processorInfo }
    }

    // --- Zombie adapter (MOBILE-587) ---

    @Test
    fun stop_thenEnterForeground_doesNotRestartSynchronizer() = runTest(dispatcher) {
        val bgStateFlow = MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.Unknown)
        every { backgroundManager.stateFlow } returns bgStateFlow

        var zombieRestartCount = 0
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } answers {
            zombieRestartCount++
            mockSynchronizer
        }

        adapter = createAdapter()

        // Simulate AdapterManager stopping the old adapter during wallet switch
        adapter.stop()
        statusFlow.value = Synchronizer.Status.STOPPED

        // Simulate app returning to foreground — stopped adapter must NOT react
        // scope is cancelled so the stateFlow collector is dead
        bgStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()

        coVerify(exactly = 0) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
        assertEquals(
            "Stopped adapter must not restart synchronizer on foreground event",
            0,
            zombieRestartCount
        )
    }

    @Test
    fun stop_whileStartInFlight_cancelsStart() = runTest(dispatcher) {
        val bgStateFlow = MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.Unknown)
        every { backgroundManager.stateFlow } returns bgStateFlow

        val startReached = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } coAnswers {
            startReached.complete(Unit)
            try {
                suspendCancellableCoroutine<Nothing> { }
            } finally {
                cancelled.complete(Unit)
            }
        }

        adapter = createAdapter()

        statusFlow.value = Synchronizer.Status.STOPPED
        bgStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()

        assertTrue("Synchronizer.new() must be reached before stop()", startReached.isCompleted)
        adapter.stop()
        advanceUntilIdle()

        assertTrue("Coroutine must be cancelled by stop()", cancelled.isCompleted)
    }

    @Test
    fun enterBackground_whileStartInFlight_cancelsStart() = runTest(dispatcher) {
        val bgStateFlow = MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.Unknown)
        every { backgroundManager.stateFlow } returns bgStateFlow

        val startReached = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } coAnswers {
            startReached.complete(Unit)
            try {
                suspendCancellableCoroutine<Nothing> { }
            } finally {
                cancelled.complete(Unit)
            }
        }

        adapter = createAdapter()

        statusFlow.value = Synchronizer.Status.STOPPED
        bgStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()

        assertTrue("Synchronizer.new() must be reached before enterBackground", startReached.isCompleted)
        bgStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        assertTrue("Coroutine must be cancelled by enterBackground", cancelled.isCompleted)
    }

    // --- Pause / resume (background → foreground) ---

    @Test
    fun enterBackground_thenEnterForeground_restartsSynchronizer() = runTest(dispatcher) {
        val bgStateFlow = MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.Unknown)
        every { backgroundManager.stateFlow } returns bgStateFlow

        var restartCount = 0
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } answers {
            restartCount++
            mockSynchronizer
        }

        adapter = createAdapter()

        // Simulate app going to background (pause — not full dispose)
        bgStateFlow.value = BackgroundManagerState.EnterBackground
        statusFlow.value = Synchronizer.Status.STOPPED

        // Simulate app returning to foreground
        bgStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()

        coVerify {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
        assertEquals(
            "Paused adapter must restart synchronizer on foreground event",
            1,
            restartCount
        )
    }

    // --- Sync progress preservation ---

    /**
     * Drains the shared virtual-time scheduler and asserts the adapter's state matches
     * [predicate]. Deterministic replacement for wall-clock polling: since every adapter
     * coroutine (recovery/status/subscriber jobs) runs on [dispatcher], draining it fully
     * guarantees all currently-schedulable work (including any queued flow emissions) has run.
     */
    private fun advanceAndAssertState(predicate: (AdapterState) -> Boolean) {
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue("Unexpected state: ${adapter.balanceState}", predicate(adapter.balanceState))
    }

    @Test
    fun onStatus_syncingWhileAlreadySyncingWithProgress_preservesProgress() = runTest(dispatcher) {
        adapter = createAdapter()
        adapter.start()

        // Wait for subscribe() to process initial SYNCING status
        advanceAndAssertState { it is AdapterState.Syncing }

        // SDK reports progress via onDownloadProgress
        progressFlow.value = PercentDecimal(0.99f)

        advanceAndAssertState { it is AdapterState.Syncing && it.progress == 99.0 }

        // SDK re-emits SYNCING status (e.g. entering new scan phase)
        statusFlow.value = Synchronizer.Status.SYNCING
        advanceUntilIdle()

        // Progress must NOT be wiped
        val state = adapter.balanceState
        assertTrue(
            "Should still be Syncing after re-emitted SYNCING status",
            state is AdapterState.Syncing
        )
        assertEquals(
            "Progress must be preserved when SDK re-emits SYNCING",
            99.0,
            (state as AdapterState.Syncing).progress
        )
    }

    @Test
    fun onProcessorInfo_syncRangeNearOrchard_preservesSdkProgress() = runTest(dispatcher) {
        adapter = createAdapter()
        adapter.start()

        advanceAndAssertState { it is AdapterState.Syncing }

        progressFlow.value = PercentDecimal(0.01f)
        advanceAndAssertState { it is AdapterState.Syncing && it.progress == 1.0 }

        processorInfoFlow.value = CompactBlockProcessor.ProcessorInfo(
            networkBlockHeight = BlockHeight.new(2_881_516L),
            overallSyncRange = BlockHeight.new(1_687_104L)..BlockHeight.new(1_687_104L),
            firstUnenhancedHeight = null
        )

        advanceAndAssertState { it is AdapterState.Syncing && it.blocksRemained != null }
        val state = adapter.balanceState as AdapterState.Syncing
        assertEquals(
            "ProcessorInfo range must not be treated as sync progress",
            1.0,
            state.progress
        )
    }

    @Test
    fun blocksRemained_scalesInverselyWithSdkProgress() = runTest(dispatcher) {
        adapter = createAdapter()
        adapter.start()

        advanceAndAssertState { it is AdapterState.Syncing }

        // accountBirthday from mock checkpoint = 2_500_000; networkHeight 3_500_000 → totalBlocks = 1_000_000
        processorInfoFlow.value = CompactBlockProcessor.ProcessorInfo(
            networkBlockHeight = BlockHeight.new(3_500_000L),
            overallSyncRange = null,
            firstUnenhancedHeight = null
        )
        advanceAndAssertState { it is AdapterState.Syncing && it.blocksRemained == 1_000_000L }

        progressFlow.value = PercentDecimal(0.5f)
        advanceAndAssertState {
            it is AdapterState.Syncing && it.progress == 50.0 && it.blocksRemained == 500_000L
        }
    }

    @Test
    fun onProcessorInfo_afterSynced_doesNotRestoreStaleBlocksRemaining() = runTest(dispatcher) {
        adapter = createAdapter()
        adapter.start()

        advanceAndAssertState { it is AdapterState.Syncing }

        processorInfoFlow.value = CompactBlockProcessor.ProcessorInfo(
            networkBlockHeight = BlockHeight.new(3_500_000L),
            overallSyncRange = null,
            firstUnenhancedHeight = null
        )
        advanceAndAssertState { it is AdapterState.Syncing && it.blocksRemained == 1_000_000L }

        statusFlow.value = Synchronizer.Status.SYNCED
        advanceAndAssertState { it is AdapterState.Synced }

        processorInfoFlow.value = CompactBlockProcessor.ProcessorInfo(
            networkBlockHeight = BlockHeight.new(3_500_001L),
            overallSyncRange = null,
            firstUnenhancedHeight = null
        )
        advanceUntilIdle()

        assertEquals(AdapterState.Synced, adapter.balanceState)
    }

    @Test
    fun onStatus_syncingAfterSynced_allowsFreshProgressUpdates() = runTest(dispatcher) {
        adapter = createAdapter()
        adapter.start()

        advanceAndAssertState { it is AdapterState.Syncing }

        statusFlow.value = Synchronizer.Status.SYNCED
        advanceAndAssertState { it is AdapterState.Synced }

        statusFlow.value = Synchronizer.Status.SYNCING
        advanceAndAssertState { it is AdapterState.Syncing }

        processorInfoFlow.value = CompactBlockProcessor.ProcessorInfo(
            networkBlockHeight = BlockHeight.new(3_500_000L),
            overallSyncRange = null,
            firstUnenhancedHeight = null
        )
        progressFlow.value = PercentDecimal(0.5f)

        advanceAndAssertState {
            it is AdapterState.Syncing && it.progress == 50.0 && it.blocksRemained == 500_000L
        }
    }

    @Test
    fun onStatus_syncingFromNonSyncingState_createsFreshSyncing() = runTest(dispatcher) {
        adapter = createAdapter()
        adapter.start()

        // Wait for subscribe() to process initial SYNCING status
        advanceAndAssertState { it is AdapterState.Syncing }

        // Move to a non-syncing state without triggering synchronizer recreation.
        statusFlow.value = Synchronizer.Status.DISCONNECTED
        advanceAndAssertState { it is AdapterState.NotSynced }

        // Transition to SYNCING — should create fresh Syncing (no progress)
        statusFlow.value = Synchronizer.Status.SYNCING
        advanceAndAssertState { it is AdapterState.Syncing }

        val state = adapter.balanceState as AdapterState.Syncing
        assertEquals("Fresh Syncing should have no progress", null, state.progress)
    }

    // --- Erase retry ---

    @Test
    fun eraseRetry_succeedsOnSecondAttempt() = runTest(dispatcher) {
        var eraseCallCount = 0
        coEvery {
            Synchronizer.erase(any(), any(), any())
        } answers {
            eraseCallCount++
            if (eraseCallCount == 1) throw IllegalStateException("synchronizer still active")
            true
        }

        adapter = createAdapter()

        capturedProcessorErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )
        advanceUntilIdle()

        coVerify {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
        assertEquals(2, eraseCallCount)
        verify(atLeast = 1) { mockSynchronizer.processorInfo }
    }

    // --- Self-heal restart after terminal STOPPED ---

    @Test
    fun stopped_whileForeground_restartsSynchronizer() = runTest(dispatcher) {
        every { backgroundManager.inForeground } returns true

        adapter = createAdapter(20L, 100L)
        adapter.start()
        advanceAndAssertState { it is AdapterState.Syncing }

        // Bounded drain to the first restart: the mock synchronizer's status flow never changes
        // on its own, so an unbounded advanceUntilIdle() here would busy-loop forever through
        // repeated self-heal restarts (each resubscription re-observes STOPPED and reschedules).
        statusFlow.value = Synchronizer.Status.STOPPED
        runCurrent()
        advanceTimeBy(20L)
        runCurrent()

        coVerify {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }

        // The resubscription above re-observes the still-STOPPED status and schedules another
        // self-heal restart. Move off STOPPED so that pending job is cancelled instead of firing
        // indefinitely (and potentially hanging runTest's own end-of-test scheduler drain).
        statusFlow.value = Synchronizer.Status.SYNCED
        advanceAndAssertState { it is AdapterState.Synced }
    }

    @Test
    fun stopped_thenSynced_thenStopped_restartsAgain() = runTest(dispatcher) {
        every { backgroundManager.inForeground } returns true
        var restartCount = 0
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } answers {
            restartCount++
            mockSynchronizer
        }

        adapter = createAdapter(20L, 100L)
        adapter.start()
        advanceAndAssertState { it is AdapterState.Syncing }

        // Bounded drain to the first restart (see stopped_whileForeground_restartsSynchronizer):
        // an unbounded advanceUntilIdle() here would busy-loop forever, since the resubscription
        // keeps re-observing the still-STOPPED status and rescheduling.
        statusFlow.value = Synchronizer.Status.STOPPED
        runCurrent()
        advanceTimeBy(20L)
        runCurrent()
        coVerify {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }

        statusFlow.value = Synchronizer.Status.SYNCED
        advanceAndAssertState { it is AdapterState.Synced }

        // SYNCED reset the backoff attempt, so the next restart is due after 20ms again.
        statusFlow.value = Synchronizer.Status.STOPPED
        runCurrent()
        advanceTimeBy(20L)
        runCurrent()
        coVerify(atLeast = 2) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
        assertTrue("Expected at least 2 restarts, got $restartCount", restartCount >= 2)

        // Move off STOPPED so the pending self-heal restart scheduled by the resubscription
        // above is cancelled instead of firing indefinitely.
        statusFlow.value = Synchronizer.Status.SYNCED
        advanceAndAssertState { it is AdapterState.Synced }
    }

    @Test
    fun zcashRestartDelayFor_exponentialWithCap() {
        assertEquals(20L, zcashRestartDelayFor(0, 20L, 100L))
        assertEquals(40L, zcashRestartDelayFor(1, 20L, 100L))
        assertEquals(80L, zcashRestartDelayFor(2, 20L, 100L))
        assertEquals(100L, zcashRestartDelayFor(3, 20L, 100L))
        assertEquals(100L, zcashRestartDelayFor(4, 20L, 100L))
    }

    @Test
    fun syncedResetsBackoffAttempt() = runTest(dispatcher) {
        every { backgroundManager.inForeground } returns true

        adapter = createAdapter(20L, 100L)
        adapter.start()
        advanceAndAssertState { it is AdapterState.Syncing }

        // First STOPPED -> restart cycle. Bounded drain (see
        // stopped_whileForeground_restartsSynchronizer): an unbounded advanceUntilIdle() here
        // would busy-loop forever, since the resubscription keeps re-observing the still-STOPPED
        // status and rescheduling.
        statusFlow.value = Synchronizer.Status.STOPPED
        runCurrent()
        advanceTimeBy(20L)
        runCurrent()
        coVerify {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }

        // Move off STOPPED (without resetting backoff, unlike SYNCING/SYNCED) so the next
        // STOPPED value change is a genuinely new emission, driving a second restart cycle. The
        // resubscription after the first restart already scheduled a second restart job with a
        // 40ms backoff (attempt 1); advance exactly to it instead of draining unbounded.
        statusFlow.value = Synchronizer.Status.DISCONNECTED
        statusFlow.value = Synchronizer.Status.STOPPED
        runCurrent()
        advanceTimeBy(40L)
        runCurrent()
        coVerify(atLeast = 2) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }

        // Moving to SYNCED cancels the pending self-heal restart scheduled by the resubscription
        // above (instead of letting it fire indefinitely) and resets the backoff attempt.
        statusFlow.value = Synchronizer.Status.SYNCED
        advanceAndAssertState { it is AdapterState.Synced }

        val restartAttempt = adapter.javaClass.getDeclaredField("restartAttempt")
            .apply { isAccessible = true }
            .getInt(adapter)
        assertEquals(0, restartAttempt)
    }

    @Test
    fun stopped_whileBackground_doesNotRestart() = runTest(dispatcher) {
        // backgroundManager is relaxed => inForeground defaults to false

        adapter = createAdapter(20L, 100L)
        adapter.start()
        advanceAndAssertState { it is AdapterState.Syncing }

        statusFlow.value = Synchronizer.Status.STOPPED
        advanceUntilIdle()

        coVerify(exactly = 0) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
    }

    @Test
    fun stopped_thenBackgroundBeforeBackoff_doesNotRestart() = runTest(dispatcher) {
        var foreground = true
        every { backgroundManager.inForeground } answers { foreground }

        adapter = createAdapter(300L, 300L)
        adapter.start()
        advanceAndAssertState { it is AdapterState.Syncing }

        statusFlow.value = Synchronizer.Status.STOPPED
        // Let onStatus() react to STOPPED and schedule the 300ms backoff job before flipping
        // foreground, mirroring the original real-time ordering (STOPPED observed while still
        // in foreground, background happens while the backoff is pending).
        runCurrent()
        foreground = false

        // Guard "not yet": right before the backoff delay elapses, no restart has fired.
        advanceTimeBy(299)
        runCurrent()
        coVerify(exactly = 0) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }

        // Guard "now": the backoff delay elapses, but the guard re-checks inForeground, which is
        // now false, so it still must not restart.
        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 0) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
    }

    @Test
    fun stopped_duringPollingSession_restartsInBackground() = runTest(dispatcher) {
        every { backgroundManager.inForeground } returns false

        adapter = createAdapter(20L, 100L)
        adapter.startForPolling()
        advanceAndAssertState { it is AdapterState.Syncing }

        // Bounded drain (see stopped_whileForeground_restartsSynchronizer): the mocked
        // Synchronizer.new() returns the same mockSynchronizer whose status is still STOPPED, so
        // an unbounded advanceUntilIdle() here would busy-loop forever on self-perpetuating
        // restarts.
        statusFlow.value = Synchronizer.Status.STOPPED
        runCurrent()
        advanceTimeBy(20L)
        runCurrent()

        coVerify {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }

        // Neutralize the still-armed self-heal chain so runTest's implicit end-of-test drain
        // doesn't hang: moving to SYNCED cancels the pending restart job and resets the backoff.
        statusFlow.value = Synchronizer.Status.SYNCED
        advanceAndAssertState { it is AdapterState.Synced }
    }

    @Test
    fun stopped_duringKeepAlive_restartsInBackground() = runTest(dispatcher) {
        every { backgroundManager.inForeground } returns false
        every { backgroundKeepAliveManager.isKeepAlive(BlockchainType.Zcash) } returns true

        adapter = createAdapter(20L, 100L)
        adapter.start()
        advanceAndAssertState { it is AdapterState.Syncing }

        // Bounded drain (see stopped_whileForeground_restartsSynchronizer): the mocked
        // Synchronizer.new() returns the same mockSynchronizer whose status is still STOPPED, so
        // an unbounded advanceUntilIdle() here would busy-loop forever on self-perpetuating
        // restarts.
        statusFlow.value = Synchronizer.Status.STOPPED
        runCurrent()
        advanceTimeBy(20L)
        runCurrent()

        coVerify {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }

        // Neutralize the still-armed self-heal chain so runTest's implicit end-of-test drain
        // doesn't hang: moving to SYNCED cancels the pending restart job and resets the backoff.
        statusFlow.value = Synchronizer.Status.SYNCED
        advanceAndAssertState { it is AdapterState.Synced }
    }

    @Test
    fun restartTrigger_duringCorruptionRecovery_doesNotCompete() = runTest(dispatcher) {
        every { backgroundManager.inForeground } returns true
        val bgStateFlow = MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.Unknown)
        every { backgroundManager.stateFlow } returns bgStateFlow

        val getAccountsStarted = CompletableDeferred<Unit>()
        val releaseGetAccounts = CompletableDeferred<Unit>()
        val recoverySynchronizer = createMockSynchronizer().also { synchronizer ->
            coEvery { synchronizer.getAccounts() } coAnswers {
                getAccountsStarted.complete(Unit)
                releaseGetAccounts.await()
                emptyList()
            }
        }
        coEvery {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        } returns recoverySynchronizer

        adapter = createAdapter(20L, 100L)
        adapter.start()
        advanceAndAssertState { it is AdapterState.Syncing }

        capturedProcessorErrorHandler?.invoke(
            SQLiteDatabaseCorruptException("database disk image is malformed")
        )
        advanceUntilIdle()
        assertTrue(
            "Recovery must be installing its synchronizer before competing triggers fire",
            getAccountsStarted.isCompleted
        )

        // Competing triggers fire while recovery is still in flight.
        bgStateFlow.value = BackgroundManagerState.EnterForeground
        statusFlow.value = Synchronizer.Status.STOPPED
        advanceUntilIdle()

        // Move the shared status off STOPPED before letting recovery finish installing its new
        // synchronizer. Otherwise recovery's own resubscription (which attaches once
        // createNewSynchronizer() completes, after recovering is cleared) would immediately
        // re-observe the stale STOPPED value and arm a genuine competing restart - both hanging
        // the drain below and breaking the exactly=1 assertion. Flipping to SYNCING here only
        // makes the resubscription call the harmless resetRestart() no-op.
        statusFlow.value = Synchronizer.Status.SYNCING
        releaseGetAccounts.complete(Unit)
        advanceUntilIdle()

        // Only recovery's own Synchronizer.new call should have happened - no competing restart.
        coVerify(exactly = 1) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(),
                walletInitMode = any(), setup = any(),
                isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
    }

    @Test
    fun enterBackground_pauseThenStopped_doesNotRestart() = runTest(dispatcher) {
        // Foreground + syncing, then the user backgrounds the app. Backgrounding pauses the adapter
        // (pauseSynchronizer -> closeSynchronizer cancels the onStatus subscriber BEFORE close()), so
        // the STOPPED that intentional close emits never reaches onStatus and must not restart.
        // inForeground is kept true on purpose: it removes the foreground guard as a confound so this
        // test locks the subscriber-cancellation itself — a regression that stopped cancelling the
        // subscriber would let STOPPED through and wrongly restart, failing this test.
        every { backgroundManager.inForeground } returns true
        val bgStateFlow = MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.Unknown)
        every { backgroundManager.stateFlow } returns bgStateFlow

        adapter = createAdapter(20L, 100L)
        adapter.start()
        advanceAndAssertState { it is AdapterState.Syncing }

        // Wait for the real pause path to run: closeSynchronizer() cancels the subscriber, then close().
        bgStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()
        verify { mockSynchronizer.close() }

        // The subscriber is now cancelled, so this STOPPED must not reach onStatus / schedule a restart.
        statusFlow.value = Synchronizer.Status.STOPPED
        runCurrent()

        // Guard "not yet": right before a wrongly-scheduled restart's 20ms backoff would fire.
        advanceTimeBy(19)
        runCurrent()
        coVerify(exactly = 0) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }

        // Guard "now": past the 20ms window - still no restart, since the subscriber never saw STOPPED.
        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 0) {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
    }

    private fun verifySynchronizerNew() {
        coVerify {
            Synchronizer.new(
                context = any(), zcashNetwork = any(), alias = any(),
                lightWalletEndpoint = any(), birthday = any(), walletInitMode = any(),
                setup = any(), isTorEnabled = any(), isExchangeRateEnabled = any()
            )
        }
    }

    private fun verifyRecoveryResubscribed() {
        verifySynchronizerNew()
        verify(atLeast = 1) { mockSynchronizer.processorInfo }
    }

    private fun createMockSynchronizer(): SdkSynchronizer {
        return mockk<SdkSynchronizer>(relaxed = true) {
            every { status } returns statusFlow
            every { progress } returns progressFlow
            every { walletBalances } returns walletBalancesFlow
            every { processorInfo } returns processorInfoFlow
            every { allTransactions } returns allTransactionsFlow
            every { coroutineScope } returns appScope
            every { latestHeight } returns null
        }
    }
}
