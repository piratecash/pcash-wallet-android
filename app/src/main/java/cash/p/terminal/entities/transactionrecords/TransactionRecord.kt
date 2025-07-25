package cash.p.terminal.entities.transactionrecords

import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.BlockchainType

abstract class TransactionRecord(
    val uid: String,
    val transactionHash: String,
    val transactionIndex: Int,
    val blockHeight: Int?,
    val confirmationsThreshold: Int?,
    val timestamp: Long,
    val failed: Boolean = false,
    val spam: Boolean = false,
    val source: TransactionSource,
    val transactionRecordType: TransactionRecordType,
    open val token: Token,
    open val to: String? = null,
    open val from: String? = null,
    open val sentToSelf: Boolean = false,
    open val memo: String? = null,
) : Comparable<TransactionRecord> {

    open val mainValue: TransactionValue? = null

    val blockchainType: BlockchainType
        get() = source.blockchain.type

    open fun changedBy(oldBlockInfo: LastBlockInfo?, newBlockInfo: LastBlockInfo?): Boolean =
        status(oldBlockInfo?.height) != status(newBlockInfo?.height)

    override fun compareTo(other: TransactionRecord): Int {
        return when {
            timestamp != other.timestamp -> timestamp.compareTo(other.timestamp)
            transactionIndex != other.transactionIndex -> transactionIndex.compareTo(other.transactionIndex)
            else -> uid.compareTo(uid)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is TransactionRecord) {
            return uid == other.uid
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return uid.hashCode()
    }

    open fun status(lastBlockHeight: Int?): TransactionStatus {
        if (failed) {
            return TransactionStatus.Failed
        } else if (blockHeight != null) {
            val threshold = confirmationsThreshold ?: 1
            val confirmations = (lastBlockHeight ?: 0) - blockHeight.toInt() + 1

            return if (confirmations >= threshold) {
                TransactionStatus.Completed
            } else {
                TransactionStatus.Processing(confirmations.toFloat() / threshold.toFloat())
            }
        }

        return TransactionStatus.Pending
    }
}

val TransactionRecord.nftUids: Set<NftUid>
    get() = if (this is EvmTransactionRecord) {
        when (transactionRecordType) {
            TransactionRecordType.EVM_CONTRACT_CALL,
            TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL -> {
                ((incomingEvents!! + outgoingEvents!!).mapNotNull { it.value.nftUid }).toSet()
            }

            TransactionRecordType.EVM_OUTGOING -> {
                value!!.nftUid?.let { setOf(it) } ?: emptySet()
            }

            else -> emptySet()
        }
    } else {
        emptySet()
    }

val List<TransactionRecord>.nftUids: Set<NftUid>
    get() {
        val nftUids = mutableSetOf<NftUid>()
        forEach { nftUids.addAll(it.nftUids) }
        return nftUids
    }

fun TransactionRecord.getShortOutgoingTransactionRecord(): ShortOutgoingTransactionRecord? =
    when (this) {

        is BitcoinTransactionRecord ->
            if (transactionRecordType == TransactionRecordType.BITCOIN_OUTGOING) {
                ShortOutgoingTransactionRecord(
                    amountOut = mainValue.decimalValue?.abs(),
                    token = token,
                    timestamp = timestamp * 1000
                )
            } else {
                null
            }

        is EvmTransactionRecord -> {
            if (transactionRecordType == TransactionRecordType.EVM_OUTGOING) {
                ShortOutgoingTransactionRecord(
                    amountOut = mainValue?.decimalValue?.abs(),
                    token = (mainValue as? TransactionValue.CoinValue)?.token,
                    timestamp = timestamp * 1000
                )
            } else {
                null
            }
        }

        is TronTransactionRecord ->
            if (transactionRecordType == TransactionRecordType.TRON_OUTGOING) {
                ShortOutgoingTransactionRecord(
                    amountOut = mainValue?.decimalValue?.abs(),
                    token = token,
                    timestamp = timestamp * 1000
                )
            } else {
                null
            }

        is TonTransactionRecord ->
            if (actions.singleOrNull()?.type is TonTransactionRecord.Action.Type.Send) {
                ShortOutgoingTransactionRecord(
                    amountOut = this.mainValue?.decimalValue?.abs(),
                    token = token,
                    timestamp = timestamp * 1000
                )
            } else {
                null
            }

        is SolanaTransactionRecord -> {
            if (transactionRecordType == TransactionRecordType.SOLANA_INCOMING) {
                ShortOutgoingTransactionRecord(
                    amountOut = mainValue?.decimalValue?.abs(),
                    token = token,
                    timestamp = timestamp * 1000
                )

            } else {
                null
            }
        }

        else -> null
    }
