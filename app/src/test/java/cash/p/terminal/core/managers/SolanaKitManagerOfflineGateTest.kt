package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.solanakit.SolanaKit
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
 * Coverage for the offline-mode network gate in SolanaKitManager.startForPolling(): an offline
 * (account, blockchain) pair must skip the kit's network calls while the polling-session
 * counter still increments, keeping it symmetric with stopForPolling()'s decrement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SolanaKitManagerOfflineGateTest {

    private val account = Account(
        id = "account-id",
        name = "Solana",
        type = AccountType.SolanaAddress("Fg6PaFpoGXkYsidMpWTK6W2BeZ7FEfcYkg476zPFsLnS"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val mockSolanaKit = mockk<SolanaKit>(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private var createdManager: SolanaKitManager? = null

    @After
    fun tearDown() {
        createdManager?.let { manager ->
            val scopeField = SolanaKitManager::class.java.getDeclaredField("coroutineScope").apply {
                isAccessible = true
            }
            (scopeField.get(manager) as CoroutineScope).cancel()
        }
    }

    private fun createManager(): SolanaKitManager {
        val manager = SolanaKitManager(
            rpcSourceManager = mockk(relaxed = true),
            walletManager = mockk(relaxed = true),
            backgroundManager = mockk<BackgroundManager>(relaxed = true),
            hardwarePublicKeyStorage = mockk(relaxed = true),
            trezorClient = mockk(relaxed = true),
            backgroundKeepAliveManager = mockk(relaxed = true),
            networkErrorTracker = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
        )
        manager.solanaKitWrapper = SolanaKitWrapper(mockSolanaKit, null)
        setField(manager, "currentAccount", account)
        createdManager = manager
        return manager
    }

    private fun setField(manager: SolanaKitManager, name: String, value: Any?) {
        SolanaKitManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(manager, value)
    }

    private fun pollingSessionCount(manager: SolanaKitManager): Int =
        (SolanaKitManager::class.java.getDeclaredField("pollingSessionCount").apply {
            isAccessible = true
        }.get(manager) as AtomicInteger).get()

    @Test
    fun startForPolling_offlinePair_skipsNetworkCallButCountsSession() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Solana)) } returns true
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        verify(exactly = 0) { mockSolanaKit.resume() }
        verify(exactly = 0) { mockSolanaKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }

    @Test
    fun startForPolling_onlinePair_resumesAndRefreshesKit() = testScope.runTest {
        every { offlineModeManager.isNetworkPaused(OfflineKey(account.id, BlockchainType.Solana)) } returns false
        val manager = createManager()

        manager.startForPolling()
        advanceUntilIdle()

        verify(exactly = 1) { mockSolanaKit.resume() }
        verify(exactly = 1) { mockSolanaKit.refresh() }
        assertEquals(1, pollingSessionCount(manager))
    }
}
