package cash.p.terminal.modules.multiswap.sendtransaction

import cash.p.terminal.modules.send.SendResult
import io.horizontalsystems.ethereumkit.models.FullTransaction
import org.stellar.sdk.responses.TransactionResponse

sealed class SendTransactionResult {
    data class Evm(val fullTransaction: FullTransaction) : SendTransactionResult()
    data class Btc(
        val uid: String,
        val canonicalHashReversedHex: String,
        val isQueued: Boolean = false
    ) : SendTransactionResult()
    data class Ton(val result: SendResult) : SendTransactionResult()
    data class Tron(val result: SendResult) : SendTransactionResult()
    data class Stellar(val transactionResponse: TransactionResponse) : SendTransactionResult()
    data class Solana(val result: SendResult) : SendTransactionResult()
    data class ZCash(val result: SendResult) : SendTransactionResult()
    data class Monero(val result: SendResult) : SendTransactionResult()

    fun getRecordUid(): String? {
        return when (this) {
            is Evm -> fullTransaction.transaction.hashString
            is Btc -> uid
            is Tron -> when (result) {
                is SendResult.Sent -> result.recordUid
                is SendResult.SentButQueued -> result.recordUid
                is SendResult.Failed,
                SendResult.Sending -> null
            }
            is Ton -> when (result) {
                is SendResult.Sent -> result.recordUid
                is SendResult.SentButQueued -> result.recordUid
                is SendResult.Failed,
                SendResult.Sending -> null
            }
            is ZCash -> when (result) {
                is SendResult.Sent -> result.recordUid
                is SendResult.SentButQueued -> result.recordUid
                is SendResult.Failed,
                SendResult.Sending -> null
            }

            is Stellar -> transactionResponse.hash
            is Solana -> when (result) {
                is SendResult.Sent -> result.recordUid
                is SendResult.SentButQueued -> result.recordUid
                is SendResult.Failed,
                SendResult.Sending -> null
            }
            is Monero -> when (result) {
                is SendResult.Sent -> result.recordUid
                is SendResult.SentButQueued -> result.recordUid
                is SendResult.Failed,
                SendResult.Sending -> null
            }
        }
    }

    // For Stellar the record uid already IS the canonical hash; UTXO carries its own field.
    // The EVM record uid is the 0x-prefixed hash, but Thornode/Mayanode tx ids are the bare hex hash.
    fun getCanonicalTxHash(): String? = when (this) {
        is Btc -> canonicalHashReversedHex
        is Evm -> fullTransaction.transaction.hashString.removePrefix("0x")
        else -> getRecordUid()
    }
}
