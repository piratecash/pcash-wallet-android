package cash.p.terminal.network.unstoppable.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class ApprovalDto(
    val token: String? = null,
    val spender: String,
    val amount: String? = null,
)
