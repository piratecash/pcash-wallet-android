package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.UserDeletedWalletDao
import cash.p.terminal.wallet.IDeletedWalletChecker

class DeletedWalletChecker(
    private val userDeletedWalletDao: UserDeletedWalletDao
) : IDeletedWalletChecker {
    override suspend fun getDeletedTokenQueryIds(accountId: String): Set<String> {
        return userDeletedWalletDao.getDeletedTokenQueryIds(accountId).toSet()
    }
}
