package cash.p.terminal.core.factories

import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord

class TransferEventFactory {

    fun transferEvents(transactionRecord: TransactionRecord): List<TransferEvent> {
        return when (transactionRecord.transactionRecordType) {
            TransactionRecordType.EVM_INCOMING -> {
                transactionRecord.mainValue?.let {
                    listOf(TransferEvent(transactionRecord.from, null, it))
                }
            }

            TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL -> {
                (transactionRecord as? EvmTransactionRecord)?.let {
                    buildList {
                        it.incomingEvents?.let { incomingEvents -> addAll(incomingEvents) }
                        it.outgoingEvents?.let { outgoingEvents -> addAll(outgoingEvents) }
                    }
                }
            }

            TransactionRecordType.TRON_EXTERNAL_CONTRACT_CALL -> {
                (transactionRecord as? EvmTransactionRecord)?.let {
                    buildList {
                        it.incomingEvents?.let { incomingEvents -> addAll(incomingEvents) }
                        it.outgoingEvents?.let { outgoingEvents -> addAll(outgoingEvents) }
                    }
                }
            }

            TransactionRecordType.TRON_INCOMING -> {
                transactionRecord.mainValue?.let {
                    listOf(TransferEvent(transactionRecord.from, null, it))
                }
            }

            TransactionRecordType.STELLAR_INCOMING -> {
                (transactionRecord as? StellarTransactionRecord)?.let {
                    StellarTransactionRecord.eventsForPhishingCheck(transactionRecord.type)
                }
            }

            else -> null
        } ?: listOf()
    }
}