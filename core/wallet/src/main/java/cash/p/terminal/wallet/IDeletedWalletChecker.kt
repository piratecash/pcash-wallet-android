package cash.p.terminal.wallet

interface IDeletedWalletChecker {
    suspend fun getDeletedTokenQueryIds(accountId: String): Set<String>
}
