package cash.p.terminal.network.unstoppable.data.entity.request

import kotlinx.serialization.Serializable

// POST /v2/track — our recorded swaps, tracked by the route's uuid; inboundTxHash is the
// canonical deposit tx hash, required so the server can resolve DEX (Barter/Circle) swaps.
@Serializable
internal data class TrackRequestDto(
    val uuid: String? = null,
    val inboundTxHash: String? = null,
)
