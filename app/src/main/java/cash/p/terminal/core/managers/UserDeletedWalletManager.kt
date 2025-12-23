package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.UserDeletedWalletDao
import cash.p.terminal.entities.UserDeletedWallet
import cash.p.terminal.wallet.Wallet
import java.util.concurrent.ConcurrentHashMap

class UserDeletedWalletManager(
    private val userDeletedWalletDao: UserDeletedWalletDao
) {
    // In-memory cache to prevent race condition where sync jobs check isDeletedByUser()
    // before the DB insert from markAsDeleted() commits.
    // Using ConcurrentHashMap for thread-safety across coroutines.
    private val pendingDeletions = ConcurrentHashMap.newKeySet<String>()

    private fun cacheKey(accountId: String, tokenQueryId: String): String {
        return "$accountId:$tokenQueryId"
    }

    suspend fun markAsDeleted(wallet: Wallet) {
        val accountId = wallet.account.id
        val tokenQueryId = wallet.token.tokenQuery.id

        // Add to in-memory cache FIRST (synchronously) to prevent race condition
        // with sync coroutines checking isDeletedByUser() before DB commit
        pendingDeletions.add(cacheKey(accountId, tokenQueryId))

        val entity = UserDeletedWallet(
            accountId = accountId,
            tokenQueryId = tokenQueryId
        )
        userDeletedWalletDao.insert(entity)
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
}
