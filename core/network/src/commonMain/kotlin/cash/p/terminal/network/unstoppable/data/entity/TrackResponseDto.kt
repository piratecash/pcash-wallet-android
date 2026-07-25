package cash.p.terminal.network.unstoppable.data.entity

import cash.p.terminal.network.data.serializers.FlexibleBigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

// /v2/track response.
@Serializable
internal data class TrackResponseDto(
    val status: String, // not_started, pending, swapping, completed, refunded, unknown, failed, action_required
    val type: String? = null,
    val hash: String? = null,
    val chainId: String? = null,
    val fromAsset: String? = null,
    @Serializable(with = FlexibleBigDecimalSerializer::class)
    val fromAmount: BigDecimal? = null,
    val fromAddress: String? = null,
    val toAsset: String? = null,
    @Serializable(with = FlexibleBigDecimalSerializer::class)
    val toAmount: BigDecimal? = null,
    val toAddress: String? = null,
    val legs: List<LegDto>? = null,
    val meta: MetaDto? = null,
)

@Serializable
internal data class LegDto(
    val type: String, // "swap" | "native_send"
    val status: String,
    val hash: String? = null,
    val chainId: String? = null,
    val fromAsset: String? = null,
    @Serializable(with = FlexibleBigDecimalSerializer::class)
    val fromAmount: BigDecimal? = null,
    val fromAddress: String? = null,
    val toAsset: String? = null,
    @Serializable(with = FlexibleBigDecimalSerializer::class)
    val toAmount: BigDecimal? = null,
    val toAddress: String? = null,
)

@Serializable
internal data class MetaDto(
    val provider: String? = null,
    val pauseReason: String? = null, // "overdue_with_funds" | "aml" | "frozen"
)
