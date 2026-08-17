package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tronkit.TronKit
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Coverage for the offline-mode network gate in TronKitManager.startForPolling(): an offline
 * (account, blockchain) pair must skip the kit's network calls while the polling-session
 * counter still increments, keeping it symmetric with stopForPolling()'s decrement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TronKitManagerOfflineGateTest {

    private val account = Account(
        id = "account-id",
        name = "Tron",
        type = AccountType.TronAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val mockTronKit = mockk<TronKit>(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private var createdManager: TronKitManager? = null

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = TronKitManager::class.java.getDeclaredField("scope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
    }

    private fun createManager(): TronKitManager {
        val manager = TronKitManager(
            backgroundManager = mockk<BackgroundManager>(relaxed = true),
            hardwarePublicKeyStorage = mockk(relaxed = true),
            backgroundKeepAliveManager = mockk(relaxed = true),
            networkErrorTracker = mockk(relaxed = true),
            trezorClient = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
        )
        setField(manager, "tronKitWrapper", TronKitWrapper(mockTronKit, null))
        setField(manager, "currentAccount", account)
        createdManager = manager
        return manager
    }

    private fun setField(manager: TronKitManager, name: String, value: Any?) {
        TronKitManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(manager, value)
    }

    private fun pollingSessionCount(manager: TronKitManager): Int =
        (TronKitManager::class.java.getDeclaredField("pollingSessionCount").apply {
            isAccessible = true
        }.get(manager) as AtomicInteger).get()

    @Test
    fun startForPolling_offlinePair_skipsNetworkCallButCountsSession() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Tron)) } returns true
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        verify(exactly = 0) { mockTronKit.resume() }
        verify(exactly = 0) { mockTronKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun startForPolling_onlinePair_resumesAndRefreshesKit() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Tron)) } returns false
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        verify(exactly = 1) { mockTronKit.resume() }
        verify(exactly = 1) { mockTronKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }
}
