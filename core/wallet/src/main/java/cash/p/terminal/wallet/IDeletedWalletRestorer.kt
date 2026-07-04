package cash.p.terminal.wallet

interface IDeletedWalletRestorer {
    suspend fun unmarkAsDeleted(accountId: String, tokenQueryIds: Iterable<String>)
}
