package cash.p.terminal.core.providers

import cash.p.terminal.wallet.Account

interface PendingAccountProvider {
    fun fromWalletId(walletId: String): Account
}
