package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.OfflineSignedTransactionDao
import cash.p.terminal.entities.DecodedOfflineTransaction
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.entities.OfflineSignedTransactionStatus
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.tokenQueryId
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import timber.log.Timber

class OfflineSignedTransactionRepository(
    private val dao: OfflineSignedTransactionDao,
    private val dispatcherProvider: DispatcherProvider,
) {
    // Best-effort: a storage failure must not turn a successful offline signature into a failure,
    // so the signed QR is still shown to the user even if persistence fails.
    suspend fun save(draft: OfflineSignedTransactionDraft, pcashPayload: String) {
        executeSafely("persist") {
            dao.insertIfAbsent(draft.toEntity(pcashPayload))
        }
    }

    suspend fun saveImported(
        wallet: Wallet,
        decoded: DecodedOfflineTransaction,
        pcashPayload: String,
    ) {
        executeSafely("persist imported") {
            dao.insertIfAbsent(decoded.toEntity(wallet, pcashPayload))
        }
    }

    suspend fun saveRawImported(
        wallet: Wallet,
        rawHex: String,
        txHash: String,
    ) {
        executeSafely("persist raw imported") {
            dao.insertIfAbsent(rawImportEntity(wallet, rawHex, txHash))
        }
    }

    suspend fun markBroadcastAttempt(accountId: String, txHash: String) {
        executeSafely("mark broadcast attempt") {
            dao.markBroadcastAttempt(
                accountId = accountId,
                txHash = txHash,
                status = OfflineSignedTransactionStatus.Pending.value,
                timestamp = System.currentTimeMillis(),
                broadcastedStatus = OfflineSignedTransactionStatus.Broadcasted.value,
            )
        }
    }

    suspend fun markBroadcasted(accountId: String, txHash: String, confirmedTxHash: String) {
        executeSafely("mark broadcasted") {
            dao.reconcileBroadcasted(
                accountId = accountId,
                txHash = txHash,
                confirmedTxHash = confirmedTxHash,
                status = OfflineSignedTransactionStatus.Broadcasted.value,
                timestamp = System.currentTimeMillis(),
            )
        }
    }

    suspend fun markBroadcastFailed(accountId: String, txHash: String, error: String) {
        executeSafely("mark broadcast failed") {
            dao.markBroadcastFailed(
                accountId = accountId,
                txHash = txHash,
                status = OfflineSignedTransactionStatus.Pending.value,
                error = error,
                broadcastedStatus = OfflineSignedTransactionStatus.Broadcasted.value,
            )
        }
    }

    fun observe(accountId: String): Flow<List<OfflineSignedTransactionEntity>> =
        dao.observe(accountId)

    private suspend fun executeSafely(action: String, block: suspend () -> Unit) {
        try {
            withContext(NonCancellable + dispatcherProvider.io) {
                block()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Failed to $action offline signed transaction")
        }
    }

    private fun OfflineSignedTransactionDraft.toEntity(pcashPayload: String): OfflineSignedTransactionEntity {
        val feeToken = feeToken ?: wallet.token
        return OfflineSignedTransactionEntity(
            accountId = wallet.account.id,
            txHash = txHash,
            blockchainTypeUid = wallet.token.blockchainType.uid,
            tokenQueryId = wallet.tokenQueryId,
            sourceTokenQueryId = wallet.tokenQueryId,
            coinUid = wallet.token.coin.uid,
            coinCode = wallet.token.coin.code,
            coinName = wallet.token.coin.name,
            tokenDecimals = wallet.token.decimals,
            amount = amount.toPlainString(),
            feeTokenQueryId = fee?.let { feeToken.tokenQuery.id },
            feeAtomic = fee?.toAtomicString(feeToken.decimals),
            solanaBlockHash = solanaRetryMetadata?.blockHash,
            solanaLastValidBlockHeight = solanaRetryMetadata?.lastValidBlockHeight,
            tonValidUntil = tonRetryMetadata?.validUntil,
            tonSenderAddress = tonRetryMetadata?.senderAddress,
            tonSeqno = tonRetryMetadata?.seqno,
            tronExpiration = tronRetryMetadata?.expiration,
            toAddress = toAddress,
            rawHex = rawHex,
            pcashPayload = pcashPayload,
            createdAt = createdAt,
            status = OfflineSignedTransactionStatus.Pending.value,
            broadcastAttempts = 0,
            lastBroadcastAt = null,
            broadcastedAt = null,
            lastError = null,
        )
    }

    private fun DecodedOfflineTransaction.toEntity(
        wallet: Wallet,
        pcashPayload: String,
    ) = OfflineSignedTransactionEntity(
        accountId = wallet.account.id,
        txHash = txHash,
        blockchainTypeUid = blockchainUid,
        tokenQueryId = token.tokenQueryId,
        sourceTokenQueryId = wallet.tokenQueryId,
        coinUid = token.coinUid,
        coinCode = token.coinCode,
        coinName = token.coinName,
        tokenDecimals = token.decimals,
        amount = amountAtomic.toBigDecimalOrNull()
            ?.movePointLeft(token.decimals)
            ?.toPlainString()
            .orEmpty(),
        feeTokenQueryId = fee?.tokenQueryId,
        feeAtomic = fee?.atomic,
        solanaBlockHash = solanaRetryMetadata?.blockHash,
        solanaLastValidBlockHeight = solanaRetryMetadata?.lastValidBlockHeight,
        tonValidUntil = tonRetryMetadata?.validUntil,
        tonSenderAddress = tonRetryMetadata?.senderAddress,
        tonSeqno = tonRetryMetadata?.seqno,
        tronExpiration = tronRetryMetadata?.expiration,
        toAddress = toAddress,
        rawHex = rawHex,
        pcashPayload = pcashPayload,
        createdAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        status = OfflineSignedTransactionStatus.Pending.value,
        broadcastAttempts = 0,
        lastBroadcastAt = null,
        broadcastedAt = null,
        lastError = null,
    )

    private fun rawImportEntity(
        wallet: Wallet,
        rawHex: String,
        txHash: String,
    ) = OfflineSignedTransactionEntity(
        accountId = wallet.account.id,
        txHash = txHash,
        blockchainTypeUid = wallet.token.blockchainType.uid,
        tokenQueryId = "",
        sourceTokenQueryId = wallet.tokenQueryId,
        coinUid = wallet.token.coin.uid,
        coinCode = wallet.token.coin.code,
        coinName = wallet.token.coin.name,
        tokenDecimals = wallet.token.decimals,
        amount = "",
        feeTokenQueryId = null,
        feeAtomic = null,
        solanaBlockHash = null,
        solanaLastValidBlockHeight = null,
        tonValidUntil = null,
        tonSenderAddress = null,
        tonSeqno = null,
        tronExpiration = null,
        toAddress = "",
        rawHex = rawHex,
        pcashPayload = "",
        createdAt = System.currentTimeMillis(),
        status = OfflineSignedTransactionStatus.Pending.value,
        broadcastAttempts = 0,
        lastBroadcastAt = null,
        broadcastedAt = null,
        lastError = null,
    )

    private fun BigDecimal.toAtomicString(decimals: Int): String =
        movePointRight(decimals).toBigInteger().toString()
}
