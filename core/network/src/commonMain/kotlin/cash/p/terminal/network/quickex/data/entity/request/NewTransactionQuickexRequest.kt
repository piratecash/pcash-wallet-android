package cash.p.terminal.network.quickex.data.entity.request

import kotlinx.serialization.Serializable

@Serializable
data class NewTransactionQuickexRequest(
    val instrumentFrom: InstrumentRequest,
    val instrumentTo: InstrumentRequest,
    val destinationAddress: String,
    val refundAddress: String,
    val claimedDepositAmount: String,
    val referrerId: String? = null // will be set later
) {
    internal fun withReferrerId(referrerId: String): NewTransactionQuickexRequest {
        return copy(referrerId = referrerId)
    }
}

@Serializable
data class InstrumentRequest(
    val currencyTitle: String,
    val networkTitle: String
)