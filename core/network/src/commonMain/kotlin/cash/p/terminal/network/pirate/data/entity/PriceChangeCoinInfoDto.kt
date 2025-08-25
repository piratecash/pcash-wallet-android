package cash.p.terminal.network.pirate.data.entity

import cash.p.terminal.network.data.serializers.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
internal data class PriceChangeCoinInfoDto(
    @SerialName("uid")
    val uid: String?,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("price")
    val price: BigDecimal?,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("price_change_1h")
    val priceChange1h: BigDecimal?,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("price_change_24h")
    val priceChange24h: BigDecimal?,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("price_change_7d")
    val priceChange7d: BigDecimal?,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("price_change_30d")
    val priceChange30d: BigDecimal?,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("price_change_1y")
    val priceChange1y: BigDecimal?,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("price_change_max")
    val priceChangeMax: BigDecimal?,
    @SerialName("last_updated")
    val lastUpdated: Long?
)
