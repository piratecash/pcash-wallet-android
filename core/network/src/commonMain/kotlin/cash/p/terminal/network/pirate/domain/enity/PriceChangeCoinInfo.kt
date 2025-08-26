package cash.p.terminal.network.pirate.domain.enity

import java.math.BigDecimal

data class PriceChangeCoinInfo(
    val uid: String,
    val price: BigDecimal,
    val priceChange1h: BigDecimal,
    val priceChange24h: BigDecimal,
    val priceChange7d: BigDecimal,
    val priceChange30d: BigDecimal,
    val priceChange1y: BigDecimal,
    val priceChangeMax: BigDecimal,
    val lastUpdated: Long,
)
