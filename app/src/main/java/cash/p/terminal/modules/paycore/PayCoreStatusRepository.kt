package cash.p.terminal.modules.paycore

import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusResult
import timber.log.Timber

class PayCoreStatusRepository(
    private val apiService: PayCoreApiService,
    private val storage: SwapProviderTransactionsStorage,
) : SwapProviderTransactionStatusRepository {

    private companion object {
        const val DEFAULT_NETWORK_TYPE = "ERC20"
    }

    override suspend fun getTransactionStatus(
        transactionId: String,
        destinationAddress: String,
    ): SwapProviderTransactionStatusResult {
        val transaction = storage.getTransaction(transactionId)
            ?: return SwapProviderTransactionStatusResult(status = TransactionStatusEnum.WAITING)

        // Records saved before backend indexed the on-chain tx live with
        // transactionId == outgoingRecordUid (a txHash placeholder). Try to
        // upgrade to a real payoutId before we attempt any /status lookup —
        // calling status with a txHash would only get 404s.
        val effectiveId = when (val recovery = recoverPayoutIdIfNeeded(transaction)) {
            is PayoutRecovery.Recovered -> recovery.payoutId
            PayoutRecovery.Pending -> return SwapProviderTransactionStatusResult(
                status = TransactionStatusEnum.WAITING
            )
            PayoutRecovery.Failed -> return SwapProviderTransactionStatusResult(
                status = TransactionStatusEnum.FAILED
            )
        }

        val isPayment = PayCoreAssets.isRub(transaction.coinUidIn)
        val networkType = resolveNetworkType(transaction, isPayment)
        val transactionType = if (isPayment) "payment" else "payout"

        val response = apiService.getTransactionStatus(transactionType, effectiveId, networkType)
        return SwapProviderTransactionStatusResult(status = resolveStatus(isPayment, response))
    }

    private suspend fun recoverPayoutIdIfNeeded(transaction: SwapProviderTransaction): PayoutRecovery {
        val txHash = transaction.outgoingRecordUid
            ?: return PayoutRecovery.Recovered(transaction.transactionId)
        if (transaction.transactionId != txHash) return PayoutRecovery.Recovered(transaction.transactionId)

        val networkType = PayCoreNetworkMapper.fromBlockchainTypeUid(transaction.blockchainTypeIn)
            ?: return PayoutRecovery.Pending

        val response = tryOrNull {
            apiService.processPayOut(
                PayCorePayoutProcessRequest(
                    transactionHash = txHash,
                    backUrl = PAYCORE_COMPLETE_BACK_URL,
                ),
                networkType = networkType,
            )
        } ?: run {
            Timber.d("PayCore: processPayOut call failed for txHash=%s", txHash)
            return PayoutRecovery.Pending
        }

        if (response.isTerminalFailure()) {
            Timber.w("PayCore: processPayOut reported terminal failure (status=3) for txHash=%s", txHash)
            return PayoutRecovery.Failed
        }

        val payoutId = response.transactionIdOrNull()
        if (payoutId == null) {
            Timber.d(
                "PayCore: backend still has no payoutId for txHash=%s (status=%d)",
                txHash, response.status
            )
            return PayoutRecovery.Pending
        }
        storage.updateTransactionId(transaction.date, payoutId)
        Timber.d("PayCore: migrated placeholder txHash=%s to payoutId=%s", txHash, payoutId)
        return PayoutRecovery.Recovered(payoutId)
    }

    private sealed interface PayoutRecovery {
        data class Recovered(val payoutId: String) : PayoutRecovery
        data object Pending : PayoutRecovery
        data object Failed : PayoutRecovery
    }

    private fun resolveNetworkType(
        transaction: SwapProviderTransaction?,
        isPayment: Boolean,
    ): String {
        val blockchainTypeUid = when {
            transaction == null -> null
            isPayment -> transaction.blockchainTypeOut
            else -> transaction.blockchainTypeIn
        }

        return blockchainTypeUid?.let(PayCoreNetworkMapper::fromBlockchainTypeUid) ?: DEFAULT_NETWORK_TYPE
    }

    private fun resolveStatus(
        isPayment: Boolean,
        response: PayCoreTransactionStatusResponse,
    ): TransactionStatusEnum {
        val cryptoStatus = response.cryptoTransactionStatus?.let(::mapCryptoStatus)
        val fiatStatus = response.fiatTransactionStatus?.let(::mapFiatStatus)

        return when {
            isPayment -> combineStatuses(fiatStatus, cryptoStatus)
            else -> combineStatuses(cryptoStatus, fiatStatus)
        }
    }

    private fun combineStatuses(
        primary: TransactionStatusEnum?,
        secondary: TransactionStatusEnum?,
    ): TransactionStatusEnum {
        val statuses = listOfNotNull(primary, secondary)
        if (statuses.isEmpty()) return TransactionStatusEnum.WAITING
        if (statuses.any { it == TransactionStatusEnum.CREATED_OR_WAIT_USER }) return TransactionStatusEnum.CREATED_OR_WAIT_USER
        if (statuses.any { it == TransactionStatusEnum.FAILED }) return TransactionStatusEnum.FAILED
        val nonFinished = statuses.firstOrNull { it != TransactionStatusEnum.FINISHED }
        return nonFinished ?: TransactionStatusEnum.FINISHED
    }

    private fun mapCryptoStatus(status: String): TransactionStatusEnum = when (status) {
        "SUCCESS" -> TransactionStatusEnum.FINISHED

        "FAILED", "REJECTED", "RETURNED",
        "CANCEL_BY_USER", "FAIL", "REJECT" -> TransactionStatusEnum.FAILED

        "CREATED_OR_WAIT_USER" -> TransactionStatusEnum.CREATED_OR_WAIT_USER

        "MEMPOOL", "PENDING_CONFIRMATIONS", "PENDING_AML", "MANUAL_CHECK",
        "CREATE", "WAITING", "PROCESSING" -> TransactionStatusEnum.WAITING

        else -> {
            Timber.w("Unknown PayCore crypto_transaction_status: %s — treating as FAILED", status)
            TransactionStatusEnum.FAILED
        }
    }

    private fun mapFiatStatus(status: String): TransactionStatusEnum = when (status) {
        "OK" -> TransactionStatusEnum.FINISHED

        "CANCELED", "ERROR_BANK", "REFUNDED", "ERROR_INIT" -> TransactionStatusEnum.FAILED

        "CREATED_OR_WAIT_USER" -> TransactionStatusEnum.CREATED_OR_WAIT_USER

        "IN_PROGRESS", "WAIT_BANK", "HOLD",
        "ERROR_ANTIFRAUD", "REFUND_REQUESTED", "TIMEOUT",
        "PROCESSING_ERROR" -> TransactionStatusEnum.WAITING

        else -> {
            Timber.w("Unknown PayCore fiat_transaction_status: %s — treating as FAILED", status)
            TransactionStatusEnum.FAILED
        }
    }
}
