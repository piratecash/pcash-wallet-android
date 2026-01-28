package cash.p.terminal.wallet.models

import androidx.room.Entity
import java.math.BigDecimal

@Entity(primaryKeys = ["coinUid", "currencyCode"])
data class CoinPrice(
    val coinUid: String,
    val currencyCode: String,
    val value: BigDecimal,
    val diff1h: BigDecimal?,
    val diff24h: BigDecimal?,
    val diff7d: BigDecimal?,
    val diff30d: BigDecimal?,
    val diff1y: BigDecimal?,
    val diffAll: BigDecimal?,
    val timestamp: Long
) {
    val expired: Boolean
        get() = System.currentTimeMillis() / 1000 - timestamp > EXPIRATION_SECONDS

    override fun toString(): String {
        return "CoinPrice [coinUid: $coinUid; currencyCode: $currencyCode; value: $value; diff24h: $diff24h; timestamp: $timestamp]"
    }

    companion object {
        const val EXPIRATION_SECONDS: Long = 300
    }
}