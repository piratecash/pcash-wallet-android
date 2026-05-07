package cash.p.terminal.trezor.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TrezorResponse(
    val success: Boolean,
    val payload: JsonElement? = null,
    val error: String? = null
)
