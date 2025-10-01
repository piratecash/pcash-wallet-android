package cash.p.terminal.network.stonfi.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class CustomPayloadDto(
    val payload: String
)
