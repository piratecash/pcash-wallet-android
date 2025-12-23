package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.EnabledWallet
import kotlinx.coroutines.flow.StateFlow

interface IWalletManager {
    val activeWallets: List<Wallet>
    val activeWalletsFlow: StateFlow<List<Wallet>>

    fun save(wallets: List<Wallet>)
    suspend fun saveSuspended(wallets: List<Wallet>)
    suspend fun saveEnabledWallets(enabledWallets: List<EnabledWallet>)
    fun delete(wallets: List<Wallet>)
    suspend fun deleteByWallet(wallet: Wallet)
    fun clear()
    fun handle(newWallets: List<Wallet>, deletedWallets: List<Wallet>)
    suspend fun getWallets(account: Account): List<Wallet>
}