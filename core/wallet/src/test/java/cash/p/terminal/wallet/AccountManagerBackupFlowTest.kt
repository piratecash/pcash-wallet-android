package cash.p.terminal.wallet

import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountManagerBackupFlowTest {

    @Test
    fun delete_pendingBackupAccount_clearsBackupNotification() = runTest {
        val account = Account(
            id = "account-id",
            name = "Trezor",
            type = AccountType.TrezorDevice(
                deviceId = "device-id",
                model = "T3T1",
                firmwareVersion = "2.8.10",
                walletPublicKey = "wallet-public-key",
            ),
            origin = AccountOrigin.Created,
            level = 0,
            isBackedUp = false,
            isFileBackedUp = false,
        )
        val storage = mockk<IAccountsStorage>(relaxed = true) {
            every { loadAccount(account.id) } returns account
        }
        val getMoneroWalletFilesNameUseCase =
            mockk<IGetMoneroWalletFilesNameUseCase>()
        coEvery { getMoneroWalletFilesNameUseCase(account) } returns null
        val accountManager = AccountManager(
            storage = storage,
            getMoneroWalletFilesNameUseCase = getMoneroWalletFilesNameUseCase,
            removeMoneroWalletFilesUseCase = mockk<RemoveMoneroWalletFilesUseCase>(relaxed = true),
            balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true),
        )
        accountManager.save(account, updateActive = false)
        assertEquals(account, accountManager.newAccountBackupRequiredFlow.value)

        accountManager.delete(account.id)

        assertNull(accountManager.newAccountBackupRequiredFlow.value)
    }
}
