package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Status repository shared by THORChain and Maya Protocol swaps: both chains expose an
 * identical `/tx/status/{hash}` schema through [ThornodeAPI], so a single implementation
 * parameterized by the chain-specific API instance covers both providers.
 */
class ThorChainSwapStatusRepository(
    private val api: ThornodeAPI,
) : SwapProviderTransactionStatusRepository {

    override suspend fun getTransactionStatus(
        transactionId: String,
        destinationAddress: String,
    ): SwapProviderTransactionStatusResult = withContext(Dispatchers.IO) {
        mapTxStatus(api.txStatus(transactionId), destinationAddress)
    }
}

/**
 * Pure mapping from a Thornode/Mayanode tx-status response to our normalized status result.
 * Kept free of network access so it can be unit-tested directly. Missing/null stage data is
 * treated as "not completed yet" so we never jump to a terminal status prematurely.
 */
fun mapTxStatus(
    txStatus: ThornodeAPI.Response.TxStatus,
    destinationAddress: String,
): SwapProviderTransactionStatusResult {
    val stages = txStatus.stages
    val outboundSigned = stages?.outboundSigned?.completed == true
    val isRefund = txStatus.plannedOutTxs.orEmpty().any { it.refund == true }

    val status = when {
        isRefund && outboundSigned -> TransactionStatusEnum.REFUNDED
        stages?.inboundObserved?.completed != true -> TransactionStatusEnum.WAITING
        stages.inboundFinalised?.completed != true -> TransactionStatusEnum.CONFIRMING
        stages.swapFinalised?.completed != true || stages.swapStatus?.pending == true ->
            TransactionStatusEnum.EXCHANGING

        !outboundSigned -> TransactionStatusEnum.SENDING
        else -> TransactionStatusEnum.FINISHED
    }

    // Prefer the out_tx actually addressed to the recipient; fall back to the first one
    // if the response doesn't let us disambiguate.
    val outTx = txStatus.outTxs.orEmpty().firstOrNull { it.toAddress == destinationAddress }
        ?: txStatus.outTxs?.firstOrNull()
    val amountOutReal = outTx?.coins?.firstOrNull()?.amount?.movePointLeft(8)

    val finishedAt = if (status == TransactionStatusEnum.FINISHED || status == TransactionStatusEnum.REFUNDED) {
        System.currentTimeMillis()
    } else {
        null
    }

    return SwapProviderTransactionStatusResult(
        status = status,
        amountOutReal = amountOutReal,
        finishedAt = finishedAt,
    )
}
