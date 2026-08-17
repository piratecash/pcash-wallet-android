package cash.p.terminal.core.managers

import cash.p.terminal.core.MoneroSpendReadiness
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
import kotlinx.coroutines.CancellationException
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
import kotlin.reflect.KClass

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
    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
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
        // A live wrapper always belongs to the account whose wallet file it opened.
        every { mockWrapper.account } returns account
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
            offlineModeManager = offlineModeManager,
        ).apply {
            moneroKitWrapper = wrapper
            createdManager = this
        }
    }

    private fun invokeSubscribeToEvents(manager: MoneroKitManager) {
        MoneroKitManager::class.java.getDeclaredMethod("subscribeToEvents", Account::class.java)
            .apply { isAccessible = true }
            .invoke(manager, account)
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
    fun startForPolling_reopensStoppedWallet() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        coEvery { mockWrapper.resume() } returns false

        manager.startForPolling()
        advanceUntilIdle()

        coVerify { mockWrapper.resume() }
        coVerify { mockWrapper.start() }
    }

    @Test
    fun stopForPolling_background_stopsAndSaves() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterBackground))

        manager.stopForPolling()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.stop(saveWallet = true) }
    }

    @Test
    fun startForPolling_offlinePair_startsLocalOnlyAndStillCountsSession() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Monero)) } returns true
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)

        manager.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockWrapper.start(fixIfCorruptedFile = true, localOnly = true) }
        coVerify(exactly = 0) { mockWrapper.resume() }
        coVerify(exactly = 0) { mockWrapper.start(fixIfCorruptedFile = true, localOnly = false) }
        assertEquals(1, (fieldValue(manager, "pollingSessionCount") as AtomicInteger).get())
    }

    @Test
    fun resumeNetwork_offlineTrezorWallet_restartsThroughHardwareStartupPath() = testScope.runTest {
        val wrapper = createPausedTrezorWrapper()
        coEvery { wrapper["startInternal"](true, false) } coAnswers {
            setWrapperField(wrapper, "isStarted", true)
            setWrapperField(wrapper, "isPaused", false)
        }

        assertTrue(wrapper.resumeNetwork())

        coVerify(exactly = 1) { wrapper["stopInternal"](false) }
        coVerify(exactly = 1) { wrapper["startInternal"](true, false) }
        verify(exactly = 0) { moneroWalletService.connectDaemon() }
    }

    @Test
    fun resumeNetwork_offlineTrezorWalletStartupFails_restoresLocalOnlyAndRethrows() =
        testScope.runTest {
            assertFailedTrezorNetworkRecoveryRestoresLocalOnly(
                HardwareWalletOperationException(HardwareWalletErrorCode.Protocol, "failed"),
                HardwareWalletOperationException::class,
            )
        }

    @Test
    fun resumeNetwork_offlineTrezorWalletStartupCancelled_restoresLocalOnlyAndRethrows() =
        testScope.runTest {
            assertFailedTrezorNetworkRecoveryRestoresLocalOnly(
                CancellationException("cancelled"),
                CancellationException::class,
            )
        }

    @Test
    fun rescanIfActive_activeAccount_rescansWrapper() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)

        assertTrue(manager.rescanIfActive(account, 123L))

        coVerify(exactly = 1) { mockWrapper.rescan(123L) }
    }

    @Test
    fun rescanIfActive_inactiveAccount_leavesWrapperUntouched() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account.copy(id = "other-account-id"))

        assertFalse(manager.rescanIfActive(account, 123L))

        coVerify(exactly = 0) { mockWrapper.rescan(any()) }
    }

    // Claiming the account without touching a closed wrapper: the caller must not fall back to
    // deleting the wallet files of the account this manager already owns.
    @Test
    fun rescanIfActive_activeAccountWithoutWrapper_claimsAccount() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)
        manager.moneroKitWrapper = null

        assertTrue(manager.rescanIfActive(account, 123L))
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
    fun exclusiveNativeWallet_blocksNormalLifecycleUntilReleased() = testScope.runTest {
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)
        (fieldValue(manager, "useCount") as AtomicInteger).set(1)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery {
            mockWrapper.withNativeWalletReleased<String>(any(), any())
        } coAnswers {
            secondArg<suspend () -> String>().invoke()
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
        coVerify(exactly = 1) { mockWrapper.withNativeWalletReleased<String>(false, any()) }
    }

    @Test
    fun withExclusiveNativeWallet_offlinePair_restoresLocalOnly() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Monero)) } returns true
        every { mockWrapper.account } returns account
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))
        setField(manager, "currentAccount", account)
        coEvery { mockWrapper.withNativeWalletReleased<String>(true, any()) } coAnswers {
            secondArg<suspend () -> String>().invoke()
        }

        assertEquals("created", manager.withExclusiveNativeWallet { "created" })

        coVerify(exactly = 1) { mockWrapper.withNativeWalletReleased<String>(true, any()) }
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
    fun subscribeToEvents_offlinePairOnColdStart_doesNotRefresh() = testScope.runTest {
        // Cold start: the collectors are installed before currentAccount is published, so the gate
        // has to read the account passed into subscribeToEvents, not the still-null field.
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Monero)) } returns true
        wrapperSyncState.value = AdapterState.NotSynced(IllegalStateException("Not connected"))
        val manager = createManager(MutableStateFlow(BackgroundManagerState.EnterForeground))

        invokeSubscribeToEvents(manager)
        networkAvailability.emit(true)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockWrapper.refresh() }
        coVerify(exactly = 0) { mockWrapper.start(fixIfCorruptedFile = true, localOnly = false) }
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
    fun getMoneroKitWrapper_trezorCleanupRetainedOwnership_throwsTransientFailure() = testScope.runTest {
        val wrapper = createWrapper()
        val manager = createKitInstanceSpy(MutableStateFlow(BackgroundManagerState.EnterBackground), wrapper)
        val startupFailure = HardwareWalletOperationException(HardwareWalletErrorCode.DeviceNotFound, "USB unavailable")

        every { wrapper.isRestartableAfterFailedStart } returns false
        coEvery { wrapper.start() } throws startupFailure

        assertSame(startupFailure, assertFailsWith<HardwareWalletOperationException> {
            manager.getMoneroKitWrapper(trezorAccount())
        })
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

    private fun createPausedTrezorWrapper(): MoneroKitWrapper {
        val wrapper = spyk(
            MoneroKitWrapper(
                moneroWalletService = moneroWalletService,
                restoreSettingsManager = restoreSettingsManager,
                account = trezorAccount(),
                dispatcherProvider = dispatcherProvider,
                networkErrorTracker = mockk(relaxed = true),
                moneroTrezorGateway = mockk(relaxed = true),
                connectivityManager = connectivityManager,
            ),
            recordPrivateCalls = true,
        )
        setWrapperField(wrapper, "isStarted", true)
        setWrapperField(wrapper, "isPaused", true)
        coEvery { wrapper["stopInternal"](false) } returns Unit
        return wrapper
    }

    private suspend fun <T : Throwable> assertFailedTrezorNetworkRecoveryRestoresLocalOnly(
        failure: T,
        failureClass: KClass<T>,
    ) {
        val wrapper = createPausedTrezorWrapper()
        coEvery { wrapper["startInternal"](true, false) } throws failure
        coEvery { wrapper["startInternal"](true, true) } coAnswers {
            setWrapperField(wrapper, "isStarted", true)
            setWrapperField(wrapper, "isPaused", true)
        }

        val actual = assertFailsWith(failureClass) { wrapper.resumeNetwork() }

        assertSame(failure, actual)
        assertEquals(MoneroSpendReadiness.ReconciliationFailed, wrapper.spendReadiness.value)
        assertTrue(fieldValue(wrapper, "isStarted") as Boolean)
        assertTrue(fieldValue(wrapper, "isPaused") as Boolean)
        coVerify(exactly = 1) { wrapper["startInternal"](true, true) }
    }

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

    private fun setWrapperField(wrapper: MoneroKitWrapper, name: String, value: Any?) {
        MoneroKitWrapper::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(wrapper, value)
    }

    private fun fieldValue(instance: Any, name: String): Any? =
        ((instance as? MoneroKitManager)?.let { MoneroKitManager::class.java }
            ?: MoneroKitWrapper::class.java)
            .getDeclaredField(name).apply {
            isAccessible = true
        }.get(instance)
}
