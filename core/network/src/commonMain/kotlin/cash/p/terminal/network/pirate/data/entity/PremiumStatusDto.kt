package cash.p.terminal.network.pirate.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PremiumStatusDto(
    @SerialName("daysLeft")
    val daysLeft: Int? = null
)
