package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import com.m2049r.xmrwallet.service.MoneroWalletService
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith

/**
 * Regression coverage for the background lifecycle: entering background must stop and
 * save the Monero wallet (not merely pause it), unless polling is active or a keep-alive
 * request is in effect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneroKitManagerTest {

    private val moneroWalletService = mockk<MoneroWalletService>(relaxed = true)
    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    private val backgroundKeepAliveManager = mockk<BackgroundKeepAliveManager>(relaxed = true)
    private val moneroFileDao = mockk<MoneroFileDao>(relaxed = true)
    private val removeMoneroWalletFilesUseCase =
        mockk<RemoveMoneroWalletFilesUseCase>(relaxed = true)
    private val networkAvailability = MutableSharedFlow<Boolean>()
    private val connectedState = MutableStateFlow(true)
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true) {
        every { networkAvailabilityFlow } returns networkAvailability
        every { isConnected } returns connectedState
    }
    private val wrapperSyncState = MutableStateFlow<AdapterState>(AdapterState.Synced)
    private val mockWrapper = mockk<MoneroKitWrapper>(relaxed = true) {
        every { syncState } returns wrapperSyncState
    }
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

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)

    private var createdManager: MoneroKitManager? = null

    @After
    fun tearDown() {
        // MoneroKitManager doesn't expose a close/destroy method, so cancel its internal
        // coroutineScope via reflection to avoid leaking the background collectors.
        createdManager?.let { manager ->
            val scopeField = MoneroKitManager::class.java.getDeclaredField("coroutineScope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
    }

    private fun createManager(
        backgroundStateFlow: MutableStateFlow<BackgroundManagerState>,
        inForeground: Boolean = true,
        wrapper: MoneroKitWrapper? = mockWrapper,
    ): MoneroKitManager {
        val backgroundManager = mockk<BackgroundManager> {
            every { stateFlow } returns backgroundStateFlow
            every { this@mockk.inForeground } returns inForeground
        }
        return MoneroKitManager(
            moneroWalletService = moneroWalletService,
            backgroundManager = backgroundManager,
            restoreSettingsManager = restoreSettingsManager,
            backgroundKeepAliveManager = backgroundKeepAliveManager,
            connectivityManager = connectivityManager,
            dispatcherProvider = dispatcherProvider,
            moneroFileDao = moneroFileDao,
            removeMoneroWalletFilesUseCase = removeMoneroWalletFilesUseCase,
            networkErrorTracker = mockk(relaxed = true),
        ).apply {
            moneroKitWrapper = wrapper
            createdManager = this
        }
    }

    private fun invokeSubscribeToEvents(manager: MoneroKitManager) {
        MoneroKitManager::class.java.getDeclaredMethod("subscribeToEvents").apply {
            isAccessible = true
        }.invoke(manager)
    }

    @Test
    fun enterBackground_idle_stopsAndSavesWallet() = testScope.runTest {
        every { backgroundKeepAliveManager.isKeepAlive(BlockchainType.Monero) } returns false
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterForeground)
        val manager = createManager(backgroundStateFlow)
        invokeSubscribeToEvents(manager)

        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.stop(saveWallet = true) }
        coVerify(exactly = 0) { mockWrapper.pause() }
    }

    @Test
    fun enterBackground_keepAlive_doesNotStop() = testScope.runTest {
        every { backgroundKeepAliveManager.isKeepAlive(BlockchainType.Monero) } returns true
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterForeground)
        val manager = createManager(backgroundStateFlow)
        invokeSubscribeToEvents(manager)

        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        advanceUntilIdle()

        coVerify(exactly = 0) { mockWrapper.stop(any()) }
    }

    @Test
    fun withPollingSession_reopensStoppedWallet() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        coEvery { mockWrapper.resume() } returns false

        manager.withPollingSession { }
        advanceUntilIdle()

        coVerify { mockWrapper.resume() }
        coVerify { mockWrapper.start() }
    }

    @Test
    fun withPollingSession_background_stopsAndSaves() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterBackground))

        manager.withPollingSession { }
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.stop(saveWallet = true) }
    }

    @Test
    fun unlink_lastUser_clearsWrapper() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)

        manager.unlink(account)
        advanceUntilIdle()

        // stopKit() must null the wrapper so a later poller restart cannot reuse it with a null factory.
        assertNull(manager.moneroKitWrapper)
        coVerify { mockWrapper.stop(any()) }
    }

    @Test
    fun unlink_nativeCloseFails_retainsWrapperOwnershipAndUseCount() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        val closeFailure = IllegalStateException("close failed")
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)
        coEvery { mockWrapper.stop(any()) } throws closeFailure

        val actual = assertFailsWith<IllegalStateException> {
            manager.unlink(account)
        }

        assertEquals(closeFailure.message, actual.message)
        assertSame(mockWrapper, manager.moneroKitWrapper)
        assertSame(account, manager.currentAccount)
        assertEquals(1, (fieldValue(manager, "useCount") as AtomicInteger).get())
    }

    @Test
    fun deleteForAccount_firstNativeCloseFails_thenSucceeds_deletesAccountAndClearsKitOwnership() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)
        var accountDeleted = false
        coEvery { mockWrapper.stop(saveWallet = false) } throws IllegalStateException("close failed") andThen Unit
        manager.deleteForAccount(account, { assertTrue(accountDeleted) }, { accountDeleted = true })
        assertNull(manager.moneroKitWrapper)
        assertNull(manager.currentAccount)
        assertEquals(0, (fieldValue(manager, "useCount") as AtomicInteger).get())
        coVerify(exactly = 2) { mockWrapper.stop(saveWallet = false) }
        coVerify(exactly = 0) { mockWrapper.stop(saveWallet = true) }
    }

    @Test
    fun deleteForAccount_twoNativeCloseFailures_retainsOwnershipAndDoesNotDeleteOrStopAdapters() =
        testScope.runTest {
            val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
            setField(manager, "currentAccount", account)
            (fieldValue(manager, "useCount") as AtomicInteger).set(1)
            coEvery { mockWrapper.stop(saveWallet = false) } throws IllegalStateException("close failed")
            var accountDeleted = false
            var adaptersStopped = false
            assertFailsWith<IllegalStateException> {
                manager.deleteForAccount(
                    account,
                    stopAdapters = { adaptersStopped = true },
                    deleteAccount = { accountDeleted = true },
                )
            }
            assertFalse(accountDeleted)
            assertFalse(adaptersStopped)
            assertSame(mockWrapper, manager.moneroKitWrapper)
            assertSame(account, manager.currentAccount)
            assertEquals(1, (fieldValue(manager, "useCount") as AtomicInteger).get())
            coVerify(exactly = 2) { mockWrapper.stop(saveWallet = false) }
        }

    @Test
    fun deleteForAccount_accountDeletionFails_retainsOwnershipAndRestartsOnForeground() = testScope.runTest {
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterForeground)
        val manager = createManager(backgroundStateFlow)
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)
        coEvery { mockWrapper.resume() } returns false
        var adaptersStopped = false
        assertFailsWith<IllegalStateException> {
            manager.deleteForAccount(account, { adaptersStopped = true }) { error("delete failed") }
        }
        advanceUntilIdle()
        assertFalse(adaptersStopped)
        assertSame(mockWrapper, manager.moneroKitWrapper)
        assertSame(account, manager.currentAccount)
        assertEquals(1, (fieldValue(manager, "useCount") as AtomicInteger).get())
        coVerify { mockWrapper.resume() }
        coVerify { mockWrapper.start() }
    }
    @Test
    fun deleteForAccount_waitsForActivePollingSession() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        setField(manager, "currentAccount", account)
        val polling = async {
            manager.withPollingSession {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val deletion = async { manager.deleteForAccount(account, {}, {}) }
        advanceUntilIdle()
        assertFalse(deletion.isCompleted)
        release.complete(Unit)
        polling.await()
        deletion.await()
        coVerify(exactly = 1) { mockWrapper.stop(saveWallet = false) }
        coVerify(exactly = 0) { mockWrapper.stop(saveWallet = true) }
    }

    @Test
    fun exclusiveNativeWallet_blocksNormalLifecycleUntilReleased() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery {
            mockWrapper.withNativeWalletReleased<String>(any())
        } coAnswers {
            firstArg<suspend () -> String>().invoke()
        }

        val exclusive = async {
            manager.withExclusiveNativeWallet {
                entered.complete(Unit)
                release.await()
                "created"
            }
        }
        entered.await()
        val unlink = async { manager.unlink(account) }

        advanceUntilIdle()
        assertFalse(unlink.isCompleted)

        release.complete(Unit)
        assertEquals("created", exclusive.await())
        unlink.await()
        coVerify(exactly = 1) { mockWrapper.withNativeWalletReleased<String>(any()) }
    }

    @Test
    fun rescan_inactiveTrezorAccount_preservesWalletFilesAndUpdatesHeight() =
        testScope.runTest {
            val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
            val trezorAccount = account.copy(
                type = AccountType.TrezorDevice(
                    deviceId = "device-id",
                    model = "T3T1",
                    firmwareVersion = "2.8.10",
                    walletPublicKey = "wallet-key",
                ),
            )

            manager.rescan(trezorAccount, 3_529_956)

            coVerify(exactly = 0) { removeMoneroWalletFilesUseCase(any<Account>()) }
            coVerify(exactly = 0) { moneroFileDao.deleteAssociatedRecord(any()) }
            verify(exactly = 1) {
                restoreSettingsManager.savePendingMoneroRescan(trezorAccount, 3_529_956)
            }
        }

    @Test
    fun rescan_activeAccount_unlinkWaitsUntilRescanCompletes() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)
        coEvery { mockWrapper.rescan(3_529_956) } coAnswers {
            entered.complete(Unit)
            release.await()
        }

        val rescan = async { manager.rescan(account, 3_529_956) }
        entered.await()
        val unlink = async { manager.unlink(account) }

        advanceUntilIdle()
        assertFalse(unlink.isCompleted)

        release.complete(Unit)
        rescan.await()
        unlink.await()
        coVerify(exactly = 1) { mockWrapper.rescan(3_529_956) }
    }

    @Test
    fun hardwareRestoreHeight_sameHeight_replaysOnlyPendingRescan() {
        assertFalse(
            shouldApplyHardwareRestoreHeight(
                currentHeight = 3_529_956,
                targetHeight = 3_529_956,
                hasPendingRescan = false,
            ),
        )
        assertTrue(
            shouldApplyHardwareRestoreHeight(
                currentHeight = 3_529_956,
                targetHeight = 3_529_956,
                hasPendingRescan = true,
            ),
        )
    }

    @Test
    fun networkAvailability_false_notifiesWrapper() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeAndSettle(manager)

        networkAvailability.emit(false)
        advanceUntilIdle()

        verify(exactly = 1) { mockWrapper.onNetworkLost() }
        coVerify(exactly = 0) { mockWrapper.refresh() }
    }

    @Test
    fun networkAvailability_trueInForeground_refreshes() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        subscribeAndSettle(manager)

        networkAvailability.emit(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.resume() }
        coVerify(exactly = 1) { mockWrapper.refresh() }
    }

    @Test
    fun networkAvailability_trueInBackground_doesNotRefresh() = testScope.runTest {
        val manager = createManager(
            MutableStateFlow(BackgroundManagerState.EnterBackground),
            inForeground = false
        )
        subscribeAndSettle(manager)

        networkAvailability.emit(true)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockWrapper.refresh() }
        verify(exactly = 0) { mockWrapper.onNetworkLost() }
    }

    @Test
    fun subscribeToEvents_networkLostBeforeSubscription_notifiesWrapper() = testScope.runTest {
        // The loss lands in the startKit() -> subscribeToEvents() gap: the replay-less SharedFlow
        // drops it, and nothing re-emits it while the app stays in foreground.
        connectedState.value = false
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))

        invokeSubscribeToEvents(manager)
        advanceUntilIdle()

        verify(exactly = 1) { mockWrapper.onNetworkLost() }
    }

    @Test
    fun subscribeToEvents_networkRestoredBeforeSubscription_refreshes() = testScope.runTest {
        // Mirror of the loss case: the kit started offline, the `true` that followed landed in the
        // startKit() -> subscribeToEvents() gap, and resumeOrStartKit alone never refreshes.
        wrapperSyncState.value = AdapterState.NotSynced(IllegalStateException("Not connected"))
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))

        invokeSubscribeToEvents(manager)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.refresh() }
    }

    @Test
    fun getMoneroKitWrapper_deviceNotFound_thenForeground_startsWallet() = testScope.runTest {
        retryInitialStartupFailure_startsWallet(
            errorCode = HardwareWalletErrorCode.DeviceNotFound,
            lifecycleFailures = 0,
        )
    }

    @Test
    fun getMoneroKitWrapper_acquireTimeout_thenForeground_startsWallet() = testScope.runTest {
        retryInitialStartupFailure_startsWallet(
            errorCode = HardwareWalletErrorCode.AcquireTimeout,
            lifecycleFailures = 1,
        )
    }

    @Test
    fun getMoneroKitWrapper_trezorRetryableStartupFailure_retainsAndReturnsWrapper() = testScope.runTest {
        val wrapper = createWrapper()
        val manager = createKitInstanceSpy(
            backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground),
            wrapper = wrapper,
        )
        val trezorAccount = trezorAccount()
        val startupFailure = HardwareWalletOperationException(
            HardwareWalletErrorCode.DeviceNotFound,
            "USB unavailable",
        )

        every { wrapper.isRestartableAfterFailedStart } returns true
        coEvery { wrapper.start() } throws startupFailure

        val initial = manager.getMoneroKitWrapper(trezorAccount)
        val retained = manager.getMoneroKitWrapper(trezorAccount)

        assertSame(initial, manager.moneroKitWrapper)
        assertSame(initial, retained)
        coVerify(exactly = 1) { wrapper.start() }
    }

    @Test
    fun deleteForAccount_partialStartupWrapper_closesAndClearsOwnershipBeforeDeletion() = testScope.runTest {
        val wrapper = createWrapper()
        val manager = createKitInstanceSpy(MutableStateFlow(BackgroundManagerState.EnterBackground), wrapper)
        val trezorAccount = trezorAccount()
        val startupFailure = HardwareWalletOperationException(HardwareWalletErrorCode.DeviceNotFound, "USB unavailable")
        val cleanupFailure = IllegalStateException("cleanup failed")

        every { wrapper.isRestartableAfterFailedStart } returns false
        coEvery { wrapper.start() } throws startupFailure
        coEvery { wrapper.stop(saveWallet = true) } throws cleanupFailure

        assertSame(startupFailure, assertFailsWith<HardwareWalletOperationException> {
            manager.getMoneroKitWrapper(trezorAccount)
        })
        assertSame(wrapper, manager.moneroKitWrapper)
        assertNull(manager.currentAccount)

        val repeatedCleanupFailure = assertFailsWith<IllegalStateException> {
            manager.getMoneroKitWrapper(trezorAccount)
        }
        assertEquals(cleanupFailure.message, repeatedCleanupFailure.message)

        var accountDeleted = false
        coEvery { wrapper.stop(saveWallet = false) } returns Unit
        manager.deleteForAccount(trezorAccount, {}, { accountDeleted = true })

        assertTrue(accountDeleted)
        assertNull(manager.moneroKitWrapper)
        assertNull(manager.currentAccount)
        coVerify(exactly = 1) { wrapper.stop(saveWallet = false) }
    }

    @Test
    fun enterForeground_cleanupRetainedOwnership_doesNotSuppressTransientFailure() = testScope.runTest {
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground)
        val wrapper = createWrapper()
        val manager = createManager(backgroundStateFlow, wrapper = wrapper)
        every { wrapper.isRestartableAfterFailedStart } returns false
        coEvery { wrapper.resume() } returns false
        coEvery { wrapper.start() } throws HardwareWalletOperationException(
            HardwareWalletErrorCode.AcquireTimeout,
            "USB unavailable",
        )

        invokeSubscribeToEvents(manager)
        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()
        backgroundStateFlow.value = BackgroundManagerState.EnterBackground
        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()

        coVerify(exactly = 1) { wrapper.start() }
    }

    @Test
    fun getMoneroKitWrapper_trezorProtocolStartupFailure_throwsAndClearsWrapper() = testScope.runTest {
        val wrapper = createWrapper()
        val manager = createKitInstanceSpy(
            backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground),
            wrapper = wrapper,
        )
        val startupFailure = HardwareWalletOperationException(
            HardwareWalletErrorCode.Protocol,
            "Protocol failure",
        )

        coEvery { wrapper.start() } throws startupFailure

        val actual = assertFailsWith<HardwareWalletOperationException> {
            manager.getMoneroKitWrapper(trezorAccount())
        }

        assertSame(startupFailure, actual)
        assertNull(manager.moneroKitWrapper)
        assertNull(fieldValue(manager, "wrapperAccount"))
    }

    // The foreground state collector also calls into the wrapper on subscription, so the recorded
    // calls are dropped before the network emission under test.
    private fun TestScope.subscribeAndSettle(manager: MoneroKitManager) {
        invokeSubscribeToEvents(manager)
        advanceUntilIdle()
        clearMocks(mockWrapper, answers = false)
    }

    private suspend fun TestScope.retryInitialStartupFailure_startsWallet(
        errorCode: HardwareWalletErrorCode,
        lifecycleFailures: Int,
    ) {
        val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.EnterBackground)
        val wrapper = createWrapper()
        val manager = createKitInstanceSpy(backgroundStateFlow, wrapper)
        every { wrapper.isRestartableAfterFailedStart } returns true
        coEvery { wrapper.resume() } returns false
        val startupFailure = HardwareWalletOperationException(errorCode, "USB unavailable")
        val startStub = coEvery { wrapper.start() } throws startupFailure
        repeat(lifecycleFailures) {
            startStub andThenThrows startupFailure
        }
        startStub andThen Unit

        manager.getMoneroKitWrapper(trezorAccount())
        backgroundStateFlow.value = BackgroundManagerState.EnterForeground
        advanceUntilIdle()
        repeat(lifecycleFailures) {
            backgroundStateFlow.value = BackgroundManagerState.EnterBackground
            backgroundStateFlow.value = BackgroundManagerState.EnterForeground
            advanceUntilIdle()
        }

        coVerify(exactly = lifecycleFailures + 2) { wrapper.start() }
    }

    private fun trezorAccount(): Account = account.copy(
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-key",
        ),
    )

    private fun createWrapper(): MoneroKitWrapper = mockk(relaxed = true) {
        every { syncState } returns wrapperSyncState
    }

    private fun createKitInstanceSpy(
        backgroundStateFlow: MutableStateFlow<BackgroundManagerState>,
        wrapper: MoneroKitWrapper,
    ): MoneroKitManager {
        val manager = spyk(
            createManager(backgroundStateFlow, wrapper = null),
            recordPrivateCalls = true,
        )
        every { manager["createKitInstance"](any<Account>()) } returns wrapper
        createdManager = manager
        return manager
    }

    private fun setField(manager: MoneroKitManager, name: String, value: Any?) {
        MoneroKitManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(manager, value)
    }

    private fun fieldValue(manager: MoneroKitManager, name: String): Any? =
        MoneroKitManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.get(manager)
}
