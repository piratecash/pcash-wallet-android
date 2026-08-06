package cash.p.terminal.modules.send

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.tokenQueryId
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.CoreApp
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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

/**
 * The send screen must follow the same per-wallet balance visibility as the token and swap screens.
 * Reading or toggling the global flag here makes hiding on the token screen have no effect on the
 * send screen, and makes a tap on the send screen hide balances app-wide.
 *
 * A real [BalanceHiddenManager] is used on purpose: the regression is in which visibility model the
 * view model talks to, so the assertions must run against the real global/per-wallet semantics.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseSendViewModelBalanceHiddenTest {

    private val dispatcher = StandardTestDispatcher()
    private val wallet = WalletFactory.previewWallet()
    private val otherWallet = WalletFactory.previewStakingWallet()
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val viewModelStore = ViewModelStore()

    private var storedBalanceHidden = false
    private lateinit var balanceHiddenManager: BalanceHiddenManager

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(App)
        every { App.instance } returns mockk<CoreApp>(relaxed = true)
        mockkObject(HudHelper)
        every { HudHelper.vibrate(any(), any()) } just runs

        every { localStorage.balanceHidden } answers { storedBalanceHidden }
        every { localStorage.balanceHidden = any() } answers { storedBalanceHidden = firstArg() }
        every { localStorage.balanceAutoHideEnabled } returns false

        balanceHiddenManager = BalanceHiddenManager(
            localStorage = localStorage,
            backgroundManager = mockk<BackgroundManager>(relaxed = true) {
                every { stateFlow } returns MutableStateFlow(BackgroundManagerState.EnterForeground)
            },
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )

        startKoin {
            modules(module {
                single { mockk<MarketKitWrapper>(relaxed = true) }
                single { mockConnectivityManager() }
                single<IBalanceHiddenManager> { balanceHiddenManager }
            })
        }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
    }

    @Test
    fun balanceHidden_hiddenOnTokenScreenBeforeOpening_isHiddenOnSendScreen() = runTest(dispatcher) {
        balanceHiddenManager.toggleWalletBalanceHidden(wallet.tokenQueryId)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.balanceHidden)
    }

    @Test
    fun balanceHidden_walletHiddenWhileSendScreenOpen_updatesState() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.balanceHidden)

        balanceHiddenManager.toggleWalletBalanceHidden(wallet.tokenQueryId)
        advanceUntilIdle()

        assertTrue(viewModel.balanceHidden)
    }

    @Test
    fun balanceHidden_otherWalletHidden_staysVisibleOnSendScreen() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        balanceHiddenManager.toggleWalletBalanceHidden(otherWallet.tokenQueryId)
        advanceUntilIdle()

        assertFalse(viewModel.balanceHidden)
    }

    @Test
    fun balanceHidden_globalHidden_isHiddenOnSendScreen() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        balanceHiddenManager.setBalanceHidden(true)
        advanceUntilIdle()

        assertTrue(viewModel.balanceHidden)
    }

    @Test
    fun toggleHideBalance_hidesThisWalletOnly_keepsGlobalStateIntact() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleHideBalance()
        advanceUntilIdle()

        assertTrue(viewModel.balanceHidden)
        assertTrue(balanceHiddenManager.isWalletBalanceHidden(wallet.tokenQueryId))
        assertFalse(balanceHiddenManager.isWalletBalanceHidden(otherWallet.tokenQueryId))
        assertFalse(balanceHiddenManager.balanceHidden)
        verify(exactly = 0) { localStorage.balanceHidden = any() }
    }

    private fun createViewModel(): BalanceHiddenTestSendViewModel {
        val viewModel = BalanceHiddenTestSendViewModel(wallet, adapterManager)
        viewModelStore.put("test-vm", viewModel)
        return viewModel
    }
}

/** Minimal concrete subclass: the visibility logic under test lives entirely in the base class. */
private class BalanceHiddenTestSendViewModel(
    wallet: Wallet,
    adapterManager: IAdapterManager,
) : BaseSendViewModel<BalanceHiddenTestUiState>(wallet, adapterManager) {
    override fun createState() = BalanceHiddenTestUiState()
}

private data class BalanceHiddenTestUiState(val dummy: Boolean = true)
