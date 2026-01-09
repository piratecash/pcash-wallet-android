package cash.p.terminal.modules.balance

import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.core.adapters.BaseTronAdapter
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.balance.BalanceWarning
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal

class BalanceAdapterRepository(
    private val adapterManager: IAdapterManager,
    private val balanceCache: BalanceCache
) : Clearable {
    private var wallets = listOf<Wallet>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var subscriptionsScope: CoroutineScope? = null

    private val readySubject = PublishSubject.create<Unit>()
    val readyObservable: Observable<Unit> get() = readySubject

    private val updatesSubject = PublishSubject.create<Wallet>()
    val updatesObservable: Observable<Wallet> get() = updatesSubject

    init {
        coroutineScope.launch {
            adapterManager.adaptersReadyObservable.asFlow().collect {
                unsubscribeFromAdapterUpdates()
                readySubject.onNext(Unit)

                balanceCache.setCache(
                    wallets.mapNotNull { wallet ->
                        adapterManager.getAdjustedBalanceData(wallet)?.let {
                            wallet to it
                        }
                    }.toMap()
                )

                subscribeForAdapterUpdates()
            }
        }
    }

    override fun clear() {
        subscriptionsScope?.cancel()
        coroutineScope.cancel()
    }

    fun setWallet(wallets: List<Wallet>) {
        unsubscribeFromAdapterUpdates()
        this.wallets = wallets
        subscribeForAdapterUpdates()
    }

    private fun unsubscribeFromAdapterUpdates() {
        subscriptionsScope?.cancel()
        subscriptionsScope = null
    }

    private fun subscribeForAdapterUpdates() {
        subscriptionsScope = CoroutineScope(coroutineScope.coroutineContext + Job())

        wallets.forEach { wallet ->
            adapterManager.getBalanceAdapterForWallet(wallet)?.let { adapter ->
                subscriptionsScope?.launch {
                    adapter.balanceStateUpdatedFlow.collect {
                        updatesSubject.onNext(wallet)
                    }
                }

                subscriptionsScope?.launch {
                    adapter.balanceUpdatedFlow.collect {
                        updatesSubject.onNext(wallet)

                        adapterManager.getAdjustedBalanceData(wallet)?.let {
                            balanceCache.setCache(wallet, it)
                        }
                    }
                }
            }
        }
    }

    fun state(wallet: Wallet): AdapterState {
        return adapterManager.getBalanceAdapterForWallet(wallet)?.balanceState
            ?: AdapterState.Syncing()
    }

    fun balanceData(wallet: Wallet): BalanceData {
        return adapterManager.getAdjustedBalanceData(wallet)
            ?: balanceCache.getCache(wallet)
            ?: BalanceData(BigDecimal.ZERO)
    }

    fun sendAllowed(wallet: Wallet): Boolean {
        return adapterManager.getBalanceAdapterForWallet(wallet)?.sendAllowed() ?: false
    }

    suspend fun warning(wallet: Wallet): BalanceWarning? {
        try {
            if (wallet.token.blockchainType is BlockchainType.Tron) {
                (adapterManager.getAdapterForWalletOld(wallet) as? BaseTronAdapter)?.let { adapter ->
                    if (!adapter.isAddressActive(adapter.receiveAddress))
                        return BalanceWarning.TronInactiveAccountWarning
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun refresh() {
        adapterManager.refresh()
    }

}