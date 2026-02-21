package cash.p.terminal.modules.balance

import cash.p.terminal.core.managers.PendingBalanceCalculator
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.core.adapters.BaseTronAdapter
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.balance.BalanceWarning
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal

class BalanceAdapterRepository(
    private val adapterManager: IAdapterManager,
    private val balanceCache: BalanceCache,
    private val pendingBalanceCalculator: PendingBalanceCalculator,
    dispatcherProvider: DispatcherProvider
) : Clearable {
    private var wallets = listOf<Wallet>()

    private val coroutineScope = CoroutineScope(dispatcherProvider.io)

    private val readySubject = PublishSubject.create<Unit>()
    val readyObservable: Observable<Unit> get() = readySubject

    private val updatesSubject = PublishSubject.create<Wallet>()
    val updatesObservable: Observable<Wallet> get() = updatesSubject

    init {
        coroutineScope.launch {
            adapterManager.adaptersReadyObservable.asFlow().collect {
                readySubject.onNext(Unit)
                balanceCache.setCache(
                    wallets.mapNotNull { wallet ->
                        adapterManager.getAdjustedBalanceData(wallet)?.let { wallet to it }
                    }.toMap()
                )
            }
        }
        coroutineScope.launch {
            adapterManager.walletBalanceUpdatedFlow.collect { wallet ->
                if (wallet in wallets) {
                    notifyWalletUpdate(wallet)
                }
            }
        }
        coroutineScope.launch {
            pendingBalanceCalculator.pendingChangedFlow.collect {
                wallets.forEach(::notifyWalletUpdate)
            }
        }
    }

    override fun clear() {
        coroutineScope.cancel()
    }

    fun setWallet(wallets: List<Wallet>) {
        this.wallets = wallets
    }

    private fun notifyWalletUpdate(wallet: Wallet) {
        updatesSubject.onNext(wallet)
        adapterManager.getAdjustedBalanceData(wallet)?.let {
            balanceCache.setCache(wallet, it)
        }
    }

    fun state(wallet: Wallet): AdapterState {
        return adapterManager.getBalanceAdapterForWallet(wallet)?.balanceState
            ?: if (adapterManager.initializationInProgressFlow.value) {
                AdapterState.Syncing()
            } else {
                AdapterState.NotSynced(Exception("Adapter unavailable"))
            }
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