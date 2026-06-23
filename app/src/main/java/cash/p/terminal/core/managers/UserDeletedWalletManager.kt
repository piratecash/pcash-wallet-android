package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.UserDeletedWalletDao
import cash.p.terminal.entities.UserDeletedWallet
import cash.p.terminal.wallet.IDeletedWalletRestorer
import cash.p.terminal.wallet.Wallet
import java.util.concurrent.ConcurrentHashMap

class UserDeletedWalletManager(
    private val userDeletedWalletDao: UserDeletedWalletDao
) : IDeletedWalletRestorer {
    // In-memory cache to prevent race condition where sync jobs check isDeletedByUser()
    // before the DB insert from markAsDeleted() commits.
    // Using ConcurrentHashMap for thread-safety across coroutines.
    private val pendingDeletions = ConcurrentHashMap.newKeySet<String>()

    private fun cacheKey(accountId: String, tokenQueryId: String): String {
        return "$accountId:$tokenQueryId"
    }

    suspend fun markAsDeleted(wallet: Wallet) {
        markAsDeleted(wallet.account.id, wallet.token.tokenQuery.id)
    }

    suspend fun markAsDeleted(accountId: String, tokenQueryId: String) {
        // Add to in-memory cache first to avoid sync jobs reading DB before insert commits.
        pendingDeletions.add(cacheKey(accountId, tokenQueryId))
        userDeletedWalletDao.insert(UserDeletedWallet(accountId, tokenQueryId))
    }

    suspend fun markAsDeleted(accountId: String, tokenQueryIds: Iterable<String>) {
        tokenQueryIds.forEach { markAsDeleted(accountId, it) }
    }

    suspend fun isDeletedByUser(accountId: String, tokenQueryId: String): Boolean {
        // Check in-memory cache first for immediate response
        // This handles the race condition where DB insert hasn't committed yet
        if (pendingDeletions.contains(cacheKey(accountId, tokenQueryId))) {
            return true
        }
        return userDeletedWalletDao.exists(accountId, tokenQueryId)
    }

    suspend fun unmarkAsDeleted(accountId: String, tokenQueryId: String) {
        pendingDeletions.remove(cacheKey(accountId, tokenQueryId))
        userDeletedWalletDao.delete(accountId, tokenQueryId)
    }

    override suspend fun unmarkAsDeleted(accountId: String, tokenQueryIds: Iterable<String>) {
        tokenQueryIds.forEach { unmarkAsDeleted(accountId, it) }
    }
}
