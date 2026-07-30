package cash.p.terminal.core.usecase

import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.SecretString
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetMoneroWalletFilesNameUseCaseTest {

    @Test
    fun invoke_trezorAccount_returnsAssociatedWalletFile() = runTest {
        val dao = mockk<MoneroFileDao> {
            coEvery { getAssociatedRecord(ACCOUNT_ID) } returns MoneroFileRecord(
                accountId = ACCOUNT_ID,
                fileName = SecretString(WALLET_FILE),
                password = SecretString("password"),
            )
        }
        val useCase = GetMoneroWalletFilesNameUseCase(dao)

        val result = useCase(trezorAccount())

        assertEquals(WALLET_FILE, result)
    }

    private fun trezorAccount() = Account(
        id = ACCOUNT_ID,
        name = "Safe 5",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-key",
        ),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val WALLET_FILE = "trezor-account-id"
    }
}
