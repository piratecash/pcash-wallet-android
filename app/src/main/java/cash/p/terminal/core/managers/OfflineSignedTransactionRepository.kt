package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.OfflineSignedTransactionDao
import cash.p.terminal.entities.DecodedOfflineTransaction
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.entities.OfflineSignedTransactionStatus
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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

    private fun OfflineSignedTransactionDraft.toEntity(pcashPayload: String) = OfflineSignedTransactionEntity(
        accountId = wallet.account.id,
        txHash = txHash,
        blockchainTypeUid = wallet.token.blockchainType.uid,
        coinCode = wallet.token.coin.code,
        tokenDecimals = wallet.token.decimals,
        amount = amount.toPlainString(),
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

    private fun DecodedOfflineTransaction.toEntity(
        wallet: Wallet,
        pcashPayload: String,
    ) = OfflineSignedTransactionEntity(
        accountId = wallet.account.id,
        txHash = txHash,
        blockchainTypeUid = wallet.token.blockchainType.uid,
        coinCode = wallet.token.coin.code,
        tokenDecimals = wallet.token.decimals,
        amount = amountAtomic.toBigDecimalOrNull()
            ?.movePointLeft(wallet.token.decimals)
            ?.toPlainString()
            .orEmpty(),
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
}
