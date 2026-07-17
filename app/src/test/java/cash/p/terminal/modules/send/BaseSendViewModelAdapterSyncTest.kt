package cash.p.terminal.modules.send

import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class BaseSendViewModelAdapterSyncTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val balanceStateFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
    private val connectivityFlow = MutableStateFlow(true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val wallet = WalletFactory.previewWallet()

    private lateinit var adapter: IBalanceAdapter
    private var currentAdapterState: AdapterState = AdapterState.Synced

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        adapter = mockk<IBalanceAdapter>(relaxed = true) {
            every { balanceState } answers { currentAdapterState }
            every { balanceStateUpdatedFlow } returns balanceStateFlow
        }
        every { adapterManager.getBalanceAdapterForWallet(any()) } returns adapter
        startKoin {
            modules(module {
                single { mockk<cash.p.terminal.wallet.MarketKitWrapper>(relaxed = true) }
                single<cash.p.terminal.manager.IConnectivityManager> { mockConnectivityManager(connectivityFlow) }
                single {
                    mockk<cash.p.terminal.wallet.managers.IBalanceHiddenManager>(relaxed = true) {
                        every { balanceHiddenFlow } returns MutableStateFlow(false)
                    }
                }
            })
        }
    }

    private val viewModelStore = androidx.lifecycle.ViewModelStore()

    @After
    fun tearDown() {
        viewModelStore.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
    }

    // --- State machine tests ---

    @Test
    fun handleAdapterState_initialNotSynced_retriesOnceWithoutError() = runTest(dispatcher) {
        currentAdapterState = AdapterState.NotSynced(Exception("fail"))
        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.isSynced)
        assertFalse(vm.hasAdapterError)
        verify(exactly = 1) { adapterManager.refreshByWallet(wallet) }
    }

    @Test
    fun handleAdapterState_secondNotSynced_setsHasAdapterError() = runTest(dispatcher) {
        currentAdapterState = AdapterState.NotSynced(Exception("fail"))
        val vm = createViewModel()
        advanceUntilIdle()

        // Emit second NotSynced (auto-retry failed)
        emitAdapterState(AdapterState.NotSynced(Exception("fail again")))
        advanceUntilIdle()

        assertTrue(vm.hasAdapterError)
        assertFalse(vm.isSynced)
        verify(exactly = 1) { adapterManager.refreshByWallet(wallet) }
    }

    @Test
    fun handleAdapterState_connectingAfterError_clearsErrorKeepsAutoRetried() = runTest(dispatcher) {
        currentAdapterState = AdapterState.NotSynced(Exception("fail"))
        val vm = createViewModel()
        advanceUntilIdle()
        emitAdapterState(AdapterState.NotSynced(Exception("fail again")))
        advanceUntilIdle()
        assertTrue(vm.hasAdapterError)

        // Connecting clears error
        emitAdapterState(AdapterState.Connecting)
        advanceUntilIdle()
        assertFalse(vm.hasAdapterError)
        assertFalse(vm.isSynced)

        // Next NotSynced shows error immediately (autoRetried still true)
        emitAdapterState(AdapterState.NotSynced(Exception("fail third")))
        advanceUntilIdle()
        assertTrue(vm.hasAdapterError)
    }

    @Test
    fun handleAdapterState_synced_clearsErrorAndReenablesAutoRetry() = runTest(dispatcher) {
        currentAdapterState = AdapterState.NotSynced(Exception("fail"))
        val vm = createViewModel()
        advanceUntilIdle()
        emitAdapterState(AdapterState.NotSynced(Exception("fail again")))
        advanceUntilIdle()
        assertTrue(vm.hasAdapterError)

        // Synced clears everything
        emitAdapterState(AdapterState.Synced)
        advanceUntilIdle()
        assertFalse(vm.hasAdapterError)
        assertTrue(vm.isSynced)

        // New NotSynced triggers auto-retry again (not immediate error)
        emitAdapterState(AdapterState.NotSynced(Exception("new failure")))
        advanceUntilIdle()
        assertFalse(vm.hasAdapterError)
        verify(exactly = 2) { adapterManager.refreshByWallet(wallet) }
    }

    // --- Manual retry tests ---

    @Test
    fun retryAdapterSync_noOpRefresh_errorUiStaysVisible() = runTest(dispatcher) {
        currentAdapterState = AdapterState.NotSynced(Exception("fail"))
        val vm = createViewModel()
        advanceUntilIdle()
        emitAdapterState(AdapterState.NotSynced(Exception("fail again")))
        advanceUntilIdle()
        assertTrue(vm.hasAdapterError)

        vm.retryAdapterSync()
        advanceUntilIdle()

        assertTrue(vm.hasAdapterError)
        assertFalse(vm.isSynced)
    }

    @Test
    fun retryAdapterSync_adapterRecovers_clearsError() = runTest(dispatcher) {
        currentAdapterState = AdapterState.NotSynced(Exception("fail"))
        val vm = createViewModel()
        advanceUntilIdle()
        emitAdapterState(AdapterState.NotSynced(Exception("fail again")))
        advanceUntilIdle()
        assertTrue(vm.hasAdapterError)

        vm.retryAdapterSync()
        emitAdapterState(AdapterState.Syncing())
        advanceUntilIdle()
        assertFalse(vm.hasAdapterError)

        emitAdapterState(AdapterState.Synced)
        advanceUntilIdle()
        assertFalse(vm.hasAdapterError)
        assertTrue(vm.isSynced)
    }

    @Test
    fun retryAdapterSync_adapterFailsAgain_showsErrorImmediately() = runTest(dispatcher) {
        currentAdapterState = AdapterState.NotSynced(Exception("fail"))
        val vm = createViewModel()
        advanceUntilIdle()
        emitAdapterState(AdapterState.NotSynced(Exception("fail again")))
        advanceUntilIdle()
        assertTrue(vm.hasAdapterError)

        vm.retryAdapterSync()
        emitAdapterState(AdapterState.NotSynced(Exception("third failure")))
        advanceUntilIdle()

        assertTrue(vm.hasAdapterError)
    }

    // --- Sync grace tests ---

    @Test
    fun syncGrace_goodState_activeAndEffectivelySynced() = runTest(dispatcher) {
        currentAdapterState = AdapterState.Synced
        connectivityFlow.value = true
        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.syncGraceActive)
        assertTrue(vm.isEffectivelySynced)
    }

    @Test
    fun syncGrace_offlineWhileStaleSynced_boundedExpiryDisablesSend() = runTest(dispatcher) {
        currentAdapterState = AdapterState.Synced
        connectivityFlow.value = true
        val vm = createViewModel()
        advanceUntilIdle()
        assertTrue(vm.syncGraceActive)

        // Network drops while the adapter is still (stale) Synced — no adapter transition.
        connectivityFlow.value = false
        assertTrue(vm.syncGraceActive)          // brief flap → still within grace
        assertTrue(vm.isEffectivelySynced)      // Send button stays enabled

        // Grace is bounded: it expires purely on the timer, without any adapter transition.
        advanceUntilIdle()
        assertFalse(vm.syncGraceActive)
        assertFalse(vm.isEffectivelySynced)     // isSynced still true, but offline past grace → false
    }

    @Test
    fun syncGrace_neverGood_inactive() = runTest(dispatcher) {
        currentAdapterState = AdapterState.NotSynced(Exception("fail"))
        connectivityFlow.value = true
        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.syncGraceActive)
    }

    @Test
    fun syncGrace_quietGoodPeriodThenDrop_grantsFullGraceFromDrop() = runTest(dispatcher) {
        currentAdapterState = AdapterState.Synced
        connectivityFlow.value = true
        val vm = createViewModel()
        advanceUntilIdle()
        assertTrue(vm.syncGraceActive)

        // Good state persists quietly far past the grace window, with no adapter/connectivity emission.
        vm.fakeElapsedRealtime = 5 * 60 * 1000L

        // Network drops now: the grace window must be anchored to THIS moment, not the last emission.
        connectivityFlow.value = false
        assertTrue(vm.syncGraceActive)          // regression: was false without the good→bad anchor
        assertTrue(vm.isEffectivelySynced)

        // The full grace window still expires via the reactive timer.
        advanceUntilIdle()
        assertFalse(vm.syncGraceActive)
        assertFalse(vm.isEffectivelySynced)
    }

    // --- Helpers ---

    private fun emitAdapterState(state: AdapterState) {
        currentAdapterState = state
        balanceStateFlow.tryEmit(Unit)
    }

    private fun createViewModel(): TestSendViewModel {
        val vm = TestSendViewModel(wallet, adapterManager)
        viewModelStore.put("test-vm", vm)
        return vm
    }
}

/** Minimal concrete subclass for testing BaseSendViewModel */
private class TestSendViewModel(
    wallet: Wallet,
    adapterManager: IAdapterManager,
) : BaseSendViewModel<TestSendUiState>(wallet, adapterManager) {
    var fakeElapsedRealtime: Long = 0L
    override val elapsedRealtimeMs: Long
        get() = fakeElapsedRealtime
    override fun createState() = TestSendUiState()
    override fun getEstimatedFee(): BigDecimal? = null
    override fun onSendRequested() {}
}

private data class TestSendUiState(val dummy: Boolean = true)
