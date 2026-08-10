package cash.p.terminal.modules.balance.token.creationblock

import cash.p.terminal.core.usecase.GetRestoreHeightForWalletUseCase
import cash.p.terminal.core.usecase.RescanMoneroUseCase
import cash.p.terminal.core.usecase.RescanZcashUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreationBlockViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val wallet = mockk<Wallet>()
    private val account = mockk<Account>()
    private val token = mockk<Token>()
    private val getRestoreHeightForWalletUseCase = mockk<GetRestoreHeightForWalletUseCase>()
    private val validateMoneroHeightUseCase = mockk<ValidateMoneroHeightUseCase>(relaxed = true)
    private val getZcashHeightUseCase = mockk<GetZcashHeightUseCase>(relaxed = true)
    private val rescanMoneroUseCase = mockk<RescanMoneroUseCase>()
    private val rescanZcashUseCase = mockk<RescanZcashUseCase>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { wallet.account } returns account
        every { wallet.token } returns token
        every { token.blockchainType } returns BlockchainType.Monero
        coEvery { getRestoreHeightForWalletUseCase(wallet) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = CreationBlockViewModel(
        wallet = wallet,
        getRestoreHeightForWalletUseCase = getRestoreHeightForWalletUseCase,
        validateMoneroHeightUseCase = validateMoneroHeightUseCase,
        getZcashHeightUseCase = getZcashHeightUseCase,
        rescanMoneroUseCase = rescanMoneroUseCase,
        rescanZcashUseCase = rescanZcashUseCase,
    )

    @Test
    fun onRescanConfirmed_moneroRescanSuspended_marksRescanStartedBeforeCompletion() =
        runTest(dispatcher) {
            val rescanEntered = CompletableDeferred<Unit>()
            val releaseRescan = CompletableDeferred<Unit>()
            coEvery { rescanMoneroUseCase(account, 123L) } coAnswers {
                rescanEntered.complete(Unit)
                releaseRescan.await()
            }
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onHeightChange("123")

            viewModel.onRescanConfirmed()
            runCurrent()

            try {
                assertTrue(rescanEntered.isCompleted)
                assertTrue(viewModel.uiState.rescanStarted)
                coVerify(exactly = 1) { rescanMoneroUseCase(account, 123L) }
            } finally {
                releaseRescan.complete(Unit)
                advanceUntilIdle()
            }
        }

    @Test
    fun onRescanConfirmed_zcashRescanSuspended_marksRescanStartedOnlyAfterCompletion() =
        runTest(dispatcher) {
            val releaseRescan = CompletableDeferred<Unit>()
            every { token.blockchainType } returns BlockchainType.Zcash
            coEvery { rescanZcashUseCase(account, 123L) } coAnswers { releaseRescan.await() }
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onHeightChange("123")

            viewModel.onRescanConfirmed()
            runCurrent()

            assertFalse(viewModel.uiState.rescanStarted)
            releaseRescan.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.rescanStarted)
        }
}
