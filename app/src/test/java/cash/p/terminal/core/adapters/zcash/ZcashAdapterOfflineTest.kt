package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.managers.OfflineKey
import cash.z.ecc.android.sdk.Synchronizer
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Offline path of [ZcashAdapter]: the local data stays readable while the network is paused, and
 * nothing on the paused path recreates the synchronizer (which would erase nothing but resync).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterOfflineTest : ZcashAdapterTestFixture() {

    private val lifecycleStateFlow = MutableStateFlow(Synchronizer.LifecycleState.Running)

    private val offlineKey = OfflineKey("test-account-id", BlockchainType.Zcash)

    override fun stubSynchronizer() {
        every { mockSynchronizer.lifecycleState } returns lifecycleStateFlow
    }

    override fun stubSynchronizerCompanion() {
        every { newBlockingWithAnyAutoStart() } returns mockSynchronizer
    }

    @Test
    fun init_networkPaused_createsSynchronizerWithoutAutoStart() = runTest(dispatcher) {
        pauseNetwork()

        adapter = createAdapter()

        verify { newBlocking(autoStart = false) }
    }

    @Test
    fun init_online_createsSynchronizerWithAutoStart() = runTest(dispatcher) {
        adapter = createAdapter()

        verify { newBlocking(autoStart = true) }
    }

    @Test
    fun pauseNetwork_pausesSyncWithoutRecreatingSynchronizer() = runTest(dispatcher) {
        adapter = createAdapter()

        adapter.pauseNetwork()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockSynchronizer.pauseSync() }
        verify(exactly = 1) { newBlockingWithAnyAutoStart() }
    }

    // The manager still reports paused here on purpose: beginTransition() holds that flag for the
    // whole go-online transition, so a resume that consulted it would never lift its own pause.
    @Test
    fun resumeNetwork_transitionInFlight_resumesPausedSynchronizerInPlace() = runTest(dispatcher) {
        pauseNetwork()
        adapter = createAdapter()
        lifecycleStateFlow.value = Synchronizer.LifecycleState.Paused
        coEvery { mockSynchronizer.resumeSync() } returns true

        adapter.resumeNetwork()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockSynchronizer.resumeSync() }
        verify(exactly = 1) { newBlockingWithAnyAutoStart() }
    }

    @Test
    fun startForPolling_networkPaused_keepsSynchronizerPaused() = runTest(dispatcher) {
        pauseNetwork()
        adapter = createAdapter()
        lifecycleStateFlow.value = Synchronizer.LifecycleState.Paused

        adapter.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockSynchronizer.resumeSync() }
        verify(exactly = 1) { newBlockingWithAnyAutoStart() }
    }

    private fun pauseNetwork() {
        every { offlineModeManager.isNetworkPaused(offlineKey) } returns true
    }

    private fun MockKMatcherScope.newBlockingWithAnyAutoStart() = newBlocking(autoStart = any())

    private fun MockKMatcherScope.newBlocking(autoStart: Boolean) = Synchronizer.newBlocking(
        context = any(),
        zcashNetwork = any(),
        alias = any(),
        lightWalletEndpoint = any(),
        birthday = any(),
        walletInitMode = any(),
        setup = any(),
        isTorEnabled = any(),
        isExchangeRateEnabled = any(),
        autoStart = autoStart,
    )
}
