package cash.p.terminal.network.zcash.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ZcashBlocksResponse(
    val data: List<ZcashBlockDto> = emptyList()
)

@Serializable
internal data class ZcashBlockDto(
    val id: Long? = null,
    val height: Long? = null,
    @SerialName("time")
    val timestamp: String? = null
) {
    val blockHeight: Long?
        get() = height ?: id
}
