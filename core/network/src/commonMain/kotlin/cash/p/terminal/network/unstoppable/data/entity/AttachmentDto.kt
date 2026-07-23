package cash.p.terminal.network.unstoppable.data.entity

import kotlinx.serialization.Serializable

// transfer.attachment — an order identifier the provider uses to credit the deposit.
@Serializable
internal data class AttachmentDto(
    val type: String, // destination_tag | text
    val value: String,
)
