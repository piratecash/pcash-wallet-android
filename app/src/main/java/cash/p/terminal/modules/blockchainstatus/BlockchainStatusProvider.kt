package cash.p.terminal.modules.blockchainstatus

import cash.p.terminal.BuildConfig
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.core.adapters.BitcoinBaseAdapter
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
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

fun StringBuilder.appendStatusSection(section: StatusSection) {
    appendLine(section.title)
    section.items.forEach { item ->
        when (item) {
            is StatusItem.KeyValue -> appendLine("  ${item.key}: ${item.value}")
            is StatusItem.Nested -> {
                appendLine("  ${item.title}:")
                item.items.forEach { kv ->
                    appendLine("    ${kv.key}: ${kv.value}")
                }
            }
        }
    }
    appendLine()
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
        val firstStatusMap = pairs.firstOrNull()?.second

        val sections = if (firstStatusMap != null) {
            val items = firstStatusMap
                .filter { (key, value) -> value !is Map<*, *> && key != "Derivation" }
                .map { (key, value) -> StatusItem.KeyValue(key, value.toString()) }
            val badges = walletManager.activeWallets
                .filter { it.token.blockchainType == blockchain.type }
                .mapNotNull { it.badge }
            val title = if (badges.isNotEmpty()) {
                "${blockchain.name} (${badges.joinToString(", ")})"
            } else {
                blockchain.name
            }
            listOf(StatusSection(title = title, items = items))
        } else {
            emptyList()
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

class EvmBlockchainStatusProvider(
    private val blockchain: Blockchain,
    private val evmBlockchainManager: EvmBlockchainManager
) : BlockchainStatusProvider {

    override val blockchainName: String = blockchain.name
    override val kitVersion: String = BuildConfig.ETHEREUM_KIT_VERSION
    override val logFilterTag: String = blockchain.type.uid

    override fun getStatus(): BlockchainStatus {
        val statusInfo = evmBlockchainManager.getEvmKitManager(blockchain.type).statusInfo
        return statusFromMap(blockchain.name, statusInfo)
    }
}

class SolanaBlockchainStatusProvider(
    private val solanaKitManager: SolanaKitManager
) : BlockchainStatusProvider {

    override val blockchainName: String = "Solana"
    override val kitVersion: String = BuildConfig.SOLANA_KIT_VERSION
    override val logFilterTag: String = BlockchainType.Solana.uid

    override fun getStatus() = statusFromMap(blockchainName, solanaKitManager.statusInfo)
}

class TronBlockchainStatusProvider(
    private val tronKitManager: TronKitManager
) : BlockchainStatusProvider {

    override val blockchainName: String = "Tron"
    override val kitVersion: String = BuildConfig.TRON_KIT_VERSION
    override val logFilterTag: String = BlockchainType.Tron.uid

    override fun getStatus() = statusFromMap(blockchainName, tronKitManager.statusInfo)
}

class TonBlockchainStatusProvider(
    private val tonKitManager: TonKitManager
) : BlockchainStatusProvider {

    override val blockchainName: String = "TON"
    override val kitVersion: String = BuildConfig.TON_KIT_VERSION
    override val logFilterTag: String = BlockchainType.Ton.uid

    override fun getStatus() = statusFromMap(blockchainName, tonKitManager.statusInfo)
}

class MoneroBlockchainStatusProvider(
    private val moneroKitManager: MoneroKitManager
) : BlockchainStatusProvider {

    override val blockchainName: String = "Monero"
    override val kitVersion: String = BuildConfig.MONERO_KIT_VERSION
    override val logFilterTag: String = BlockchainType.Monero.uid

    override fun getStatus() = statusFromMap(blockchainName, moneroKitManager.moneroKitWrapper?.statusInfo())
}

class StellarBlockchainStatusProvider(
    private val stellarKitManager: StellarKitManager
) : BlockchainStatusProvider {

    override val blockchainName: String = "Stellar"
    override val kitVersion: String = BuildConfig.STELLAR_KIT_VERSION
    override val logFilterTag: String = BlockchainType.Stellar.uid

    override fun getStatus() = statusFromMap(blockchainName, stellarKitManager.statusInfo)
}

class ZcashBlockchainStatusProvider(
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager
) : BlockchainStatusProvider {

    override val blockchainName: String = "Zcash"
    override val kitVersion: String = BuildConfig.ZCASH_SDK_VERSION
    override val logFilterTag: String = BlockchainType.Zcash.uid

    override fun getStatus(): BlockchainStatus {
        val adapter = walletManager.activeWallets
            .firstOrNull { it.token.blockchainType == BlockchainType.Zcash }
            ?.let { adapterManager.getAdapterForWallet<ZcashAdapter>(it) }

        return statusFromMap(blockchainName, adapter?.statusInfo)
    }
}

private fun statusFromMap(title: String, statusInfo: Map<String, Any>?): BlockchainStatus {
    val items = statusInfo
        ?.filter { (_, value) -> value !is Map<*, *> }
        ?.map { (key, value) -> StatusItem.KeyValue(key, value.toString()) }
        .orEmpty()

    val sections = if (items.isNotEmpty()) {
        listOf(StatusSection(title = title, items = items))
    } else {
        emptyList()
    }

    return BlockchainStatus(sections = sections, sharedSection = null)
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
