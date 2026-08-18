package cash.p.terminal.core.managers

import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.requiresTrezorPreparation
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.Wallet.ConnectionStatus
import com.m2049r.xmrwallet.offline.RawMoneroBroadcastResult
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.piratecash.monero.signer.ExternalSignerRegistration
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoneroKitWrapperRefreshTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)
    private val controlledRefreshCoordinatorJobs = mutableListOf<Job>()

    @After
    fun cancelControlledRefreshCoordinatorJobs() {
        controlledRefreshCoordinatorJobs.forEach(Job::cancel)
        controlledRefreshCoordinatorJobs.clear()
    }

    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    private val account = Account(
        id = "account-id",
        name = "Monero",
        type = AccountType.MnemonicMonero(
            words = emptyList(),
            password = "password",
            height = 1,
            walletInnerName = "wallet"
        ),
        origin = AccountOrigin.Created,
        level = 0
    )
    private val unsupportedAccount = account.copy(
        type = AccountType.EvmAddress("0x1234")
    )
    private val trezorAccount = account.copy(
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-key",
        ),
    )

    @Test
    fun refresh_syncingWallet_doesNotStopService() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service)

        wrapper.refresh()

        verify(exactly = 0) { service.stop(any()) }
    }

    @Test
    fun refresh_pausedWalletFailedResume_restartsWallet() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, unsupportedAccount)
        every { service.resume(wrapper) } returns false

        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        wrapper.pause()
        wrapper.refresh()

        verify(exactly = 1) { service.resume(wrapper) }
        verify(exactly = 1) { service.stop(false) }
    }

    @Test
    fun refreshHardwareKeyImagesWithProgress_reportsNativeProgressAndClearsObserver() {
        val events = mutableListOf<String>()
        val states = mutableListOf<AdapterState>()
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        var progressObserver: ((Long) -> Unit)? = null
        var fallbackCalls = 0
        every { service.setControlledRefreshProgressObserver(any()) } answers {
            events += "register"
            progressObserver = firstArg()
        }
        every { service.clearControlledRefreshProgressObserver() } answers { events += "clear" }

        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        wrapper.refreshHardwareKeyImagesWithProgress(
            cachedTotalHeight = 0,
            fallbackTotalHeight = {
                events += "fallback"
                fallbackCalls += 1
                1_000
            },
        ) {
            events += "refresh"
            progressObserver?.let { observer ->
                observer(400)
                states += wrapper.syncState.value
                observer(500)
                states += wrapper.syncState.value
            }
        }

        assertNotNull(progressObserver)
        assertEquals(
            listOf(
                AdapterState.Syncing(progress = 40.0, blocksRemained = 600),
                AdapterState.Syncing(progress = 50.0, blocksRemained = 500),
            ),
            states,
        )
        assertEquals(1, fallbackCalls)
        assertEquals(listOf("fallback", "register", "refresh", "clear"), events)
    }

    @Test
    fun refreshHardwareKeyImagesWithProgress_refreshFailureClearsObserver() {
        val events = mutableListOf<String>()
        val error = IllegalStateException("refresh failed")
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        every { service.setControlledRefreshProgressObserver(any()) } answers { events += "register" }
        every { service.clearControlledRefreshProgressObserver() } answers { events += "clear" }

        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        val thrown = try {
            wrapper.refreshHardwareKeyImagesWithProgress(
                cachedTotalHeight = 1_000,
                fallbackTotalHeight = { error("fallback should not be called") },
            ) {
                events += "refresh"
                throw error
            }
            null
        } catch (caught: Throwable) {
            caught
        }

        assertSame(error, thrown)
        assertEquals(listOf("register", "refresh", "clear"), events)
    }

    @Test
    fun rescan_hardwareGatewayWaiting_publishesSyncingBeforeNativeCallback() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        val coordinator = privateField(wrapper, "controlledRefreshCoordinator")
            .get(wrapper) as ControlledHardwareRefreshCoordinator
        val mutex = coordinatorMutex(coordinator)
        mutex.lock()

        try {
            assertSame(AdapterState.Synced, wrapper.syncState.value)
            val rescan = async(start = CoroutineStart.UNDISPATCHED) { wrapper.rescan(42) }

            assertTrue(wrapper.syncState.value is AdapterState.Syncing)
            verify(exactly = 1) { restoreSettingsManager.savePendingMoneroRescan(trezorAccount, 42) }
            wrapper.onRefreshed(wallet = null, full = true)
            assertTrue(wrapper.syncState.value is AdapterState.Syncing)
            privateField(wrapper, "controlledLiveRefreshPending").set(wrapper, true)
            privateField(wrapper, "controlledLiveRefreshCommitted").set(wrapper, true)
            wrapper.onRefreshed(wallet = null, full = true)
            assertSame(AdapterState.Synced, wrapper.syncState.value)
            rescan.cancelAndJoin()
        } finally {
            mutex.unlock()
        }
    }

    @Test
    fun abortControlledHardwareWallet_syncingRescanFailure_publishesNotSyncedWithSameError() {
        val error = IllegalStateException("Trezor rejected rescan")
        val wrapper = createWrapper(mockService(), trezorAccount)
        setSyncState(wrapper, AdapterState.Syncing())

        invokeAbortControlledHardwareWallet(wrapper, error)

        val state = wrapper.syncState.value as? AdapterState.NotSynced
        assertSame(error, state?.error)
    }

    @Test
    fun controlledRefreshCoordinator_failureAbortsBeforeSignerRelease() = runTest {
        val registration = mockk<ExternalSignerRegistration>(relaxed = true)
        val abort = mockk<() -> Unit>(relaxed = true)
        val coordinator = controlledRefreshCoordinator()

        assertTrue(
            runCatching {
                coordinator.execute(
                    testControlledRefreshOperation(
                        refresh = { aborter ->
                            withExternalSignerRegistration(registration) {
                                try {
                                    error("refresh failed")
                                } catch (error: Throwable) {
                                    aborter.abort(error)
                                    throw error
                                }
                            }
                        },
                        abort = { abort() },
                    ),
                )
            }.isFailure,
        )

        verifyOrder {
            abort.invoke()
            registration.release()
        }
    }

    @Test
    fun controlledRefreshCoordinator_concurrentCallers_createOneOperationAndJoin() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        var refreshCount = 0
        var finalizationCount = 0
        val operation = testControlledRefreshOperation(
            refresh = {
                refreshCount += 1
                refreshStarted.complete(Unit)
                refreshGate.await()
            },
            finalize = { finalizationCount += 1 },
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) { coordinator.execute(operation) }
        refreshStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { coordinator.execute(operation) }

        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        assertEquals(1, refreshCount)
        refreshGate.complete(Unit)

        first.await()
        second.await()
        assertEquals(1, finalizationCount)
    }

    @Test
    fun controlledRefreshCoordinator_lastCancelledWaiter_releasesInNonCancellableContext() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        var abortError: Throwable? = null
        val operation = testControlledRefreshOperation(
            refresh = {
                refreshStarted.complete(Unit)
                refreshGate.await()
            },
            abort = { abortError = it },
        )

        val waiter = async(start = CoroutineStart.UNDISPATCHED) { coordinator.execute(operation) }
        refreshStarted.await()
        val mutex = coordinatorMutex(coordinator)
        mutex.lock()
        waiter.cancel()
        runCurrent()

        assertEquals(null, abortError)
        mutex.unlock()
        waiter.join()
        runCurrent()

        assertTrue(abortError is CancellationException)
    }

    @Test
    fun controlledRefreshCoordinator_cancelDuringFinalization_completesAbortBeforeFreshRetry() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val finalizationStarted = CompletableDeferred<Unit>()
        val abortFinished = CompletableDeferred<Unit>()
        val releaseFinalization = CompletableDeferred<Unit>()
        var retryRefreshCount = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.execute(
                testControlledRefreshOperation(
                    finalize = {
                        finalizationStarted.complete(Unit)
                        releaseFinalization.await()
                    },
                    abort = { abortFinished.complete(Unit) },
                ),
            )
        }
        finalizationStarted.await()

        first.cancel()
        runCurrent()
        abortFinished.await()
        first.join()

        val retry = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.execute(
                testControlledRefreshOperation(refresh = { retryRefreshCount += 1 }),
            )
        }
        retry.await()
        assertEquals(1, retryRefreshCount)
    }

    @Test
    fun controlledRefreshCoordinator_cancelDuringFinalization_abortsBeforeQueuedLifecycleOperation() =
        runTest {
            val coordinator = controlledRefreshCoordinator()
            val lifecycleMutex = Mutex()
            val finalizationStarted = CompletableDeferred<Unit>()
            val releaseFinalization = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val operation = object : ControlledHardwareRefreshOperation {
                override suspend fun run(aborter: ControlledHardwareRefreshAborter) {
                    lifecycleMutex.withLock {
                        try {
                            finalizationStarted.complete(Unit)
                            releaseFinalization.await()
                        } catch (error: Throwable) {
                            aborter.abort(error)
                            throw error
                        }
                    }
                }

                override fun abort(error: Throwable) {
                    order += "abort"
                }
            }
            val refresh = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.execute(operation)
            }
            finalizationStarted.await()
            val lifecycleOperation = async(start = CoroutineStart.UNDISPATCHED) {
                lifecycleMutex.withLock {
                    order += "lifecycle"
                }
            }

            refresh.cancel()
            refresh.join()
            lifecycleOperation.await()

            assertEquals(listOf("abort", "lifecycle"), order)
        }

    @Test
    fun controlledRefreshCoordinator_cancelWhileWaitingForLifecycle_doesNotAbortUnstartedOperation() =
        runTest {
            val coordinator = controlledRefreshCoordinator()
            val lifecycleMutex = Mutex(locked = true)
            val runStarted = CompletableDeferred<Unit>()
            var abortCount = 0
            val operation = object : ControlledHardwareRefreshOperation {
                override suspend fun run(aborter: ControlledHardwareRefreshAborter) {
                    runStarted.complete(Unit)
                    lifecycleMutex.withLock { }
                }

                override fun abort(error: Throwable) {
                    abortCount += 1
                }
            }
            val refresh = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.execute(operation)
            }
            runStarted.await()

            refresh.cancel()
            refresh.join()
            lifecycleMutex.unlock()

            assertEquals(0, abortCount)
        }

    @Test
    fun controlledRefreshCoordinator_staleWaitersDoNotKeepReplacementOperationAlive() = runTest {
        val coordinator = controlledRefreshCoordinator()
        val staleScheduler = TestCoroutineScheduler()
        val staleSupervisor = SupervisorJob()
        val staleScope = CoroutineScope(staleSupervisor + StandardTestDispatcher(staleScheduler))
        val firstStarted = CompletableDeferred<Unit>()
        val firstGate = CompletableDeferred<Unit>()
        val replacementStarted = CompletableDeferred<Unit>()
        val replacementGate = CompletableDeferred<Unit>()
        var replacementAbortCount = 0

        try {
            val firstOperation = testControlledRefreshOperation(
                refresh = {
                    firstStarted.complete(Unit)
                    firstGate.await()
                },
            )
            staleScope.async { coordinator.execute(firstOperation) }
            staleScope.async { coordinator.execute(firstOperation) }
            staleScheduler.runCurrent()
            runCurrent()
            firstStarted.await()

            firstGate.complete(Unit)
            runCurrent()

            val replacement = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.execute(
                    testControlledRefreshOperation(
                        refresh = {
                            replacementStarted.complete(Unit)
                            replacementGate.await()
                        },
                        abort = { replacementAbortCount += 1 },
                    ),
                )
            }
            replacementStarted.await()

            replacement.cancel()
            replacement.join()
            runCurrent()

            assertEquals(1, replacementAbortCount)
        } finally {
            firstGate.complete(Unit)
            replacementGate.complete(Unit)
            staleScheduler.runCurrent()
            runCurrent()
            staleSupervisor.cancel()
        }
    }

    @Test
    fun abortControlledHardwareWallet_leavesRetryableKeyImageSyncReadiness() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        invokeAbortControlledHardwareWallet(wrapper, IllegalStateException("refresh failed"))

        assertSame(MoneroSpendReadiness.NeedsKeyImageSync, wrapper.spendReadiness.value)
    }

    @Test
    fun openPausedHardwareWalletForRefresh_armsSessionBeforeSynchronousStartCallback() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        var sessionDuringStart: Long? = null

        wrapper.openPausedHardwareWalletForRefresh {
            assertTrue(privateField(wrapper, "startInProgress").getBoolean(wrapper))
            sessionDuringStart = activeReconciliationSession(wrapper)
        }

        assertTrue(sessionDuringStart != null)
        assertFalse(privateField(wrapper, "startInProgress").getBoolean(wrapper))
        assertTrue(privateField(wrapper, "isStarted").getBoolean(wrapper))
        assertTrue(privateField(wrapper, "isPaused").getBoolean(wrapper))
    }

    @Test
    fun controlledRefreshCoordinator_failureAbortsThenRetryCreatesFreshOperation() = runTest {
        val coordinator = controlledRefreshCoordinator()
        var abortCount = 0
        var retryRefreshCount = 0
        var retryFinalizationCount = 0

        assertTrue(
            runCatching {
                coordinator.execute(
                    testControlledRefreshOperation(
                        refresh = { throw IllegalStateException("refresh failed") },
                        abort = { abortCount += 1 },
                    ),
                )
            }.isFailure,
        )

        coordinator.execute(
            testControlledRefreshOperation(
                refresh = { retryRefreshCount += 1 },
                finalize = { retryFinalizationCount += 1 },
            ),
        )

        assertEquals(1, abortCount)
        assertEquals(1, retryRefreshCount)
        assertEquals(1, retryFinalizationCount)
    }

    @Test
    fun controlledRefreshFinalization_missingCallback_isRetryableHardwareFailure() = runTest {
        val failure = runCatching {
            awaitControlledRefreshFinalization(CompletableDeferred(), timeoutMillis = 1)
        }.exceptionOrNull()

        assertTrue(failure is HardwareWalletOperationException)
    }

    @Test
    fun hardwareStartupRecovery_explicitColdRecoveryPending_resumesExplicitRecovery() {
        assertSame(
            HardwareStartupRecovery.ExplicitColdRecovery,
            hardwareStartupRecovery(MoneroSpentReconciliationState.ExplicitColdRecoveryPending),
        )
    }

    @Test
    fun startAccountService_initialCatchUp_releasesGatewayBeforeOrdinaryResume() = runTest {
        val events = mutableListOf<String>()
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true)
        val gateway = mockk<MoneroTrezorOperationGateway>()
        val wrapper = spyk(createWrapper(service, trezorAccount, gateway = gateway))
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.LiveRefreshPending
        every { status.isOk } returns true
        every { service.startPaused("wallet", "password") } answers {
            events += "start_paused"
            status
        }
        every { service.resume(wrapper) } answers {
            events += "resume"
            true
        }
        coEvery { gateway.execute<Any?>(trezorAccount, any()) } coAnswers {
            events += "gateway_enter"
            try {
                secondArg<(String) -> Any?>().invoke("wallet-key")
            } finally {
                events += "gateway_exit"
            }
        }

        wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)

        assertEquals(listOf("gateway_enter", "start_paused", "gateway_exit", "resume"), events)
        verify(exactly = 0) { wrapper.refreshHardwareKeyImagesLeaseOwned(any(), any()) }
        verify(exactly = 1) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.LiveRefreshPending,
            )
        }
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "controlledLiveRefreshPending").getBoolean(wrapper))
        assertFalse(privateField(wrapper, "controlledLiveRefreshCommitted").getBoolean(wrapper))
    }

    @Test
    fun startAccountService_readyWallet_refreshesWhileGatewayOwned() = runTest {
        val events = mutableListOf<String>()
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true)
        val gateway = mockk<MoneroTrezorOperationGateway>()
        val wrapper = spyk(createWrapper(service, trezorAccount, gateway = gateway))
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.Ready
        every { status.isOk } returns true
        every { service.startPaused("wallet", "password") } answers {
            events += "start_paused"
            status
        }
        every { wrapper.refreshHardwareKeyImagesLeaseOwned(any(), any()) } answers {
            events += "controlled_refresh"
        }
        coEvery { gateway.execute<Any?>(trezorAccount, any()) } coAnswers {
            events += "gateway_enter"
            try {
                secondArg<(String) -> Any?>().invoke("wallet-key")
            } finally {
                events += "gateway_exit"
            }
        }

        wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)

        assertEquals(listOf("gateway_enter", "start_paused", "controlled_refresh", "gateway_exit"), events)
        verify(exactly = 1) { wrapper.refreshHardwareKeyImagesLeaseOwned(any(), any()) }
        verify(exactly = 0) { service.resume(wrapper) }
        verify(exactly = 1) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.LiveRefreshPending,
            )
        }
        assertTrue(privateField(wrapper, "controlledLiveRefreshPending").getBoolean(wrapper))
        assertTrue(privateField(wrapper, "controlledLiveRefreshCommitted").getBoolean(wrapper))
    }

    @Test
    fun startAccountService_migrationReplayFailure_remainsPendingAndRetries() = runTest {
        val service = mockService()
        val status = mockk<Wallet.Status>(relaxed = true)
        val gateway = mockk<MoneroTrezorOperationGateway>()
        val wrapper = spyk(createWrapper(service, trezorAccount, gateway = gateway))
        val events = mutableListOf<String>()
        var refreshAttempts = 0
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.MigrationReplayRequired
        every { status.isOk } returns true
        every { service.startPaused("wallet", "password") } returns status
        every { wrapper.refreshHardwareKeyImagesLeaseOwned(any(), any()) } answers {
            events += "refresh"
            if (refreshAttempts++ == 0) error("refresh failed")
        }
        coEvery { gateway.execute<Any?>(trezorAccount, any()) } coAnswers {
            events += "gateway"
            secondArg<(String) -> Any?>().invoke("wallet-key")
        }
        every {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.MigrationReplayPending,
            )
        } answers { events += "pending" }

        assertTrue(
            runCatching {
                wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)
            }.isFailure,
        )
        wrapper.startAccountService("wallet", "password", fixIfCorruptedFile = true)

        assertEquals(
            listOf("pending", "gateway", "refresh", "pending", "gateway", "refresh"),
            events,
        )
        verify(exactly = 0) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.Ready,
            )
        }
    }

    @Test
    fun normalizedLiveRefreshState_durableRecoveryObligationsAreNeverDowngraded() {
        assertSame(
            MoneroSpentReconciliationState.MigrationReplayPending,
            MoneroSpentReconciliationState.MigrationReplayPending.normalizedLiveRefreshState(),
        )
        assertSame(
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending,
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending.normalizedLiveRefreshState(),
        )
    }

    @Test
    fun explicitColdRecovery_doesNotRequireControlledLiveRefreshFinalization() {
        assertFalse(
            MoneroSpentReconciliationState.ExplicitColdRecoveryPending
                .requiresControlledRefreshFinalization(),
        )
        assertTrue(
            MoneroSpentReconciliationState.LiveRefreshPending
                .requiresControlledRefreshFinalization(),
        )
        assertTrue(
            MoneroSpentReconciliationState.MigrationReplayPending
                .requiresControlledRefreshFinalization(),
        )
    }

    @Test
    fun reconciliationFailure_remainsActionableForTrezorPreparation() {
        assertTrue(MoneroSpendReadiness.ReconciliationFailed.requiresTrezorPreparation())
        assertTrue(MoneroSpendReadiness.NeedsKeyImageSync.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.Syncing.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.CheckingKeyImages.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.ReconcilingSpentStatus.requiresTrezorPreparation())
        assertFalse(MoneroSpendReadiness.Ready.requiresTrezorPreparation())
    }

    @Test
    fun handleStartFailure_explicitColdRecovery_clearsSessionMarkerBeforeRetry() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        setExplicitColdRecoveryPending(wrapper, true)

        runCatching {
            invokeHandleStartFailure(wrapper, IllegalStateException("cold recovery failed"))
        }

        assertFalse(explicitColdRecoveryPending(wrapper))
    }

    @Test
    fun saveSynced_syncingAfterQueuedSyncedEvent_doesNotStoreOrClearRescan() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service)
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Syncing())

        val stored = wrapper.saveSynced()

        assertFalse(stored)
        verify(exactly = 0) { service.pause() }
        verify(exactly = 0) {
            restoreSettingsManager.clearPendingMoneroRescan(account)
        }
    }

    @Test
    fun abandonFaultedWallet_readyHardwareWallet_invalidatesStateAndOwnership() {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        val failure = HardwareWalletOperationException(
            HardwareWalletErrorCode.StoreFailed,
            "store failed",
        )
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        invokeAbandonFaultedWallet(wrapper, failure)

        verify(exactly = 1) { service.abandonFaultedWallet() }
        assertSame(failure, (wrapper.syncState.value as AdapterState.NotSynced).error)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
    }

    @Test
    fun startFailure_hardwareCleanupFailureRetainsNativeOwnershipButFailsClosed() {
        val wrapper = spyk(createWrapper(mockService(), trezorAccount), recordPrivateCalls = true)
        val failure = IllegalStateException("start failed")
        every { wrapper["closeHardwareWalletAfterFailedStart"](failure) } returns false
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        val thrown = runCatching { invokeHandleStartFailure(wrapper, failure) }.exceptionOrNull()

        assertSame(failure, (thrown as InvocationTargetException).targetException)
        assertTrue(privateField(wrapper, "isStarted").getBoolean(wrapper))
        assertTrue(privateField(wrapper, "isPaused").getBoolean(wrapper))
        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
        assertSame(failure, (wrapper.syncState.value as AdapterState.NotSynced).error)
    }

    @Test
    fun startFailure_hardwareGatewayPreflightFailure_exposesRetryableTerminalReadiness() {
        val failure = IllegalStateException("device unavailable")
        val wrapper = spyk(createWrapper(mockService(), trezorAccount), recordPrivateCalls = true)
        every { wrapper["closeHardwareWalletAfterFailedStart"](failure) } returns true

        runCatching { invokeHandleStartFailure(wrapper, failure) }

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
    }

    @Test
    fun stop_readyHardwareWallet_invalidatesReadinessBeforeItCanReopen() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        every { service.stop(true) } returns true
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        wrapper.stop()

        verify(exactly = 1) { service.stop(true) }
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
    }

    @Test
    fun send_pendingHardwareReconciliation_failsBeforePauseGatewayOrPreparation() = runTest {
        val service = mockService()
        val gateway = mockk<MoneroTrezorOperationGateway>(relaxed = true)
        val wrapper = createWrapper(service, trezorAccount, gateway = gateway)
        every { restoreSettingsManager.moneroSpentReconciliationState(trezorAccount) } returns
            MoneroSpentReconciliationState.LiveRefreshPending
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        assertTrue(runCatching { wrapper.send(BigDecimal.ONE, "address", null) }.isFailure)

        verify(exactly = 0) { service.pause() }
        verify(exactly = 0) { service.prepareTransaction(any()) }
        coVerify(exactly = 0) { gateway.execute<Any?>(trezorAccount, any()) }
    }

    @Test
    fun submitSignedRawTransaction_pendingHardwareReconciliation_neverSubmitsOrLoadsWallet() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        every {
            restoreSettingsManager.moneroSpentReconciliationState(trezorAccount)
        } returns MoneroSpentReconciliationState.LiveRefreshPending
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.ReconcilingSpentStatus)

        val failure = try {
            wrapper.submitSignedRawTransaction(byteArrayOf(1, 2, 3))
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertTrue(failure != null)
        coVerify(exactly = 0) { service.submitSignedRawTransaction(any()) }
    }

    @Test
    fun completedSubmittedRawTransaction_resumeFailureFailsClosedButPreservesResult() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        val result = RawMoneroBroadcastResult.Submitted("tx-id")
        val failure = IllegalStateException("resume failed")
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)
        val actual = wrapper.completeHardwareOperation(result, failure, true)
        assertSame(result, actual)
        assertSame(failure, (wrapper.syncState.value as AdapterState.NotSynced).error)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
    }

    @Test
    fun stop_callbackDuringNativeClose_cannotRecreateReconciliationWork() = runTest {
        val service = mockService()
        val wrapper = createWrapper(service, trezorAccount)
        var callbackAccepted: Boolean? = null
        every { service.stop(true) } answers {
            callbackAccepted = wrapper.onRefreshed(wallet = null, full = true)
            true
        }
        setStarted(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)
        invokeActivateReconciliationSession(wrapper)

        wrapper.stop()

        assertFalse(callbackAccepted ?: true)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        assertFalse(privateField(wrapper, "isStarted").getBoolean(wrapper))
    }

    @Test
    fun reconciliationReadiness_legacyAndPendingNeverExposeReady() {
        assertFalse(
            canExposeMoneroSpendReady(
                hardwareWallet = true,
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                durableState = MoneroSpentReconciliationState.LiveRefreshPending,
            ),
        )
        assertTrue(
            canExposeMoneroSpendReady(
                hardwareWallet = true,
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                durableState = MoneroSpentReconciliationState.Ready,
            ),
        )
    }

    @Test
    fun reconciliationCallback_matchingIsGenerationBased() {
        assertFalse(isMatchingReconciliationCallback(4, 3))
        assertTrue(isMatchingReconciliationCallback(4, 4))
    }

    @Test
    fun walletStartedCallback_activeSessionIsAcceptedDuringItsStartOrAfterward() {
        assertTrue(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = true,
                isStarted = false,
                startInProgress = true,
            ),
        )
        assertTrue(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = true,
                isStarted = true,
                startInProgress = false,
            ),
        )
        assertFalse(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = true,
                isStarted = false,
                startInProgress = false,
            ),
        )
        assertFalse(
            canAcceptWalletStartedCallback(
                hasActiveCurrentSession = false,
                isStarted = false,
                startInProgress = true,
            ),
        )
    }

    @Test
    fun walletStartedSyncState_nativeConnectedBeforeServiceStatePublished_staysSyncingUntilHealthy() {
        assertTrue(
            walletStartedSyncState(
                healthyWalletHealth(
                    serviceConnectionIsConnected = false,
                    walletIsSynchronized = false,
                ),
            ) is AdapterState.Syncing,
        )
        assertSame(
            AdapterState.Synced,
            walletStartedSyncState(healthyWalletHealth()),
        )
    }

    @Test
    fun nativeHealthFailure_serviceStateLagIsTransientButNativeFailuresRemainErrors() {
        assertFalse(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(
                    serviceConnectionIsConnected = false,
                    walletIsSynchronized = false,
                ),
            ),
        )
        assertTrue(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(callbackWalletIsCurrent = false),
            ),
        )
        assertTrue(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(nativeStatusIsOk = false),
            ),
        )
        assertTrue(
            hasMoneroNativeHealthFailure(
                healthyWalletHealth(nativeConnectionIsConnected = false),
            ),
        )
    }

    @Test
    fun reconciliationCallback_failedNativeStatusIsConsumedFailClosed() {
        val callbackIsSuccessful = canFinalizeSpentReconciliation(
            healthyWalletHealth(nativeStatusIsOk = false),
        )

        assertEquals(
            ReconciliationCallbackDisposition.FailClosed,
            reconciliationCallbackDisposition(
                awaitingGeneration = 4,
                callbackGeneration = 4,
                callbackIsSuccessful = callbackIsSuccessful,
            ),
        )
    }

    @Test
    fun reconciliationCallback_requiresSuccessfulNativeStatusConnectionAndSynchronization() {
        assertFalse(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(nativeStatusIsOk = false),
            ),
        )
        assertFalse(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(serviceConnectionIsConnected = false),
            ),
        )
        assertFalse(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(walletIsSynchronized = false),
            ),
        )
        assertTrue(
            canFinalizeSpentReconciliation(
                healthyWalletHealth(),
            ),
        )
    }

    @Test
    fun coldKeyImageSync_trustedAndUntrustedResultsTakeDifferentSafePaths() {
        assertEquals(
            ColdKeyImageSyncNextStep.TrustedReady,
            coldKeyImageSyncNextStep(
                spentStatusVerified = true,
                hasUnknownKeyImages = false,
            ),
        )
        assertEquals(
            ColdKeyImageSyncNextStep.PreserveKeyImagesRescan,
            coldKeyImageSyncNextStep(
                spentStatusVerified = false,
                hasUnknownKeyImages = false,
            ),
        )
    }

    @Test
    fun coldKeyImageSync_trustedResultWithUnknownKeyImagesRemainsFailClosed() {
        assertEquals(
            ColdKeyImageSyncNextStep.NeedsKeyImageSync,
            coldKeyImageSyncNextStep(
                spentStatusVerified = true,
                hasUnknownKeyImages = true,
            ),
        )
    }

    @Test
    fun trustedKeyImageSyncFinalization_onlyReadyCanAuthorizeSpending() {
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.Ready,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                syncState = AdapterState.Synced,
            ),
        )
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.ReconciliationFailed,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(nativeStatusIsOk = false),
                hasUnknownKeyImages = false,
                syncState = AdapterState.Synced,
            ),
        )
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.ReconciliationFailed,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
                syncState = AdapterState.Syncing(),
            ),
        )
        assertSame(
            TrustedKeyImageSyncFinalizationOutcome.NeedsKeyImageSync,
            trustedKeyImageSyncFinalizationOutcome(
                health = healthyWalletHealth(nativeStatusIsOk = false),
                hasUnknownKeyImages = true,
                syncState = AdapterState.Syncing(),
            ),
        )
    }

    @Test
    fun spentStatusRequestRetry_isWiredToTerminalFailure() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)

        wrapper.applySpentStatusRequestResult(session, MoneroSpentStatusRequestResult.Retry)

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
    }

    @Test
    fun spentStatusRequestNeedsKeyImageSync_isTerminalAndClearsOperation() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        assertTrue(reconciler.beginRecovery(session) != null)

        wrapper.applySpentStatusRequestResult(session, MoneroSpentStatusRequestResult.NeedsKeyImageSync)

        assertSame(MoneroSpendReadiness.NeedsKeyImageSync, wrapper.spendReadiness.value)
        assertFalse(reconciler.hasActiveOperation(session))
    }

    @Test
    fun terminalReadiness_clearsOnlyItsMatchingKeyImageSyncMarker() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)

        listOf(
            MoneroSpendReadiness.Ready,
            MoneroSpendReadiness.NeedsKeyImageSync,
            MoneroSpendReadiness.ReconciliationFailed,
        ).forEach { terminalReadiness ->
                setKeyImageSyncSession(wrapper, session)
                invokeSetSpendReadinessForSession(wrapper, session, terminalReadiness)

                assertSame(terminalReadiness, wrapper.spendReadiness.value)
                assertEquals(null, keyImageSyncSession(wrapper))
            }

        invokeActivateReconciliationSession(wrapper)
        val newerSession = activeReconciliationSession(wrapper)
        setKeyImageSyncSession(wrapper, newerSession)

        invokeSetSpendReadinessForSession(wrapper, session, MoneroSpendReadiness.Ready)

        assertEquals(newerSession, keyImageSyncSession(wrapper))
    }

    @Test
    fun postSyncReadinessFailure_isTerminalWhenKeyImageSyncWasBegun() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)
        setKeyImageSyncSession(wrapper, session)

        invokeFailClosedAfterPostSyncError(wrapper, session, IllegalStateException("probe failed"))

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
        assertEquals(null, keyImageSyncSession(wrapper))
    }

    @Test
    fun keyImageSync_preservingRescanProgress_keepsAwaitedReconciliationAlive() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        val session = checkNotNull(reconciler.activeSession())
        val generation = checkNotNull(reconciler.beginRequest(session))
        assertTrue(reconciler.markAwaitingCallback(session, generation))
        setKeyImageSyncSession(wrapper, session)
        setSyncState(wrapper, AdapterState.Syncing())

        assertTrue(
            wrapper.applyHardwareReadinessSnapshot(
                session = session,
                health = healthyWalletHealth(walletIsSynchronized = false),
                hasUnknownKeyImages = null,
            ),
        )

        assertSame(MoneroSpendReadiness.ReconcilingSpentStatus, wrapper.spendReadiness.value)
        assertTrue(reconciler.hasActiveOperation(session))
        assertEquals(generation, reconciler.awaitingCallbackGeneration(session))
        assertEquals(session, keyImageSyncSession(wrapper))

        setSyncState(wrapper, AdapterState.Synced)
        assertFalse(
            wrapper.applyHardwareReadinessSnapshot(
                session = session,
                health = healthyWalletHealth(),
                hasUnknownKeyImages = false,
            ),
        )
        assertEquals(generation, reconciler.awaitingCallbackGeneration(session))
    }

    @Test
    fun onRefreshed_staleWalletProvenanceLeavesCurrentSessionUntouched() {
        val wrapper = createWrapper(
            service = mockService(),
            account = trezorAccount,
            healthReader = fakeHealthReader(healthyWalletHealth(callbackWalletIsCurrent = false)),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSyncState(wrapper, AdapterState.Synced)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        assertFalse(wrapper.onRefreshed(wallet = null, full = true))

        assertSame(AdapterState.Synced, wrapper.syncState.value)
        assertSame(MoneroSpendReadiness.Ready, wrapper.spendReadiness.value)
    }

    @Test
    fun onRefreshed_fullyHandledSoftwareWalletCallbackReturnsTrue() {
        val wrapper = createWrapper(
            service = mockService(),
            healthReader = fakeHealthReader(healthyWalletHealth()),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        assertTrue(wrapper.onRefreshed(wallet = null, full = true))
    }

    @Test
    fun refreshCallbackReturn_hardwareTerminalPathsAreHandledButRetryablePathsAreNot() {
        assertTrue(
            isRefreshCallbackFullyHandled(
                hardwareAccount = true,
                hasActiveReconciliationOperation = false,
                spendReadiness = MoneroSpendReadiness.Ready,
            ),
        )
        assertTrue(
            isRefreshCallbackFullyHandled(
                hardwareAccount = true,
                hasActiveReconciliationOperation = false,
                spendReadiness = MoneroSpendReadiness.NeedsKeyImageSync,
            ),
        )
        assertTrue(
            isRefreshCallbackFullyHandled(
                hardwareAccount = true,
                hasActiveReconciliationOperation = false,
                spendReadiness = MoneroSpendReadiness.ReconciliationFailed,
            ),
        )
        assertFalse(
            isRefreshCallbackFullyHandled(
                hardwareAccount = true,
                hasActiveReconciliationOperation = false,
                spendReadiness = MoneroSpendReadiness.ReconcilingSpentStatus,
            ),
        )
        assertFalse(
            isRefreshCallbackFullyHandled(
                hardwareAccount = true,
                hasActiveReconciliationOperation = true,
                spendReadiness = MoneroSpendReadiness.Ready,
            ),
        )
    }

    @Test
    fun reconciliationFailure_isTerminalFailClosedReadiness() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        val session = checkNotNull(reconciler.activeSession())

        invokeFailClosedAfterReconciliationError(wrapper, session, IllegalStateException("store failed"))

        assertSame(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
        assertFalse(reconciler.hasActiveOperation(session))
        assertFalse(
            isHardwareSpendLifecycleReady(
                syncState = AdapterState.Synced,
                durableState = MoneroSpentReconciliationState.LiveRefreshPending,
                spendReadiness = wrapper.spendReadiness.value,
            ),
        )
    }

    @Test
    fun onRefreshed_awaitedConnectedNativeStatusFailure_remainsRetryable() {
        val service = mockService()
        val nativeStatusError = "rescan blockchain failed"
        var health = healthyWalletHealth(
            nativeStatusIsOk = false,
            nativeStatusError = nativeStatusError,
        )
        val wrapper = createWrapper(
            service = service,
            account = trezorAccount,
            healthReader = object : MoneroWalletHealthReader {
                override fun snapshot(callbackWallet: Wallet?) = health
            },
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        val session = checkNotNull(reconciler.activeSession())
        setKeyImageSyncSession(wrapper, session)
        val generation = checkNotNull(reconciler.beginRequest(session))
        assertTrue(reconciler.markAwaitingCallback(session, generation))

        assertFalse(wrapper.onRefreshed(wallet = null, full = true))

        assertTrue(wrapper.syncState.value is AdapterState.Syncing)
        assertSame(MoneroSpendReadiness.ReconcilingSpentStatus, wrapper.spendReadiness.value)
        assertTrue(reconciler.hasActiveOperation(session))
        assertEquals(generation, reconciler.awaitingCallbackGeneration(session))
        assertEquals(session, keyImageSyncSession(wrapper))
        verify(exactly = 0) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.Ready,
            )
        }

        health = healthyWalletHealth()
        assertSame(
            ReconciliationCallbackDisposition.Finalize,
            reconciler.callbackDisposition(
                session = session,
                generation = generation,
                callbackIsSuccessful = canFinalizeSpentReconciliation(health),
            ),
        )
    }

    @Test
    fun onRefreshed_unsynchronizedNativeStatusFailure_preservesNativeError() {
        val nativeStatusError = "rescan blockchain failed"
        val wrapper = createWrapper(
            service = mockService(),
            account = trezorAccount,
            healthReader = fakeHealthReader(
                healthyWalletHealth(
                    nativeStatusIsOk = false,
                    walletIsSynchronized = false,
                    nativeStatusError = nativeStatusError,
                ),
            ),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        wrapper.onRefreshed(wallet = null, full = true)

        val state = wrapper.syncState.value as? AdapterState.NotSynced
        assertEquals(nativeStatusError, state?.error?.message)
    }

    @Test
    fun onRefreshed_connectedSynchronizedNativeStatusFailure_keepsSyncPresentationFailClosed() {
        val wrapper = createWrapper(
            service = mockService(),
            account = trezorAccount,
            healthReader = fakeHealthReader(
                healthyWalletHealth(
                    nativeStatusIsOk = false,
                    nativeStatusError = "Device state not initialized",
                ),
            ),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)

        assertFalse(wrapper.onRefreshed(wallet = null, full = true))

        val state = wrapper.syncState.value as? AdapterState.NotSynced
        assertEquals("Device state not initialized", state?.error?.message)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        verify(exactly = 0) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.Ready,
            )
        }
    }

    @Test
    fun terminalReady_transientUnhealthyThenHealthyCallback_canRestoreDurableReady() {
        val wrapper = createWrapper(
            service = mockService(),
            account = trezorAccount,
        )
        every {
            restoreSettingsManager.moneroSpentReconciliationState(trezorAccount)
        } returns MoneroSpentReconciliationState.Ready
        invokeActivateReconciliationSession(wrapper)
        val session = activeReconciliationSession(wrapper)
        setKeyImageSyncSession(wrapper, session)
        invokeSetSpendReadinessForSession(wrapper, session, MoneroSpendReadiness.Ready)
        setSyncState(wrapper, AdapterState.Synced)

        wrapper.applyHardwareReadinessSnapshot(
            session,
            healthyWalletHealth(nativeStatusIsOk = false),
            hasUnknownKeyImages = null,
        )
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)

        setSyncState(wrapper, AdapterState.Synced)
        wrapper.applyHardwareReadinessSnapshot(
            session,
            healthyWalletHealth(),
            hasUnknownKeyImages = false,
        )

        assertSame(MoneroSpendReadiness.Ready, wrapper.spendReadiness.value)
    }

    @Test
    fun lifecycleActivationAndDeactivation_invalidateKeyImageSyncMarker() {
        val wrapper = createWrapper(mockService(), trezorAccount)
        invokeActivateReconciliationSession(wrapper)
        setKeyImageSyncSession(wrapper, activeReconciliationSession(wrapper))

        invokeDeactivateReconciliationSession(wrapper)
        assertEquals(null, keyImageSyncSession(wrapper))

        invokeActivateReconciliationSession(wrapper)
        assertEquals(null, keyImageSyncSession(wrapper))
    }

    @Test
    fun onRefreshed_activeHardwareSessionWithNativeWrongVersion_failsClosedWithoutReadyPersistence() {
        val service = mockService()
        val wrapper = createWrapper(
            service = service,
            account = trezorAccount,
            healthReader = fakeHealthReader(
                healthyWalletHealth(
                    nativeConnectionIsConnected = false,
                    nativeConnectionStatus = ConnectionStatus.ConnectionStatus_WrongVersion,
                ),
            ),
        )
        setStarted(wrapper)
        invokeActivateReconciliationSession(wrapper)
        setSpendReadiness(wrapper, MoneroSpendReadiness.Ready)

        assertFalse(wrapper.onRefreshed(wallet = null, full = true))

        assertTrue(wrapper.syncState.value is AdapterState.NotSynced)
        assertSame(MoneroSpendReadiness.Syncing, wrapper.spendReadiness.value)
        verify(exactly = 0) {
            restoreSettingsManager.saveMoneroSpentReconciliationState(
                trezorAccount,
                MoneroSpentReconciliationState.Ready,
            )
        }
    }

    // recordNativeConnectionError is exercised directly (not through onRefreshed/statusInfo) because
    // those paths touch the native Wallet class (moneroWalletService.wallet), whose static
    // initializer loads libmonerujo and cannot run on the JVM. The helper has no such dependency.

    @Test
    fun recordNativeConnectionError_disconnectedRepeated_recordsOnce() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Disconnected, "boom")
        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Disconnected, "boom")

        verify(exactly = 1) { tracker.record(BlockchainType.Monero, account.id, any()) }
    }

    @Test
    fun recordNativeConnectionError_wrongVersion_recordsWithStatusName() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_WrongVersion, null)

        verify(exactly = 1) {
            tracker.record(
                BlockchainType.Monero,
                account.id,
                match { it.method == "ConnectionStatus_WrongVersion" }
            )
        }
    }

    @Test
    fun recordNativeConnectionError_connected_recordsNothing() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Connected, null)

        verify(exactly = 0) { tracker.record(any(), any(), any()) }
    }

    @Test
    fun recordNativeConnectionError_nullStatus_recordsNothing() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        val wrapper = createWrapper(mockService(), tracker = tracker)

        invokeRecord(wrapper, null, null)

        verify(exactly = 0) { tracker.record(any(), any(), any()) }
    }

    @Test
    fun recordNativeConnectionError_trackerThrows_doesNotPropagate() {
        val tracker = mockk<NetworkErrorTracker>(relaxed = true)
        every { tracker.record(any(), any(), any()) } throws RuntimeException("boom")
        val wrapper = createWrapper(mockService(), tracker = tracker)

        // Must not throw: the helper wraps recording in tryOrNull.
        invokeRecord(wrapper, ConnectionStatus.ConnectionStatus_Disconnected, null)
    }

    // The device is offline while the kit still reports ConnectionStatus_Connected: MoneroWalletService
    // derives that status from the local chain height and never contacts the daemon.

    @Test
    fun refreshedStatePath_deviceOfflineWalletReportsSynced_forcesNotSynced() {
        val wrapper = createWrapper(mockService(), connectivityManager = connectivity(connected = false))

        val state =
            invokeResolve(wrapper, nativeConnected = true, isSynchronized = true, currentHeight = 0L, totalHeight = 0L)
        invokePublish(wrapper, state)

        assertTrue(wrapper.syncState.value is AdapterState.NotSynced)
    }

    @Test
    fun refreshedStatePath_deviceOfflineNativeConnected_forcesNotSynced() {
        val wrapper = createWrapper(mockService(), connectivityManager = connectivity(connected = false))

        val state =
            invokeResolve(wrapper, nativeConnected = true, isSynchronized = false, currentHeight = 0L, totalHeight = 0L)
        invokePublish(wrapper, state)

        assertTrue(wrapper.syncState.value is AdapterState.NotSynced)
    }

    @Test
    fun refreshedStatePath_deviceOnlineNativeConnected_keepsSyncing() {
        val wrapper = createWrapper(mockService())

        val state = invokeResolve(
            wrapper, nativeConnected = true, isSynchronized = false, currentHeight = 50L, totalHeight = 100L
        )
        invokePublish(wrapper, state)

        assertTrue(wrapper.syncState.value is AdapterState.Syncing)
    }

    @Test
    fun resolveSyncState_nativeDisconnected_returnsNotSynced() {
        val wrapper = createWrapper(mockService())

        val state = invokeResolve(
            wrapper, nativeConnected = false, isSynchronized = false, currentHeight = 0L, totalHeight = 0L
        )

        assertTrue(state is AdapterState.NotSynced)
    }

    @Test
    fun onNetworkLost_deviceStillReportsConnected_setsNotSynced() {
        val wrapper = createWrapper(mockService())
        setSyncState(wrapper, AdapterState.Synced)

        wrapper.onNetworkLost()

        assertTrue(wrapper.syncState.value is AdapterState.NotSynced)
    }

    @Test
    fun appendNetworkErrors_afterRecord_mergesIntoStatus() {
        // Contract that MoneroKitWrapper.statusInfo() relies on: a recorded error surfaces as
        // "Recent Network Error ..." keys in the merged status map.
        val tracker = NetworkErrorTracker()
        tracker.record(
            BlockchainType.Monero,
            account.id,
            NetworkErrorInfo(
                source = "Monero",
                method = "ConnectionStatus_Disconnected",
                url = "",
                host = "",
                resolvedIps = emptyList(),
                throwable = IllegalStateException("Not connected"),
            )
        )

        val merged = tracker.appendNetworkErrors(mapOf("isStarted" to true), BlockchainType.Monero, account.id)

        assertTrue(merged.keys.any { it.startsWith("Recent Network Error") })
    }

    private fun mockService(): MoneroWalletService {
        return mockk(relaxed = true)
    }

    private fun connectivity(connected: Boolean): IConnectivityManager = mockk {
        every { isConnected } returns MutableStateFlow(connected)
    }

    private fun createWrapper(
        service: MoneroWalletService,
        account: Account = this.account,
        tracker: NetworkErrorTracker = mockk(relaxed = true),
        connectivityManager: IConnectivityManager = connectivity(connected = true),
        gateway: MoneroTrezorOperationGateway = mockk(relaxed = true),
        healthReader: MoneroWalletHealthReader = fakeHealthReader(healthyWalletHealth()),
    ): MoneroKitWrapper {
        return MoneroKitWrapper(
            moneroWalletService = service,
            restoreSettingsManager = restoreSettingsManager,
            account = account,
            dispatcherProvider = dispatcherProvider,
            networkErrorTracker = tracker,
            moneroTrezorGateway = gateway,
            connectivityManager = connectivityManager,
            walletHealthReader = healthReader,
        )
    }

    private fun invokeResolve(
        wrapper: MoneroKitWrapper,
        nativeConnected: Boolean,
        isSynchronized: Boolean,
        currentHeight: Long,
        totalHeight: Long,
    ): AdapterState = MoneroKitWrapper::class.java.getDeclaredMethod(
        "resolveSyncState",
        Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
    ).apply { isAccessible = true }
        .invoke(wrapper, nativeConnected, isSynchronized, currentHeight, totalHeight) as AdapterState

    private fun invokePublish(wrapper: MoneroKitWrapper, state: AdapterState) {
        MoneroKitWrapper::class.java.getDeclaredMethod("publishSyncState", AdapterState::class.java)
            .apply { isAccessible = true }
            .invoke(wrapper, state)
    }

    private fun fakeHealthReader(health: MoneroWalletHealthSnapshot): MoneroWalletHealthReader =
        object : MoneroWalletHealthReader {
            override fun snapshot(callbackWallet: Wallet?) = health
        }

    private fun healthyWalletHealth(
        callbackWalletIsCurrent: Boolean = true,
        nativeStatusIsOk: Boolean = true,
        nativeConnectionIsConnected: Boolean = true,
        serviceConnectionIsConnected: Boolean = true,
        walletIsSynchronized: Boolean = true,
        nativeConnectionStatus: ConnectionStatus? = ConnectionStatus.ConnectionStatus_Connected,
        nativeStatusError: String? = null,
    ) = MoneroWalletHealthSnapshot(
        callbackWalletIsCurrent = callbackWalletIsCurrent,
        nativeStatusIsOk = nativeStatusIsOk,
        nativeConnectionIsConnected = nativeConnectionIsConnected,
        serviceConnectionIsConnected = serviceConnectionIsConnected,
        walletIsSynchronized = walletIsSynchronized,
        nativeConnectionStatus = nativeConnectionStatus,
        nativeStatusError = nativeStatusError,
    )

    private fun invokeRecord(
        wrapper: MoneroKitWrapper,
        status: ConnectionStatus?,
        errorString: String?
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "recordNativeConnectionError", ConnectionStatus::class.java, String::class.java
        ).apply { isAccessible = true }.invoke(wrapper, status, errorString)
    }

    private fun invokeAbandonFaultedWallet(
        wrapper: MoneroKitWrapper,
        failure: HardwareWalletOperationException,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "abandonFaultedWallet",
            HardwareWalletOperationException::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, failure)
    }

    private fun invokeHandleStartFailure(wrapper: MoneroKitWrapper, failure: Exception) {
        MoneroKitWrapper::class.java.getDeclaredMethod("handleStartFailure", Exception::class.java)
            .apply { isAccessible = true }
            .invoke(wrapper, failure)
    }

    private fun invokeAbortControlledHardwareWallet(wrapper: MoneroKitWrapper, error: Throwable) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "abortControlledHardwareWallet",
            Throwable::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, error)
    }

    private fun invokeActivateReconciliationSession(wrapper: MoneroKitWrapper) {
        MoneroKitWrapper::class.java.getDeclaredMethod("activateReconciliationSession")
            .apply { isAccessible = true }
            .invoke(wrapper)
    }

    private fun invokeDeactivateReconciliationSession(wrapper: MoneroKitWrapper) {
        MoneroKitWrapper::class.java.getDeclaredMethod("deactivateReconciliationSession")
            .apply { isAccessible = true }
            .invoke(wrapper)
    }

    private fun invokeSetSpendReadinessForSession(
        wrapper: MoneroKitWrapper,
        session: Long,
        readiness: MoneroSpendReadiness,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "setSpendReadinessForSession",
            Long::class.javaPrimitiveType,
            MoneroSpendReadiness::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, session, readiness)
    }

    private fun invokeFailClosedAfterReconciliationError(
        wrapper: MoneroKitWrapper,
        session: Long,
        error: Throwable,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "failClosedAfterReconciliationError",
            Long::class.javaPrimitiveType,
            Throwable::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, session, error)
    }

    private fun invokeFailClosedAfterPostSyncError(
        wrapper: MoneroKitWrapper,
        session: Long,
        error: Throwable,
    ) {
        MoneroKitWrapper::class.java.getDeclaredMethod(
            "failClosedAfterPostSyncError",
            Long::class.javaPrimitiveType,
            Throwable::class.java,
        ).apply { isAccessible = true }.invoke(wrapper, session, error)
    }

    private fun activeReconciliationSession(wrapper: MoneroKitWrapper): Long {
        val reconciler = privateField(wrapper, "spentStatusReconciler")
            .get(wrapper) as MoneroSpentStatusReconciler
        return checkNotNull(reconciler.activeSession())
    }

    private fun setKeyImageSyncSession(wrapper: MoneroKitWrapper, session: Long) {
        privateField(wrapper, "keyImageSyncSession").set(wrapper, session)
    }

    private fun keyImageSyncSession(wrapper: MoneroKitWrapper): Long? =
        privateField(wrapper, "keyImageSyncSession").get(wrapper) as Long?

    private fun setStarted(wrapper: MoneroKitWrapper) {
        privateField(wrapper, "isStarted").set(wrapper, true)
    }

    private fun setExplicitColdRecoveryPending(wrapper: MoneroKitWrapper, pending: Boolean) {
        privateField(wrapper, "explicitColdRecoveryPending").set(wrapper, pending)
    }

    private fun explicitColdRecoveryPending(wrapper: MoneroKitWrapper): Boolean =
        privateField(wrapper, "explicitColdRecoveryPending").getBoolean(wrapper)

    @Suppress("UNCHECKED_CAST")
    private fun setSyncState(wrapper: MoneroKitWrapper, state: AdapterState) {
        val syncState = privateField(wrapper, "_syncState")
            .get(wrapper) as MutableStateFlow<AdapterState>
        syncState.value = state
    }

    @Suppress("UNCHECKED_CAST")
    private fun setSpendReadiness(
        wrapper: MoneroKitWrapper,
        readiness: MoneroSpendReadiness,
    ) {
        val spendReadiness = privateField(wrapper, "_spendReadiness")
            .get(wrapper) as MutableStateFlow<MoneroSpendReadiness>
        spendReadiness.value = readiness
    }

    private fun testControlledRefreshOperation(
        refresh: suspend (ControlledHardwareRefreshAborter) -> Unit = {},
        finalize: suspend () -> Unit = {},
        abort: (Throwable) -> Unit = {},
    ): ControlledHardwareRefreshOperation = object : ControlledHardwareRefreshOperation {
        override suspend fun run(aborter: ControlledHardwareRefreshAborter) {
            try {
                refresh(aborter)
                finalize()
            } catch (error: Throwable) {
                aborter.abort(error)
                throw error
            }
        }

        override fun abort(error: Throwable) {
            abort(error)
        }
    }

    private fun TestScope.controlledRefreshCoordinator(): ControlledHardwareRefreshCoordinator {
        val supervisor = SupervisorJob()
        controlledRefreshCoordinatorJobs += supervisor
        return ControlledHardwareRefreshCoordinator(
            CoroutineScope(coroutineContext.minusKey(Job) + supervisor),
        )
    }

    private fun coordinatorMutex(coordinator: ControlledHardwareRefreshCoordinator): Mutex =
        coordinator.javaClass.getDeclaredField("mutex").apply {
            isAccessible = true
        }.get(coordinator) as Mutex

    private fun privateField(wrapper: MoneroKitWrapper, name: String) =
        wrapper.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }

}
