package cash.p.terminal.network.quickex.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal class QuickexRatesDto(
    val depositRules: RulesDto?,
    val minConfirmationsToWithdraw: Int,
    val minConfirmationsToTrade: Int,
    val instrumentFrom: InstrumentDto,
    val instrumentTo: InstrumentDto,
    val amountToGet: String,
    val price: String
)

@Serializable
internal class RulesDto(
    val minAmount: String,
    val maxAmount: String
)

@Serializable
internal class InstrumentDto(
    val currencyTitle: String,
    val networkTitle: String,
    val precisionDecimals: Int
)
