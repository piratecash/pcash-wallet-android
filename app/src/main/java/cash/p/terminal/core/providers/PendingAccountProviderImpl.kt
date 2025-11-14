package cash.p.terminal.core.providers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IWalletManager

class PendingAccountProviderImpl(
    private val walletManager: IWalletManager
) : PendingAccountProvider {

    override fun fromWalletId(walletId: String): Account {
        return walletManager.activeWallets
            .firstOrNull { it.account.id == walletId }
            ?.account
            ?: throw IllegalStateException("Wallet with id $walletId not found")
    }
}
