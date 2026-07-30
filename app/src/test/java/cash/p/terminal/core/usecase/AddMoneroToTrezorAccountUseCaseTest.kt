package cash.p.terminal.core.usecase

import cash.p.terminal.core.managers.MoneroDeviceWalletProvisioner
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.monero
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import org.junit.Test

class AddMoneroToTrezorAccountUseCaseTest {
    private val provisioner = mockk<MoneroDeviceWalletProvisioner>()
    private val heightUseCase = mockk<ValidateMoneroHeightUseCase> {
        every { getTodayHeight() } returns RESTORE_HEIGHT
    }
    private val walletActivator = mockk<WalletActivator>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val useCase = AddMoneroToTrezorAccountUseCase(
        provisioner,
        heightUseCase,
        walletActivator,
        accountManager,
    )

    @Test
    fun provision_walletIdentityMissing_updatesAccountWithoutActivation() = runTest {
        val account = account(walletPublicKey = "")
        every { accountManager.account(account.id) } returns account
        coEvery {
            provisioner.provision(account, RESTORE_HEIGHT)
        } returns WALLET_PUBLIC_KEY

        val provisionedAccount = useCase.provision(account)

        assertEquals(WALLET_PUBLIC_KEY, provisionedAccount.trezorType.walletPublicKey)
        verify(exactly = 1) { accountManager.update(provisionedAccount) }
        coVerify(exactly = 0) { walletActivator.activateWalletsSuspended(any(), any()) }
    }

    @Test
    fun invoke_walletProvisioned_activatesMonero() = runTest {
        val account = account(walletPublicKey = WALLET_PUBLIC_KEY)
        every { accountManager.account(account.id) } returns account
        coEvery {
            provisioner.provision(account, RESTORE_HEIGHT)
        } returns WALLET_PUBLIC_KEY

        useCase(account)

        coVerify(exactly = 1) {
            walletActivator.activateWalletsSuspended(account, listOf(TokenQuery.monero))
        }
    }

    @Test
    fun provision_concurrentStaleAccount_refreshesIdentityBeforeSecondProvision() = runTest {
        val staleAccount = account(walletPublicKey = "")
        var storedAccount: Account? = null
        val provisionedAccounts = mutableListOf<Account>()
        every { accountManager.account(staleAccount.id) } answers { storedAccount }
        every { accountManager.update(any()) } answers {
            storedAccount = firstArg<Account>()
        }
        coEvery {
            provisioner.provision(any(), RESTORE_HEIGHT)
        } coAnswers {
            provisionedAccounts += firstArg<Account>()
            WALLET_PUBLIC_KEY
        }

        awaitAll(
            async { useCase.provision(staleAccount) },
            async { useCase.provision(staleAccount) },
        )

        assertEquals("", provisionedAccounts.first().trezorType.walletPublicKey)
        assertEquals(WALLET_PUBLIC_KEY, provisionedAccounts.last().trezorType.walletPublicKey)
        verify(exactly = 1) { accountManager.update(any()) }
    }

    private fun account(walletPublicKey: String) = Account(
        id = "account-id",
        name = "Trezor",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = walletPublicKey,
        ),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val Account.trezorType: AccountType.TrezorDevice
        get() = type as AccountType.TrezorDevice

    private companion object {
        const val RESTORE_HEIGHT = 3_529_956L
        const val WALLET_PUBLIC_KEY = "wallet-public-key"
    }
}
