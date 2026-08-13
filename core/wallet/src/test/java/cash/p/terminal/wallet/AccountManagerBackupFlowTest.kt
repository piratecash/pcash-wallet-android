package cash.p.terminal.wallet

import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.test.assertFailsWith

class AccountManagerBackupFlowTest {

    @Test
    fun delete_pendingBackupAccount_clearsBackupNotification() = runTest {
        val account = trezorAccount()
        val storage = mockk<IAccountsStorage>(relaxed = true) {
            every { loadAccount(account.id) } returns account
        }
        val getMoneroWalletFilesNameUseCase =
            mockk<IGetMoneroWalletFilesNameUseCase>()
        coEvery { getMoneroWalletFilesNameUseCase(account) } returns null
        val accountManager = accountManager(storage, getMoneroWalletFilesNameUseCase)
        accountManager.save(account, updateActive = false)
        assertEquals(account, accountManager.newAccountBackupRequiredFlow.value)

        accountManager.delete(account.id)

        assertNull(accountManager.newAccountBackupRequiredFlow.value)
    }

    @Test
    fun delete_storageDeleteFails_doesNotRemoveMoneroWalletFiles() = runTest {
        val account = trezorAccount()
        val storage = mockk<IAccountsStorage> {
            every { loadAccount(account.id) } returns account
            every { delete(account.id) } throws IllegalStateException("Storage unavailable")
        }
        val getMoneroWalletFilesNameUseCase = mockk<IGetMoneroWalletFilesNameUseCase>()
        coEvery { getMoneroWalletFilesNameUseCase(account) } returns "wallet-file"
        val removeMoneroWalletFilesUseCase = mockk<RemoveMoneroWalletFilesUseCase>(relaxed = true)
        val accountManager = accountManager(
            storage,
            getMoneroWalletFilesNameUseCase,
            removeMoneroWalletFilesUseCase,
        )
        assertFailsWith<IllegalStateException> { accountManager.delete(account.id) }
        coVerify(exactly = 0) { removeMoneroWalletFilesUseCase(any<String>()) }
    }

    private fun trezorAccount() = Account(
        id = "account-id",
        name = "Trezor",
        type = AccountType.TrezorDevice("device-id", "T3T1", "2.8.10", "wallet-public-key"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private fun accountManager(
        storage: IAccountsStorage,
        getWalletFilesName: IGetMoneroWalletFilesNameUseCase,
        removeWalletFiles: RemoveMoneroWalletFilesUseCase = mockk(relaxed = true),
    ) = AccountManager(
        storage = storage,
        getMoneroWalletFilesNameUseCase = getWalletFilesName,
        removeMoneroWalletFilesUseCase = removeWalletFiles,
        balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true),
    )
}
