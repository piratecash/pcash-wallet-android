package cash.p.terminal.premium.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

internal data class TokenBalance(
    val balance: BigDecimal
) {
    companion object {
        const val PIRATE_DECIMALS = 8

        fun fromHexBalance(
            hexBalance: String,
            decimals: Int,
        ): TokenBalance {
            val rawBalance = hexToBigInteger(hexBalance)
            val divisor = BigDecimal.TEN.pow(decimals)
            val humanReadableBalance = rawBalance.toBigDecimal()
                .divide(divisor, decimals, RoundingMode.DOWN)

            return TokenBalance(
                balance = humanReadableBalance,
            )
        }
        
        private fun hexToBigInteger(hex: String): BigInteger {
            return BigInteger(hex.removePrefix("0x"), 16)
        }
    }
} 