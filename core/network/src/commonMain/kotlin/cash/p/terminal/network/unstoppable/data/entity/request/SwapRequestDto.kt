package cash.p.terminal.network.unstoppable.data.entity.request

import kotlinx.serialization.Serializable

// POST /v2/swap — commits the order with a single sub-provider and returns the executable route.
@Serializable
internal data class SwapRequestDto(
    val sellAsset: String,
    val buyAsset: String,
    val sellAmount: String,
    // The API validates slippage as a JSON number (a string is rejected with HTTP 400).
    val slippage: Double,
    val provider: String,
    val destinationAddress: String,
    val refundAddress: String? = null,
    val sourceAddress: String? = null,
    val chainId: String? = null,
)
