package cash.p.terminal.entities.transactionrecords.evm

import cash.p.terminal.entities.TransactionValue

/**
 * @param addressForIncomingAddress used only for incoming transfers
 */
data class TransferEvent(
    val address: String?,
    val addressForIncomingAddress: String?,
    val value: TransactionValue
)
