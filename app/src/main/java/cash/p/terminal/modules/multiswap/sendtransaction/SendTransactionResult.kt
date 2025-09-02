package cash.p.terminal.modules.multiswap.sendtransaction

import cash.p.terminal.modules.send.SendResult
import io.horizontalsystems.ethereumkit.models.FullTransaction
import org.stellar.sdk.responses.TransactionResponse

sealed class SendTransactionResult {
    data class Common(val result: SendResult) : SendTransactionResult()
    data class Evm(val fullTransaction: FullTransaction) : SendTransactionResult()
    data class  Stellar(val transactionResponse: TransactionResponse) : SendTransactionResult()
}
