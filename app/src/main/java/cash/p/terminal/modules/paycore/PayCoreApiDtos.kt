package cash.p.terminal.modules.paycore

import cash.p.terminal.core.tryOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

internal const val PAYCORE_COMPLETE_BACK_URL = "pcash://paycore/complete"

@Serializable
data class PayCoreRateResponse(
    @SerialName("currency_from") val currencyFrom: String,
    @SerialName("currency_to") val currencyTo: String,
    @SerialName("amount_from") val amountFrom: String,
    @SerialName("amount_to") val amountTo: String,
    val rate: String,
    @SerialName("min_rub_count") val minRubCount: Long? = null,
    @SerialName("max_rub_count") val maxRubCount: Long? = null,
)

@Serializable
data class PayCoreWalletCreateRequest(
    val phone: String,
    val address: String,
    @SerialName("network_type") val networkType: String,
    @SerialName("verify_sign_key") val verifySignKey: String,
    @SerialName("back_url") val backUrl: String
)

@Serializable
data class PayCoreWalletCreateResponse(
    val status: Int,
    val url: String? = null
)

@Serializable
data class PayCoreWalletChangeRequest(
    val address: String,
    @SerialName("network_type") val networkType: String
)

@Serializable
data class PayCorePayoutAddressRequest(
    @SerialName("network_type") val networkType: String
)

@Serializable
data class PayCorePayoutAddressResponse(
    val address: String,
    @SerialName("network_type") val networkType: String
)

@Serializable
data class PayCorePayoutProcessRequest(
    @SerialName("transaction_hash") val transactionHash: String,
    @SerialName("back_url") val backUrl: String
)

@Serializable
data class PayCorePayoutProcessResponse(
    val status: Int,
    val url: String? = null
)

@Serializable
data class PayCorePaymentCreateRequest(
    val amount: String,
    @SerialName("network_type") val networkType: String,
    @SerialName("back_url") val backUrl: String
)

@Serializable
data class PayCorePaymentCreateResponse(
    val url: String
)

@Serializable
data class PayCoreTransactionStatusResponse(
    @SerialName("crypto_transaction_status") val cryptoTransactionStatus: String? = null,
    @SerialName("fiat_transaction_status") val fiatTransactionStatus: String? = null
)

internal fun PayCorePaymentCreateResponse.transactionIdOrNull(): String? {
    return parsePayCoreRedirectTransactionId(url, "payment")
}

internal fun PayCorePayoutProcessResponse.transactionIdOrNull(): String? {
    return url?.let { parsePayCoreRedirectTransactionId(it, "payout") }
}

/**
 * Per API contract (`status=3` → "транзакция завершилась неудачно"),
 * collapsing this into a transient "no url yet" would leave the swap
 * pending forever instead of transitioning to FAILED.
 */
internal fun PayCorePayoutProcessResponse.isTerminalFailure(): Boolean = status == PAYCORE_PAYOUT_STATUS_FAILED

private const val PAYCORE_PAYOUT_STATUS_FAILED = 3

private fun parsePayCoreRedirectTransactionId(url: String, routeSegment: String): String? {
    val path = tryOrNull { URI(url).path } ?: return null
    val pathSegments = path.split('/').filter(String::isNotBlank)
    val routeIndex = pathSegments.indexOf(routeSegment)
    if (routeIndex < 0) return null
    return pathSegments.getOrNull(routeIndex + 1)?.takeIf(String::isNotBlank)
}
