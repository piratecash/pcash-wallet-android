package cash.p.terminal.core.usecase

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class DeleteAccountUseCaseTest {
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val moneroKitManager = mockk<MoneroKitManager>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val account = Account(
        "account-id", "Trezor", AccountType.TrezorDevice("device-id", "T3T1", "2.8.10", "wallet-key"), AccountOrigin.Created, 0,
    )
    @Test
    fun invoke_trezorAccount_retriesAccountDeletion() = runTest {
        val calls = mutableListOf<String>()
        var deleteAttempts = 0
        coEvery { moneroKitManager.deleteForAccount(account, any(), any()) } coAnswers {
            arg<suspend () -> Unit>(2).invoke()
            arg<suspend () -> Unit>(1).invoke()
        }
        coEvery { adapterManager.stopAdapters(listOf(account.id), BlockchainType.Monero) } coAnswers { calls += "stop" }
        coEvery { accountManager.delete(account.id) } coAnswers {
            calls += "delete"
            if (++deleteAttempts == 1) error("first attempt")
        }
        useCase(this)(account)
        assertEquals(listOf("delete", "delete", "stop"), calls)
    }
    private fun useCase(applicationScope: CoroutineScope) = DeleteAccountUseCase(
        accountManager,
        moneroKitManager,
        adapterManager,
        TestDispatcherProvider(StandardTestDispatcher(), applicationScope),
    )
}
