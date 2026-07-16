package cash.p.terminal.network.unstoppable.data.entity

import kotlinx.serialization.Serializable

// /v2/rate response — a list of routes to compare (economics only, no execution/uuid yet).
@Serializable
internal data class RateResponseDto(
    val routes: List<RouteDto> = emptyList(),
)
