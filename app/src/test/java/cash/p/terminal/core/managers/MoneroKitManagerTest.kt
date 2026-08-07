package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.AdapterState
import com.m2049r.xmrwallet.service.MoneroWalletService
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

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
            moneroFileDao = mockk(relaxed = true),
            removeMoneroWalletFilesUseCase = mockk(relaxed = true),
            networkErrorTracker = mockk(relaxed = true),
        ).apply {
            moneroKitWrapper = mockWrapper
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

    // The foreground state collector also calls into the wrapper on subscription, so the recorded
    // calls are dropped before the network emission under test.
    private fun TestScope.subscribeAndSettle(manager: MoneroKitManager) {
        invokeSubscribeToEvents(manager)
        advanceUntilIdle()
        clearMocks(mockWrapper, answers = false)
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
