package cash.p.terminal.core.managers

import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.wallet.AdapterState
import com.m2049r.xmrwallet.model.Wallet
import com.m2049r.xmrwallet.model.Wallet.ConnectionStatus
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class MoneroKitWrapperReconciliationTest : MoneroKitWrapperTestFixture() {
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
    fun onRefreshed_initialHealthyTrezorCallback_doesNotEmitSyncedBeforeAutomaticReconciliation() = runTest {
        val fixture = pendingLiveRefreshFixture(this)
        val emissions = mutableListOf<AdapterState>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            fixture.wrapper.syncState.collect(emissions::add)
        }

        try {
            assertEquals(listOf(AdapterState.Syncing()), emissions)
            assertFalse(fixture.reconciler.hasActiveOperation(fixture.session))
            assertFalse(fixture.wrapper.onRefreshed(wallet = null, full = true))

            assertTrue(fixture.wrapper.syncState.value is AdapterState.Syncing)
            assertSame(MoneroSpendReadiness.ReconcilingSpentStatus, fixture.wrapper.spendReadiness.value)
            assertTrue(fixture.reconciler.hasActiveOperation(fixture.session))
            assertFalse(emissions.any { it is AdapterState.Synced })

            fixture.reconciler.clearOperation(fixture.session)
            every {
                restoreSettingsManager.moneroSpentReconciliationState(trezorAccount)
            } returns MoneroSpentReconciliationState.Ready
            assertTrue(
                fixture.wrapper.applyHardwareReadinessSnapshot(
                    session = fixture.session,
                    health = healthyWalletHealth(hasUnknownKeyImages = false),
                    hasUnknownKeyImages = false,
                ),
            )
            assertSame(MoneroSpendReadiness.Ready, fixture.wrapper.spendReadiness.value)
            assertSame(AdapterState.Synced, fixture.wrapper.syncState.value)
            assertSame(AdapterState.Synced, emissions.last())
            assertEquals(
                listOf(AdapterState.Syncing(), AdapterState.Synced),
                emissions,
            )
        } finally {
            collector.cancelAndJoin()
        }
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

        val state = invokeResolve(wrapper, nativeConnected = true, isSynchronized = true, currentHeight = 0L, totalHeight = 0L)
        invokePublish(wrapper, state)

        assertTrue(wrapper.syncState.value is AdapterState.NotSynced)
    }

    @Test
    fun refreshedStatePath_deviceOfflineNativeConnected_forcesNotSynced() {
        val wrapper = createWrapper(mockService(), connectivityManager = connectivity(connected = false))

        val state = invokeResolve(wrapper, nativeConnected = true, isSynchronized = false, currentHeight = 0L, totalHeight = 0L)
        invokePublish(wrapper, state)

        assertTrue(wrapper.syncState.value is AdapterState.NotSynced)
    }

    @Test
    fun refreshedStatePath_deviceOnlineNativeConnected_keepsSyncing() {
        val wrapper = createWrapper(mockService())

        val state = invokeResolve(wrapper, nativeConnected = true, isSynchronized = false, currentHeight = 50L, totalHeight = 100L)
        invokePublish(wrapper, state)

        assertTrue(wrapper.syncState.value is AdapterState.Syncing)
    }

    @Test
    fun resolveSyncState_nativeDisconnected_returnsNotSynced() {
        val wrapper = createWrapper(mockService())

        val state = invokeResolve(wrapper, nativeConnected = false, isSynchronized = false, currentHeight = 0L, totalHeight = 0L)

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


}
