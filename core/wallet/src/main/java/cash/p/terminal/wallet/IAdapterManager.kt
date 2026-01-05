package cash.p.terminal.wallet

import io.reactivex.Flowable
import kotlinx.coroutines.flow.StateFlow

interface IAdapterManager {
    val adaptersReadyObservable: Flowable<Map<Wallet, IAdapter>>
    val initializationInProgressFlow: StateFlow<Boolean>

    fun startAdapterManager()
    suspend fun refresh()

    /**
     * Wait for adapter for the given wallet to be available
     * If adapter is already available, return it
     * @param timeoutMs Maximum time to wait for adapter initialization (default 300ms)
     */
    suspend fun <T> awaitAdapterForWallet(wallet: Wallet, timeoutMs: Long = 300): T?

    fun <T> getAdapterForWallet(wallet: Wallet): T?
    fun getAdapterForWalletOld(wallet: Wallet): IAdapter?
    fun <T> getAdapterForToken(token: Token): T?
    fun getBalanceAdapterForWallet(wallet: Wallet): IBalanceAdapter?
    fun getReceiveAdapterForWallet(wallet: Wallet): IReceiveAdapter?
    fun refreshAdapters(wallets: List<Wallet>)
    fun refreshByWallet(wallet: Wallet)
}