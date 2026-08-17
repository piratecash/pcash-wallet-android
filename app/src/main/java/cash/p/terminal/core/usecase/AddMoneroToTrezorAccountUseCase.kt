package cash.p.terminal.core.usecase

import cash.p.terminal.core.managers.MoneroDeviceWalletProvisioner
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.latestAccountOr
import cash.p.terminal.wallet.monero
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AddMoneroToTrezorAccountUseCase(
    private val provisioner: MoneroDeviceWalletProvisioner,
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase,
    private val walletActivator: WalletActivator,
    private val accountManager: IAccountManager,
) {
    private val provisioningMutex = Mutex()

    suspend operator fun invoke(account: Account) {
        val provisionedAccount = provision(account)
        walletActivator.activateWalletsSuspended(provisionedAccount, listOf(TokenQuery.monero))
    }

    suspend fun provision(account: Account): Account = provisioningMutex.withLock {
        val latestAccount = accountManager.latestAccountOr(account)
        check(latestAccount.type is AccountType.TrezorDevice) {
            "Monero device wallet requires a Trezor account"
        }
        val walletPublicKey = provisioner.provision(
            account = latestAccount,
            restoreHeight = validateMoneroHeightUseCase.getTodayHeight(),
        )
        val accountType = latestAccount.type as AccountType.TrezorDevice
        val updatedAccount = if (accountType.walletPublicKey.isNotEmpty()) {
            check(accountType.walletPublicKey == walletPublicKey) {
                "Connected Trezor passphrase wallet does not match the account"
            }
            latestAccount
        } else {
            latestAccount.copy(type = accountType.copy(walletPublicKey = walletPublicKey))
                .also(accountManager::update)
        }
        updatedAccount
    }
}
