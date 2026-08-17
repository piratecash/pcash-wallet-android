package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import cash.p.terminal.entities.OfflineBlockchain
import io.horizontalsystems.core.entities.BlockchainType

/**
 * `@Upsert` is deliberately not used: it rewrites every column, so a sync stamp would clobber the
 * authoritative [OfflineBlockchain.offline] flag written by another coroutine. Each writer updates
 * only its own columns instead, creating the row with `IGNORE` when absent.
 */
@Dao
interface OfflineBlockchainDao {

    /**
     * Non-suspend on purpose: the manager reads the cache synchronously in `init` as a cold-start
     * barrier, which only works because the database allows main-thread queries.
     */
    @Query("SELECT * FROM OfflineBlockchain")
    fun getAll(): List<OfflineBlockchain>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: OfflineBlockchain)

    @Query(
        "UPDATE OfflineBlockchain SET offline = :offline, enabledAt = :enabledAt " +
            "WHERE accountId = :accountId AND blockchainType = :blockchainType"
    )
    suspend fun updateMode(
        accountId: String,
        blockchainType: BlockchainType,
        offline: Boolean,
        enabledAt: Long?,
    )

    @Query(
        "UPDATE OfflineBlockchain SET lastSyncedAt = :lastSyncedAt " +
            "WHERE accountId = :accountId AND blockchainType = :blockchainType"
    )
    suspend fun updateLastSynced(
        accountId: String,
        blockchainType: BlockchainType,
        lastSyncedAt: Long,
    )

    @Query("DELETE FROM OfflineBlockchain WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: String)

    @Query("DELETE FROM OfflineBlockchain WHERE accountId = :accountId AND blockchainType = :blockchainType")
    suspend fun deleteChain(accountId: String, blockchainType: BlockchainType)

    @Transaction
    suspend fun setMode(
        accountId: String,
        blockchainType: BlockchainType,
        offline: Boolean,
        enabledAt: Long?,
    ) {
        insertIfAbsent(
            OfflineBlockchain(accountId, blockchainType, offline, enabledAt, lastSyncedAt = null)
        )
        updateMode(accountId, blockchainType, offline, enabledAt)
    }

    @Transaction
    suspend fun setLastSynced(
        accountId: String,
        blockchainType: BlockchainType,
        lastSyncedAt: Long,
    ) {
        insertIfAbsent(
            OfflineBlockchain(accountId, blockchainType, offline = false, enabledAt = null, lastSyncedAt = lastSyncedAt)
        )
        updateLastSynced(accountId, blockchainType, lastSyncedAt)
    }
}
