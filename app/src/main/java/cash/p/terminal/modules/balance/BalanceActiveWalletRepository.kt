package cash.p.terminal.modules.balance

import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import io.reactivex.Observable
import kotlinx.coroutines.rx2.asObservable

class BalanceActiveWalletRepository(
    private val walletManager: IWalletManager,
    private val userDeletedWalletManager: UserDeletedWalletManager,
    evmSyncSourceManager: EvmSyncSourceManager
) {

    val itemsObservable: Observable<List<Wallet>> =
        Observable
            .merge(
                Observable.just(Unit),
                walletManager.activeWalletsFlow.asObservable(),
                evmSyncSourceManager.syncSourceObservable
            )
            .map {
                walletManager.activeWallets
            }

    suspend fun disable(wallet: Wallet) {
        userDeletedWalletManager.markAsDeleted(wallet)
        walletManager.deleteByWallet(wallet)
    }

    fun enable(wallet: Wallet) {
        walletManager.save(listOf(wallet))
    }

}
