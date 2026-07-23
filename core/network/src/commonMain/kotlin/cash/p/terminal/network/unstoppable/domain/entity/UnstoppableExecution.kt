package cash.p.terminal.network.unstoppable.domain.entity

/**
 * How to execute a committed route. Narrowed to the two methods this app's sub-provider set
 * actually uses (`transfer` for off-chain deposit providers, `signed_transaction` for EVM
 * Barter/Circle) — `thorchain_deposit` exists in the v2 API but is not emitted for our providers.
 */
data class UnstoppableExecution(
    val method: String,
    val chain: String?,
    val transactions: List<UnstoppableSignableTx>,
    val approval: UnstoppableApproval?,
    val depositAddress: String?,
    val attachment: UnstoppableAttachment?,
    val unsignedTx: UnstoppableSignableTx?,
) {
    val approvalSpender: String?
        get() = approval?.spender

    /** The single signable transaction to send, resolved regardless of method shape. */
    val primarySignable: UnstoppableSignableTx?
        get() = when (method) {
            METHOD_SIGNED_TRANSACTION -> transactions.firstOrNull()
            METHOD_TRANSFER -> unsignedTx
            else -> null
        }

    fun resolvedDepositAddress(): String? = when (method) {
        METHOD_TRANSFER -> depositAddress
        else -> null
    }

    fun resolvedMemo(): String? = when (method) {
        METHOD_TRANSFER -> attachment?.value
        else -> null
    }

    companion object {
        const val METHOD_TRANSFER = "transfer"
        const val METHOD_SIGNED_TRANSACTION = "signed_transaction"
    }
}

data class UnstoppableSignableTx(
    val kind: String,
    val to: String?,
    val from: String?,
    val value: String?,
    val data: String?,
    val gas: String?,
)

data class UnstoppableApproval(
    val token: String?,
    val spender: String,
    val amount: String?,
)

data class UnstoppableAttachment(
    val type: String,
    val value: String,
)
