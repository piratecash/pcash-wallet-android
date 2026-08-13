package cash.p.terminal.core.managers

import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.wallet.AdapterState
import com.m2049r.xmrwallet.model.Wallet
import com.piratecash.monero.signer.ExternalSignerRegistration
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class MoneroKitWrapperRefreshTest : MoneroKitWrapperTestFixture() {
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
                        refresh = { error("refresh failed") },
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


}
