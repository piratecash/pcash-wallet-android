package cash.p.terminal.core.adapters

import cash.p.terminal.core.managers.BackgroundKeepAliveManager
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.wallet.WalletFactory
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * The offline gate sits inside `onPollingStarted`, so a paused polling session still counts: it
 * skips the kit wake-up but keeps start/stop symmetric.
 */
class BitcoinAdapterOfflinePollingTest {

    private val kit = mockk<BitcoinKit>(relaxed = true)
    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val backgroundManager = mockk<BackgroundManager>(relaxed = true)
    private val backgroundState =
        MutableStateFlow<BackgroundManagerState>(BackgroundManagerState.Unknown)
    private val wallet = WalletFactory.previewWallet()

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single { offlineModeManager }
                single { mockk<BackgroundKeepAliveManager>(relaxed = true) }
                single { mockk<NetworkErrorTracker>(relaxed = true) }
            })
        }
        every { backgroundManager.stateFlow } returns backgroundState
        every {
            offlineModeManager.isNetworkPaused(
                OfflineKey(wallet.account.id, wallet.token.blockchainType)
            )
        } returns true
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun startForPolling_networkPaused_doesNotWakeKit() {
        val adapter = createAdapter()

        adapter.startForPolling()

        verify(exactly = 0) { kit.onEnterForeground() }
    }

    @Test
    fun startStopForPolling_networkPaused_leavesPollingCounterAtZero() {
        val adapter = createAdapter()
        adapter.attachLocalData()

        try {
            adapter.startForPolling()
            adapter.stopForPolling()
            clearMocks(kit, answers = false)

            backgroundState.value = BackgroundManagerState.EnterBackground

            // Backgrounding stops the kit only while no polling session is open. A paused start
            // that skipped the increment would leave the counter at -1 and never get here.
            verify(timeout = COLLECTOR_TIMEOUT_MS) { kit.onEnterBackground() }
        } finally {
            adapter.stop()
        }
    }

    private fun createAdapter() = BitcoinAdapter(
        kit = kit,
        syncMode = BitcoinCore.SyncMode.Full(),
        backgroundManager = backgroundManager,
        wallet = wallet,
    )

    private companion object {
        const val COLLECTOR_TIMEOUT_MS = 2_000L
    }
}
