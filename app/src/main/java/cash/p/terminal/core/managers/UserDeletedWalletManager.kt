package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.UserDeletedWalletDao
import cash.p.terminal.entities.UserDeletedWallet
import cash.p.terminal.wallet.Wallet

class UserDeletedWalletManager(
    private val userDeletedWalletDao: UserDeletedWalletDao
) {

    suspend fun markAsDeleted(wallet: Wallet) {
        val entity = UserDeletedWallet(
            accountId = wallet.account.id,
            tokenQueryId = wallet.token.tokenQuery.id
        )
        userDeletedWalletDao.insert(entity)
    }

    suspend fun isDeletedByUser(accountId: String, tokenQueryId: String): Boolean {
        return userDeletedWalletDao.exists(accountId, tokenQueryId)
    }
}
