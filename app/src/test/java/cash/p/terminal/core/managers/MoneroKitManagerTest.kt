package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import com.m2049r.xmrwallet.service.MoneroWalletService
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Test

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
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true) {
        every { networkAvailabilityFlow } returns MutableSharedFlow()
    }
    private val mockWrapper = mockk<MoneroKitWrapper>(relaxed = true)

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

    private fun createManager(backgroundStateFlow: MutableStateFlow<BackgroundManagerState>): MoneroKitManager {
        val backgroundManager = mockk<BackgroundManager> {
            every { stateFlow } returns backgroundStateFlow
        }
        return MoneroKitManager(
            moneroWalletService = moneroWalletService,
            backgroundManager = backgroundManager,
            restoreSettingsManager = restoreSettingsManager,
            backgroundKeepAliveManager = backgroundKeepAliveManager,
            connectivityManager = connectivityManager,
            dispatcherProvider = dispatcherProvider,
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
}
