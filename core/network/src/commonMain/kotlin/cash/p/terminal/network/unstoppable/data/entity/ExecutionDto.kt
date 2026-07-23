package cash.p.terminal.network.unstoppable.data.entity

import kotlinx.serialization.Serializable

// /v2 `execution` discriminated union — switch on `method` (transfer | signed_transaction |
// thorchain_deposit). Modeled as one flat class (matches the wire format) with only the
// fields this app's sub-provider set needs; the mapper narrows it further into the domain model.
@Serializable
internal data class ExecutionDto(
    val method: String,
    // Chain the server built this execution for; used by the EVM wrapper to validate chain identity.
    val chain: String? = null,
    // signed_transaction
    val transactions: List<SignableTxDto>? = null,
    val approval: ApprovalDto? = null,
    // transfer
    val depositAddress: String? = null,
    val amount: String? = null,
    val attachment: AttachmentDto? = null,
    val unsignedTx: SignableTxDto? = null,
    // thorchain_deposit (not used by this app's Unstoppable sub-provider set today)
    val inboundAddress: String? = null,
    val memo: String? = null,
    val delivery: DeliveryDto? = null,
)

// thorchain_deposit.delivery — chain-specific memo binding.
@Serializable
internal data class DeliveryDto(
    val kind: String,
    val router: String? = null,
    val approval: ApprovalDto? = null,
    val shieldedMemoAddress: String? = null,
    val unsignedTx: SignableTxDto? = null,
)
