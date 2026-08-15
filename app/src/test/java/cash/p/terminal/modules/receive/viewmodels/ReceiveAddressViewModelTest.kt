package cash.p.terminal.modules.receive.viewmodels

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Flowable
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveAddressViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val testDispatcherProvider = TestDispatcherProvider(dispatcher, testScope)

    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val adapter = mockk<IReceiveAdapter>(relaxed = true)
    private val initializationInProgress = MutableStateFlow(false)

    private val account = mockk<Account> {
        every { isWatchAccount } returns false
    }
    private val coin = mockk<Coin> {
        every { code } returns "ZEC"
    }
    private val blockchain = mockk<Blockchain> {
        every { name } returns "Zcash"
    }
    private val token = mockk<Token>(relaxed = true) {
        every { this@mockk.blockchain } returns this@ReceiveAddressViewModelTest.blockchain
        every { blockchainType } returns BlockchainType.Zcash
        every { type } returns TokenType.Native
    }
    private val wallet = mockk<Wallet>(relaxed = true) {
        every { this@mockk.account } returns this@ReceiveAddressViewModelTest.account
        every { this@mockk.coin } returns this@ReceiveAddressViewModelTest.coin
        every { this@mockk.token } returns this@ReceiveAddressViewModelTest.token
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { adapterManager.adaptersReadyObservable } returns Flowable.empty()
        every { adapterManager.initializationInProgressFlow } returns initializationInProgress
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns null
        coEvery { adapterManager.getReceiveAddressForWallet(wallet) } returns null
        every { adapter.receiveAddress } returns ADAPTER_ADDRESS
        every { adapter.isMainNet } returns true
        every { adapter.isAddressHistorySupported } returns false
        every { adapter.usedAddresses(any()) } returns emptyList()
        coEvery { adapter.isAddressActive(any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ReceiveAddressViewModel(
        wallet = wallet,
        adapterManager = adapterManager,
        dispatcherProvider = testDispatcherProvider,
    )

    @Test
    fun setData_adapterMissingWhileInitializing_showsLoading() = runTest(dispatcher) {
        initializationInProgress.value = true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Loading, viewModel.uiState.viewState)
    }

    @Test
    fun setData_adapterMissingAfterInitFinished_showsError() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.viewState is ViewState.Error)
    }

    @Test
    fun setData_adapterMissingButFallbackAddressAvailable_showsSuccessWithAddress() =
        runTest(dispatcher) {
            coEvery { adapterManager.getReceiveAddressForWallet(wallet) } returns FALLBACK_ADDRESS

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(ViewState.Success, viewModel.uiState.viewState)
            assertEquals(FALLBACK_ADDRESS, viewModel.uiState.address)
        }

    @Test
    fun setData_isAddressActiveThrows_keepsSuccessAndAssumesActive() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.isAddressActive(any()) } throws IllegalStateException("no network")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
        assertEquals(false, viewModel.uiState.showTronAlert)
    }

    @Test
    fun setData_isAddressActiveReturnsFalse_keepsSuccessAndShowsNotActive() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.isAddressActive(any()) } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(true, viewModel.uiState.showTronAlert)
    }

    @Test
    fun initializationFinished_adapterAppears_replacesFallbackWithAdapterAddress() =
        runTest(dispatcher) {
            initializationInProgress.value = true
            coEvery { adapterManager.getReceiveAddressForWallet(wallet) } returns FALLBACK_ADDRESS

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(FALLBACK_ADDRESS, viewModel.uiState.address)

            every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
            initializationInProgress.value = false
            advanceUntilIdle()

            assertEquals(ViewState.Success, viewModel.uiState.viewState)
            assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
        }

    private companion object {
        const val ADAPTER_ADDRESS = "u1adapteraddress"
        const val FALLBACK_ADDRESS = "u1fallbackaddress"
    }
}
