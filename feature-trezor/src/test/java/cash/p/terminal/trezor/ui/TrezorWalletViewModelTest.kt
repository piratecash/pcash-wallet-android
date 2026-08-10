package cash.p.terminal.trezor.ui

import androidx.lifecycle.ViewModelStore
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.trezor.domain.usecase.ICreateTrezorWalletUseCase
import cash.p.terminal.trezor.domain.usecase.TrezorMoneroRestoreHeightProvider
import cash.p.terminal.trezor.domain.usecase.TrezorMoneroRestoreHeightResolver
import cash.p.terminal.trezorkit.TrezorNotInitializedException
import cash.p.terminal.wallet.AccountType
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrezorWalletViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private val heightResolver = mockk<TrezorMoneroRestoreHeightResolver>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun connectTrezor_success_keepsLoadingUntilWalletCreationCompletes() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val viewModel = viewModel { completion.await() }

        viewModel.connectTrezor()

        assertTrue(viewModel.uiState.loading)
        completion.complete(Unit)

        assertFalse(viewModel.uiState.loading)
        assertTrue(viewModel.uiState.success)
    }

    @Test
    fun connectTrezor_notInitialized_clearsLoadingAndShowsSetupPrompt() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val viewModel = viewModel {
            completion.await()
            throw TrezorNotInitializedException("Not initialized")
        }

        viewModel.connectTrezor()

        assertTrue(viewModel.uiState.loading)
        completion.complete(Unit)

        assertFalse(viewModel.uiState.loading)
        assertTrue(viewModel.uiState.showNotInitialized)
        assertFalse(viewModel.uiState.success)
    }

    @Test
    fun connectTrezor_userCancellation_clearsLoadingWithoutSuccessOrPrompt() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val viewModel = viewModel {
            completion.await()
            throw TrezorCancelledException()
        }

        viewModel.connectTrezor()

        assertTrue(viewModel.uiState.loading)
        completion.complete(Unit)

        assertFalse(viewModel.uiState.loading)
        assertFalse(viewModel.uiState.success)
        assertFalse(viewModel.uiState.showNotInitialized)
    }

    @Test
    fun connectTrezor_failure_clearsLoadingWithoutSuccessOrPrompt() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val viewModel = viewModel {
            completion.await()
            error("failed")
        }

        viewModel.connectTrezor()

        assertTrue(viewModel.uiState.loading)
        completion.complete(Unit)

        assertFalse(viewModel.uiState.loading)
        assertFalse(viewModel.uiState.success)
        assertFalse(viewModel.uiState.showNotInitialized)
    }

    @Test
    fun connectTrezor_duplicateInvocation_doesNotCancelOrRestartActiveOperation() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        var invocationCount = 0
        val viewModel = viewModel {
            invocationCount += 1
            completion.await()
        }

        viewModel.connectTrezor()
        viewModel.connectTrezor()

        assertTrue(viewModel.uiState.loading)
        assertEquals(1, invocationCount)
        completion.complete(Unit)

        assertFalse(viewModel.uiState.loading)
        assertTrue(viewModel.uiState.success)
        assertEquals(1, invocationCount)
    }

    @Test
    fun connectTrezor_viewModelCleared_cancelsWalletCreation() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val viewModel = viewModel {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }

        viewModel.connectTrezor()
        started.await()

        assertTrue(viewModel.uiState.loading)
        viewModelStore.clear()
        cancelled.await()
        assertFalse(viewModel.uiState.loading)
    }

    @Test
    fun connectTrezor_restoreHeightRequested_showsUnselectedChoiceWithoutSpinner() =
        runTest(dispatcher) {
            val viewModel = viewModel { provider ->
                provider.getRestoreHeight()
            }

            viewModel.connectTrezor()

            assertFalse(viewModel.uiState.loading)
            assertTrue(viewModel.uiState.moneroRestore.visible)
            assertEquals(null, viewModel.uiState.moneroRestore.mode)
            assertFalse(viewModel.uiState.moneroRestore.canSubmit)
            assertFalse(viewModel.uiState.success)
        }

    @Test
    fun submitMoneroRestore_newWallet_usesTodayHeightAndResumesCreation() =
        runTest(dispatcher) {
            val resolvedHeight = CompletableDeferred<Long>()
            val finishCreation = CompletableDeferred<Unit>()
            io.mockk.every { heightResolver.getTodayHeight() } returns RESTORE_HEIGHT
            val viewModel = viewModel { provider ->
                resolvedHeight.complete(provider.getRestoreHeight())
                finishCreation.await()
            }
            viewModel.connectTrezor()

            viewModel.selectMoneroRestoreMode(TrezorMoneroRestoreMode.NewWallet)
            assertTrue(viewModel.uiState.moneroRestore.canSubmit)
            viewModel.submitMoneroRestore()

            assertEquals(RESTORE_HEIGHT, resolvedHeight.await())
            assertFalse(viewModel.uiState.moneroRestore.visible)
            assertTrue(viewModel.uiState.loading)
            finishCreation.complete(Unit)
            assertTrue(viewModel.uiState.success)
        }

    @Test
    fun submitMoneroRestore_newWalletWithoutAutomaticHeight_keepsPromptAndShowsError() =
        runTest(dispatcher) {
            io.mockk.every { heightResolver.getTodayHeight() } returns -1L
            val viewModel = viewModel { provider ->
                provider.getRestoreHeight()
            }
            viewModel.connectTrezor()
            viewModel.selectMoneroRestoreMode(TrezorMoneroRestoreMode.NewWallet)

            viewModel.submitMoneroRestore()

            assertTrue(viewModel.uiState.moneroRestore.visible)
            assertTrue(viewModel.uiState.moneroRestore.automaticHeightUnavailable)
            assertFalse(viewModel.uiState.moneroRestore.invalidHeight)
            assertFalse(viewModel.uiState.loading)
            assertFalse(viewModel.uiState.success)
        }

    @Test
    fun submitMoneroRestore_existingWalletWithInvalidHeight_keepsPromptAndShowsError() =
        runTest(dispatcher) {
            io.mockk.every { heightResolver.resolve("not-a-height") } returns -1L
            val viewModel = viewModel { provider ->
                provider.getRestoreHeight()
            }
            viewModel.connectTrezor()
            viewModel.selectMoneroRestoreMode(TrezorMoneroRestoreMode.ExistingWallet)
            viewModel.setMoneroRestoreHeight("not-a-height")

            viewModel.submitMoneroRestore()

            assertTrue(viewModel.uiState.moneroRestore.visible)
            assertTrue(viewModel.uiState.moneroRestore.invalidHeight)
            assertFalse(viewModel.uiState.loading)
            assertFalse(viewModel.uiState.success)
        }

    @Test
    fun submitMoneroRestore_existingWalletWithValidHeight_resumesCreation() =
        runTest(dispatcher) {
            val resolvedHeight = CompletableDeferred<Long>()
            val finishCreation = CompletableDeferred<Unit>()
            io.mockk.every { heightResolver.resolve("2020-01-01") } returns RESTORE_HEIGHT
            val viewModel = viewModel { provider ->
                resolvedHeight.complete(provider.getRestoreHeight())
                finishCreation.await()
            }
            viewModel.connectTrezor()
            viewModel.selectMoneroRestoreMode(TrezorMoneroRestoreMode.ExistingWallet)
            viewModel.setMoneroRestoreHeight("2020-01-01")

            viewModel.submitMoneroRestore()

            assertEquals(RESTORE_HEIGHT, resolvedHeight.await())
            assertFalse(viewModel.uiState.moneroRestore.visible)
            assertTrue(viewModel.uiState.loading)
            finishCreation.complete(Unit)
            assertTrue(viewModel.uiState.success)
        }

    @Test
    fun connectTrezor_viewModelClearedWhileRestoreChoiceVisible_cancelsRequest() =
        runTest(dispatcher) {
            val cancelled = CompletableDeferred<Unit>()
            val viewModel = viewModel { provider ->
                try {
                    provider.getRestoreHeight()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            viewModel.connectTrezor()
            assertTrue(viewModel.uiState.moneroRestore.visible)

            viewModelStore.clear()

            cancelled.await()
            assertFalse(viewModel.uiState.loading)
        }

    private fun viewModel(block: suspend (TrezorMoneroRestoreHeightProvider) -> Unit): TrezorWalletViewModel {
        val useCase = object : ICreateTrezorWalletUseCase {
            override suspend fun invoke(
                accountName: String,
                moneroRestoreHeightProvider: TrezorMoneroRestoreHeightProvider,
            ): AccountType.TrezorDevice {
                block(moneroRestoreHeightProvider)
                return mockk<AccountType.TrezorDevice>()
            }
        }
        return TrezorWalletViewModel(
            accountName = "Trezor",
            createTrezorWalletUseCase = useCase,
            moneroRestoreHeightResolver = heightResolver,
        ).also { viewModelStore.put("test-vm", it) }
    }

    private companion object {
        const val RESTORE_HEIGHT = 3_529_956L
    }
}
