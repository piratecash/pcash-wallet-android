package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.EnabledWallet

interface IWalletStorage {
    suspend fun wallets(account: Account): List<Wallet>
    fun save(wallets: List<Wallet>)
    fun delete(wallets: List<Wallet>)
    fun deleteByTokenQueryId(accountId: String, tokenQueryId: String)
    fun handle(newEnabledWallets: List<EnabledWallet>): List<Long>
    fun clear()
}