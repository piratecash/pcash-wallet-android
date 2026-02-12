package cash.p.terminal.modules.btcblockchainsettings

import cash.p.terminal.core.adapters.BitcoinBaseAdapter
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import io.horizontalsystems.core.entities.Blockchain
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject

class BtcBlockchainSettingsService(
    val blockchain: Blockchain,
    private val btcBlockchainManager: BtcBlockchainManager,
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager
) {

    private val hasChangesSubject = BehaviorSubject.create<Boolean>()
    val hasChangesObservable: Observable<Boolean>
        get() = hasChangesSubject

    var restoreMode: BtcRestoreMode = btcBlockchainManager.restoreMode(blockchain.type)
        private set

    val restoreModes: List<BtcRestoreMode>
        get() = btcBlockchainManager.availableRestoreModes(blockchain.type)

    fun save(forceUpdate: Boolean) {
        if (forceUpdate || restoreMode != btcBlockchainManager.restoreMode(blockchain.type)) {
            btcBlockchainManager.save(restoreMode, blockchain.type)
        }
    }

    fun setRestoreMode(id: String) {
        restoreMode = BtcRestoreMode.values().first { it.raw == id }
        syncHasChanges()
    }

    fun getStatusInfo(): List<Pair<String, Map<String, Any>>> {
        return walletManager.activeWallets
            .filter { it.token.blockchainType == blockchain.type }
            .mapNotNull { wallet ->
                val adapter = adapterManager.getAdapterForWallet<BitcoinBaseAdapter>(wallet)
                    ?: return@mapNotNull null
                val label = wallet.badge ?: wallet.token.coin.name
                label to adapter.statusInfo
            }
    }

    private fun syncHasChanges() {
        val initialRestoreMode = btcBlockchainManager.restoreMode(blockchain.type)
        hasChangesSubject.onNext(restoreMode != initialRestoreMode)
    }
}
