package cash.p.terminal.network.unstoppable.data.entity.request

import kotlinx.serialization.Serializable

// POST /v2/rate — read-only, prices the swap across the requested sub-providers.
@Serializable
internal data class RateRequestDto(
    val sellAsset: String,
    val buyAsset: String,
    val sellAmount: String,
    // The API validates slippage as a JSON number (a string is rejected with HTTP 400).
    val slippage: Double,
    val providers: Set<String>,
    val chainId: String? = null,
)
