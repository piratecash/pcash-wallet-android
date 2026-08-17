package cash.p.terminal.core.storage

import cash.p.terminal.entities.OfflineBlockchain
import io.horizontalsystems.core.entities.BlockchainType

class OfflineModeStorage(appDatabase: AppDatabase) {

    private val dao by lazy { appDatabase.offlineBlockchainDao() }

    fun getAll(): List<OfflineBlockchain> = dao.getAll()

    suspend fun setMode(
        accountId: String,
        blockchainType: BlockchainType,
        offline: Boolean,
        enabledAt: Long?,
    ) = dao.setMode(accountId, blockchainType, offline, enabledAt)

    suspend fun setLastSynced(
        accountId: String,
        blockchainType: BlockchainType,
        lastSyncedAt: Long,
    ) = dao.setLastSynced(accountId, blockchainType, lastSyncedAt)

    suspend fun deleteByAccount(accountId: String) = dao.deleteByAccount(accountId)

    suspend fun deleteChain(accountId: String, blockchainType: BlockchainType) =
        dao.deleteChain(accountId, blockchainType)
}
