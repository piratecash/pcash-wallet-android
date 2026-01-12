package cash.p.terminal.modules.transactions

import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.monero.MoneroTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord

private val incomingTypes = setOf(
    TransactionRecordType.BITCOIN_INCOMING,
    TransactionRecordType.EVM_INCOMING,
    TransactionRecordType.SOLANA_INCOMING,
    TransactionRecordType.TRON_INCOMING,
    TransactionRecordType.STELLAR_INCOMING,
    TransactionRecordType.MONERO_INCOMING,
)

fun TransactionRecord.isIncomingForAmlCheck(): Boolean {
    if (transactionRecordType in incomingTypes) return true

    return when (this) {
        is EvmTransactionRecord -> !incomingEvents.isNullOrEmpty()
        is TronTransactionRecord -> !incomingEvents.isNullOrEmpty()
        is SolanaTransactionRecord -> !incomingSolanaTransfers.isNullOrEmpty()
        is TonTransactionRecord -> actions.any { it.type is TonTransactionRecord.Action.Type.Receive }
        else -> false
    }
}

fun Map<String, List<TransactionViewItem>>.withUpdatedAmlStatus(
    uid: String,
    status: AmlStatus?
): Map<String, List<TransactionViewItem>> = mapValues { (_, items) ->
    items.map { item ->
        if (item.uid == uid) item.copy(amlStatus = status) else item
    }
}

fun Map<String, List<TransactionViewItem>>.withClearedAmlStatus(): Map<String, List<TransactionViewItem>> =
    mapValues { (_, items) ->
        items.map { it.copy(amlStatus = null) }
    }

fun TransactionRecord.getSenderAddresses(): List<String> {
    return when (this) {
        is EvmTransactionRecord -> {
            incomingEvents?.mapNotNull { it.address }?.ifEmpty { null }
                ?: listOfNotNull(from)
        }
        is TronTransactionRecord -> {
            incomingEvents?.mapNotNull { it.address }?.ifEmpty { null }
                ?: listOfNotNull(from)
        }
        is SolanaTransactionRecord -> {
            incomingSolanaTransfers?.mapNotNull { it.address }?.ifEmpty { null }
                ?: listOfNotNull(from)
        }
        is TonTransactionRecord -> {
            actions.mapNotNull { action ->
                (action.type as? TonTransactionRecord.Action.Type.Receive)?.from
            }.ifEmpty { listOfNotNull(from) }
        }
        is BitcoinTransactionRecord -> listOfNotNull(from)
        is StellarTransactionRecord -> {
            (type as? StellarTransactionRecord.Type.Receive)?.from?.let { listOf(it) }
                ?: listOfNotNull(from)
        }
        is MoneroTransactionRecord -> listOfNotNull(from)
        else -> listOfNotNull(from)
    }
}
