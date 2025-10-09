package cash.p.terminal.wallet

import io.horizontalsystems.core.entities.BlockchainType

interface IAccountCleaner {
    suspend fun clearAccounts(accountIds: List<String>)
    suspend fun clearWalletForAccount(accountId: String, blockchainType: BlockchainType)
    suspend fun clearWalletForCurrentAccount(blockchainType: BlockchainType)
}
