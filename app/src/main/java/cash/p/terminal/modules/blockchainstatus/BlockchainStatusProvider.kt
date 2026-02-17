package cash.p.terminal.modules.blockchainstatus

import cash.p.terminal.BuildConfig
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.core.adapters.BitcoinBaseAdapter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType

data class StatusSection(
    val title: String,
    val items: List<StatusItem>
)

sealed class StatusItem {
    data class KeyValue(val key: String, val value: String) : StatusItem()
    data class Nested(val title: String, val items: List<KeyValue>) : StatusItem()
}

data class BlockchainStatus(
    val sections: List<StatusSection>,
    val sharedSection: StatusSection?
)

interface BlockchainStatusProvider {
    val blockchainName: String
    val kitVersion: String
    val logFilterTag: String

    fun getStatus(): BlockchainStatus
}

class BtcBlockchainStatusProvider(
    private val blockchain: Blockchain,
    private val btcBlockchainManager: BtcBlockchainManager,
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager
) : BlockchainStatusProvider {

    override val blockchainName: String = blockchain.name

    override val kitVersion: String = BuildConfig.BITCOIN_KIT_VERSION

    override val logFilterTag: String = blockchain.type.logTag

    override fun getStatus(): BlockchainStatus {
        val pairs = getWalletStatusPairs()

        val sections = pairs.map { (label, statusMap) ->
            val items = statusMap
                .filter { (_, value) -> value !is Map<*, *> }
                .map { (key, value) -> StatusItem.KeyValue(key, value.toString()) }
            StatusSection(title = label, items = items)
        }

        val sharedSection = buildSharedSection(pairs)

        return BlockchainStatus(sections = sections, sharedSection = sharedSection)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildSharedSection(
        pairs: List<Pair<String, Map<String, Any>>>
    ): StatusSection? {
        val firstStatusMap = pairs.firstOrNull()?.second ?: return null

        val peerItems = firstStatusMap
            .filter { (_, value) -> value is Map<*, *> }
            .map { (key, value) ->
                val peerMap = value as Map<String, Any>
                StatusItem.Nested(
                    title = key,
                    items = peerMap.map { (k, v) -> StatusItem.KeyValue(k, v.toString()) }
                )
            }

        if (peerItems.isEmpty()) return null

        return StatusSection(
            title = "${blockchain.name} Peers",
            items = peerItems
        )
    }

    private fun getWalletStatusPairs(): List<Pair<String, Map<String, Any>>> {
        return walletManager.activeWallets
            .filter { it.token.blockchainType == blockchain.type }
            .mapNotNull { wallet ->
                val adapter = adapterManager.getAdapterForWallet<BitcoinBaseAdapter>(wallet)
                    ?: return@mapNotNull null
                val label = wallet.badge ?: wallet.token.coin.name
                val restoreMode = btcBlockchainManager.restoreMode(wallet.token.blockchainType)
                val statusInfo = mutableMapOf<String, Any>("Sync Mode" to restoreMode.name)
                statusInfo.putAll(adapter.statusInfo)
                label to statusInfo
            }
    }
}

val BlockchainType.logTag: String
    get() = when (this) {
        BlockchainType.Bitcoin -> "BTC"
        BlockchainType.Litecoin -> "LTC"
        BlockchainType.BitcoinCash -> "BCH"
        BlockchainType.Dash -> "DASH"
        BlockchainType.ECash -> "XEC"
        BlockchainType.Dogecoin -> "DOGE"
        BlockchainType.Cosanta -> "COSA"
        BlockchainType.PirateCash -> "PCASH"
        else -> uid
    }
