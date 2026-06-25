package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineSignedTransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: OfflineSignedTransactionEntity)

    @Query(
        "SELECT * FROM OfflineSignedTransaction WHERE accountId = :accountId ORDER BY createdAt DESC"
    )
    fun observe(accountId: String): Flow<List<OfflineSignedTransactionEntity>>

    @Query(
        """
        UPDATE OfflineSignedTransaction
        SET status = :status,
            broadcastAttempts = broadcastAttempts + 1,
            lastBroadcastAt = :timestamp,
            lastError = NULL
        WHERE accountId = :accountId AND txHash = :txHash AND status != :broadcastedStatus
        """
    )
    suspend fun markBroadcastAttempt(
        accountId: String,
        txHash: String,
        status: String,
        timestamp: Long,
        broadcastedStatus: String,
    )

    // Reconciles the imported row to the authoritative hash derived by the broadcasting kit.
    // Renaming the primary key to confirmedTxHash would hit a unique conflict if the same transaction
    // was already imported under that hash (e.g. a second import that claimed a spoofed txHash). In
    // that case the authoritative row already exists, so the duplicate import is dropped and the
    // surviving row is marked broadcasted; otherwise the imported row is simply renamed and marked.
    @Transaction
    suspend fun reconcileBroadcasted(
        accountId: String,
        txHash: String,
        confirmedTxHash: String,
        status: String,
        timestamp: Long,
    ) {
        if (txHash != confirmedTxHash && exists(accountId, confirmedTxHash)) {
            delete(accountId, txHash)
            markBroadcasted(accountId, confirmedTxHash, confirmedTxHash, status, timestamp)
        } else {
            markBroadcasted(accountId, txHash, confirmedTxHash, status, timestamp)
        }
    }

    // confirmedTxHash is the txid the broadcasting kit derived from the raw bytes. It overwrites the
    // imported (untrusted) txHash so the persisted history is keyed by the authoritative hash even if
    // the scanned payload claimed a different one.
    @Query(
        """
        UPDATE OfflineSignedTransaction
        SET status = :status,
            txHash = :confirmedTxHash,
            broadcastedAt = :timestamp,
            lastError = NULL
        WHERE accountId = :accountId AND txHash = :txHash
        """
    )
    suspend fun markBroadcasted(
        accountId: String,
        txHash: String,
        confirmedTxHash: String,
        status: String,
        timestamp: Long,
    )

    @Query(
        "SELECT EXISTS(SELECT 1 FROM OfflineSignedTransaction WHERE accountId = :accountId AND txHash = :txHash)"
    )
    suspend fun exists(accountId: String, txHash: String): Boolean

    @Query(
        """
        SELECT * FROM OfflineSignedTransaction
        WHERE rawHex = :rawHex AND status = :pendingStatus
        """
    )
    suspend fun pendingByRawHex(rawHex: String, pendingStatus: String): List<OfflineSignedTransactionEntity>

    @Query(
        """
        SELECT pending.accountId AS accountId,
               pending.txHash AS txHash,
               (
                   SELECT sent.txHash
                   FROM OfflineSignedTransaction AS sent
                   WHERE sent.rawHex = pending.rawHex AND sent.status = :broadcastedStatus
                   ORDER BY sent.broadcastedAt DESC, sent.createdAt DESC
                   LIMIT 1
               ) AS confirmedTxHash
        FROM OfflineSignedTransaction AS pending
        WHERE pending.accountId = :accountId
          AND pending.status = :pendingStatus
          AND EXISTS (
              SELECT 1
              FROM OfflineSignedTransaction AS sent
              WHERE sent.rawHex = pending.rawHex AND sent.status = :broadcastedStatus
          )
        """
    )
    suspend fun broadcastedRawMatches(
        accountId: String,
        pendingStatus: String,
        broadcastedStatus: String,
    ): List<OfflineBroadcastedRawMatch>

    @Query("DELETE FROM OfflineSignedTransaction WHERE accountId = :accountId AND txHash = :txHash")
    suspend fun delete(accountId: String, txHash: String)

    @Query(
        """
        UPDATE OfflineSignedTransaction
        SET status = :status,
            lastError = :error
        WHERE accountId = :accountId AND txHash = :txHash AND status != :broadcastedStatus
        """
    )
    suspend fun markBroadcastFailed(
        accountId: String,
        txHash: String,
        status: String,
        error: String,
        broadcastedStatus: String,
    )
}

data class OfflineBroadcastedRawMatch(
    val accountId: String,
    val txHash: String,
    val confirmedTxHash: String,
)
