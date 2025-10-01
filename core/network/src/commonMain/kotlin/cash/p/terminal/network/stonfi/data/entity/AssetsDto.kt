package cash.p.terminal.network.stonfi.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class AssetsDto(
    val asset_list: List<AssetDto>
)

@Serializable
internal data class AssetDto(
    val contract_address: String,
    val kind: String,
    val balance: String? = null,
    val dex_price_usd: String? = null,
    val extensions: List<String>? = null,
    val meta: AssetMetaDto? = null,
    val pair_priority: Int? = null,
    val popularity_index: Double? = null,
    val tags: List<String>? = null,
    val wallet_address: String? = null
)

@Serializable
internal data class AssetMetaDto(
    val custom_payload_api_uri: String? = null,
    val decimals: Int? = null,
    val display_name: String? = null,
    val image_url: String? = null,
    val symbol: String? = null
)

@Serializable
internal data class AssetResponseDto(
    val asset: AssetDto
)
