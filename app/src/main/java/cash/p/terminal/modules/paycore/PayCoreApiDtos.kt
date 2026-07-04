package cash.p.terminal.modules.paycore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

internal const val PAYCORE_COMPLETE_BACK_URL = "pcash://paycore/complete"

@Serializable
data class PayCoreRateResponse(
    val ticker: String,
    val network: String,
    @Serializable(with = PayCoreBigDecimalSerializer::class) val sell: BigDecimal,
    @Serializable(with = PayCoreBigDecimalSerializer::class) val buy: BigDecimal,
    @Serializable(with = PayCoreBigDecimalSerializer::class) val withdrawFee: BigDecimal,
    @SerialName("updated_at") val updatedAt: String? = null,
    val limits: PayCoreRateLimits? = null,
)

@Serializable
data class PayCoreRateLimits(
    @SerialName("min_buy_limit_rub")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val minBuyLimitRub: BigDecimal? = null,
    @SerialName("max_buy_limit_rub")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val maxBuyLimitRub: BigDecimal? = null,
    @SerialName("min_buy_limit_usdt")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val minBuyLimitUsdt: BigDecimal? = null,
    @SerialName("max_buy_limit_usdt")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val maxBuyLimitUsdt: BigDecimal? = null,
    @SerialName("min_sell_limit_rub")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val minSellLimitRub: BigDecimal? = null,
    @SerialName("max_sell_limit_rub")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val maxSellLimitRub: BigDecimal? = null,
    @SerialName("min_sell_limit_usdt")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val minSellLimitUsdt: BigDecimal? = null,
    @SerialName("max_sell_limit_usdt")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val maxSellLimitUsdt: BigDecimal? = null,
)

@Serializable
data class PayCoreWalletCreateRequest(
    val phone: String,
    val address: String,
    @SerialName("network_type") val networkType: PayCoreTicker,
    @SerialName("back_url") val backUrl: String
)

@Serializable
data class PayCoreWalletCreateResponse(
    val status: String,
    val url: String? = null
)

object PayCoreWalletCreateStatus {
    const val NO_ACCESS = "NoAccess"
    const val NOT_REGISTERED = "NotRegistered"
    const val PENDING = "Pending"
    const val APPROVED = "Approved"
    const val REJECTED = "Rejected"
    const val SUSPENDED = "Suspended"
}

@Serializable
data class PayCoreWalletChangeRequest(
    val address: String,
    @SerialName("network_type") val networkType: PayCoreTicker
)

@Serializable
data class PayCoreBankResponse(
    val id: String,
    val name: String
)

@Serializable
data class PayCorePayoutCalculationRequest(
    @Serializable(with = PayCoreBigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("amount_type") val amountType: String,
    @SerialName("bank_id") val bankId: String,
    val ticker: PayCoreTicker
)

@Serializable
data class PayCorePayoutCalculationResponse(
    @SerialName("amount_crypto")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val amountCrypto: BigDecimal,
    @SerialName("full_amount_rub")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val fullAmountRub: BigDecimal,
    val ticker: String,
    val uuid: String,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
data class PayCorePayoutCreateRequest(
    val uuid: String
)

@Serializable
data class PayCorePayoutCreateResponse(
    val address: String,
    val network: String,
    val uuid: String,
    @SerialName("expires_at") val expiresAt: String
)

object PayCoreAmountType {
    const val CRYPTO = "Crypto"
    const val RUB = "Rub"
}

@Serializable
data class PayCorePaymentCalculationRequest(
    @Serializable(with = PayCoreBigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("amount_type") val amountType: String,
    val ticker: PayCoreTicker
)

@Serializable
data class PayCorePaymentCalculationResponse(
    @SerialName("amount_crypto")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val amountCrypto: BigDecimal,
    @SerialName("full_amount_rub")
    @Serializable(with = PayCoreBigDecimalSerializer::class)
    val fullAmountRub: BigDecimal,
    val ticker: String,
    val uuid: String,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
data class PayCorePaymentCreateRequest(
    val uuid: String
)

@Serializable
data class PayCorePaymentCreateResponse(
    @SerialName("payment_url") val paymentUrl: String,
    val uuid: String,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
data class PayCoreTransactionStatusResponse(
    @SerialName("transaction_status") val transactionStatus: String? = null
)

private object PayCoreBigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("PayCoreBigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonPrimitive(value))
        } else {
            encoder.encodeString(value.toPlainString())
        }
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        if (decoder is JsonDecoder) {
            val content = decoder.decodeJsonElement().jsonPrimitive.contentOrNull
            return requireNotNull(content?.toBigDecimalOrNull()) {
                "Invalid PayCore decimal value: $content"
            }
        }
        return decoder.decodeString().toBigDecimal()
    }
}
