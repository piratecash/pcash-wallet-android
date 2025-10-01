package cash.p.terminal.network.stonfi.domain.entity

data class Asset(
    val contractAddress: String,
    val kind: String,
    val balance: String? = null,
    val dexPriceUsd: String? = null,
    val extensions: List<String>? = null,
    val meta: AssetMeta? = null,
    val pairPriority: Int? = null,
    val popularityIndex: Double? = null,
    val tags: List<String>? = null,
    val walletAddress: String? = null
)

data class AssetMeta(
    val customPayloadApiUri: String? = null,
    val decimals: Int? = null,
    val displayName: String? = null,
    val imageUrl: String? = null,
    val symbol: String? = null
)
