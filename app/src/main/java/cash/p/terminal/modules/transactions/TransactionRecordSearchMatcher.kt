package cash.p.terminal.modules.transactions

import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.monero.MoneroTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import java.util.Locale

class TransactionRecordSearchMatcher {

    fun matches(record: TransactionRecord, query: String): Boolean {
        val normalizedQuery = query.normalizedSearchValue()
        if (normalizedQuery.isEmpty()) return true

        return searchableFields(record).any {
            it.normalizedSearchValue().contains(normalizedQuery)
        }
    }

    private fun searchableFields(record: TransactionRecord): List<String> = buildList {
        addCommonFields(record)
        addTypeSpecificFields(record)
    }.filterNotNull()

    private fun MutableList<String?>.addCommonFields(record: TransactionRecord) {
        add(record.transactionHash)
        add(record.from)
        record.to?.let(::addAll)
        add(record.memo)
        add(record.token.coin.uid)
        add(record.token.coin.code)
        add(record.token.coin.name)
        addValue(record.mainValue)
    }

    private fun MutableList<String?>.addTypeSpecificFields(record: TransactionRecord) {
        when (record) {
            is BitcoinTransactionRecord -> addBitcoinFields(record)
            is EvmTransactionRecord -> addEvmFields(record)
            is TronTransactionRecord -> addTronFields(record)
            is SolanaTransactionRecord -> addSolanaFields(record)
            is TonTransactionRecord -> addTonFields(record)
            is StellarTransactionRecord -> addStellarFields(record)
            is MoneroTransactionRecord -> add(record.subaddressLabel)
        }
    }

    private fun MutableList<String?>.addBitcoinFields(record: BitcoinTransactionRecord) {
        add(record.canonicalTransactionHash)
        record.changeAddresses?.let(::addAll)
    }

    private fun MutableList<String?>.addEvmFields(record: EvmTransactionRecord) {
        add(record.spender)
        add(record.contractAddress)
        add(record.method)
        add(record.exchangeAddress)
        add(record.recipient)
        addEvents(record.incomingEvents)
        addEvents(record.outgoingEvents)
        addValue(record.value)
        addValue(record.valueIn)
        addValue(record.valueOut)
    }

    private fun MutableList<String?>.addTronFields(record: TronTransactionRecord) {
        add(record.spender)
        add(record.contractAddress)
        add(record.method)
        addEvents(record.incomingEvents)
        addEvents(record.outgoingEvents)
        addValue(record.value)
    }

    private fun MutableList<String?>.addSolanaFields(record: SolanaTransactionRecord) {
        addSolanaTransfers(record.incomingSolanaTransfers)
        addSolanaTransfers(record.outgoingSolanaTransfers)
    }

    private fun MutableList<String?>.addTonFields(record: TonTransactionRecord) {
        record.actions.forEach { action ->
            when (val type = action.type) {
                is TonTransactionRecord.Action.Type.Send -> addTonSendFields(type)
                is TonTransactionRecord.Action.Type.Receive -> addTonReceiveFields(type)
                is TonTransactionRecord.Action.Type.Burn -> addValue(type.value)
                is TonTransactionRecord.Action.Type.Mint -> {
                    add(type.to)
                    addValue(type.value)
                }

                is TonTransactionRecord.Action.Type.Swap -> {
                    add(type.routerName)
                    add(type.routerAddress)
                    addValue(type.valueIn)
                    addValue(type.valueOut)
                }

                is TonTransactionRecord.Action.Type.ContractDeploy -> addAll(type.interfaces)
                is TonTransactionRecord.Action.Type.ContractCall -> {
                    add(type.address)
                    add(type.operation)
                    addValue(type.value)
                }

                is TonTransactionRecord.Action.Type.Unsupported -> add(type.type)
            }
        }
    }

    private fun MutableList<String?>.addTonSendFields(type: TonTransactionRecord.Action.Type.Send) {
        add(type.to)
        add(type.comment)
        addValue(type.value)
    }

    private fun MutableList<String?>.addTonReceiveFields(type: TonTransactionRecord.Action.Type.Receive) {
        add(type.from)
        add(type.to)
        add(type.comment)
        addValue(type.value)
    }

    private fun MutableList<String?>.addStellarFields(record: StellarTransactionRecord) {
        add(record.operation.memo)
        when (val type = record.type) {
            is StellarTransactionRecord.Type.Send -> {
                add(type.to)
                add(type.comment)
                addValue(type.value)
            }

            is StellarTransactionRecord.Type.Receive -> {
                add(type.from)
                add(type.to)
                add(type.comment)
                addValue(type.value)
            }

            is StellarTransactionRecord.Type.ChangeTrust -> {
                add(type.trustee)
                addValue(type.value)
            }

            is StellarTransactionRecord.Type.Unsupported -> add(type.type)
        }
    }

    private fun MutableList<String?>.addValue(value: TransactionValue?) {
        value ?: return
        add(value.coinUid)
        add(value.coinCode)
        add(value.fullName)
        add(value.badge)
        value.nftUid?.let { nftUid ->
            add(nftUid.tokenId)
            add(nftUid.contractAddress)
        }
    }

    private fun MutableList<String?>.addEvents(events: List<TransferEvent>?) {
        events?.forEach { event ->
            add(event.address)
            add(event.addressForIncomingAddress)
            addValue(event.value)
        }
    }

    private fun MutableList<String?>.addSolanaTransfers(
        transfers: List<SolanaTransactionRecord.SolanaTransfer>?
    ) {
        transfers?.forEach { transfer ->
            add(transfer.address)
            add(transfer.addressForIncomingAddress)
            addValue(transfer.value)
        }
    }

    private fun String.normalizedSearchValue() = trim().lowercase(Locale.ROOT)
}
