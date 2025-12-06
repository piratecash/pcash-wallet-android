package cash.p.terminal.network.quickex.domain.entity

import java.math.BigDecimal

data class QuickexRates(
    val depositRules: Rules?,
    val minConfirmationsToWithdraw: Int,
    val minConfirmationsToTrade: Int,
    val instrumentFrom: Instrument,
    val instrumentTo: Instrument,
    val amountToGet: BigDecimal,
    val price: BigDecimal
)

data class Rules(
    val minAmount: BigDecimal,
    val maxAmount: BigDecimal
)

data class Instrument(
    val currencyTitle: String,
    val networkTitle: String,
    val precisionDecimals: Int
)
