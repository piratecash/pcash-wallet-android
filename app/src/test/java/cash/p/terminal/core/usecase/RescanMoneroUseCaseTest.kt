package cash.p.terminal.core.usecase

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.wallet.Account
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RescanMoneroUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher))
    private val moneroKitManager = mockk<MoneroKitManager>(relaxed = true)
    private val account = mockk<Account>()

    private val useCase = RescanMoneroUseCase(moneroKitManager, dispatcherProvider)

    @Test
    fun invoke_delegatesToManagerRescan() = runTest(dispatcher) {
        useCase(account, 2_975_499L)

        coVerify(exactly = 1) { moneroKitManager.rescan(account, 2_975_499L) }
    }
}
