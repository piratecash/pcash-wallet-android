package cash.p.terminal.core.managers

import cash.p.terminal.core.adapters.BitcoinAdapter
import cash.p.terminal.core.adapters.BitcoinCashAdapter
import cash.p.terminal.core.adapters.DashAdapter
import cash.p.terminal.core.adapters.ECashAdapter
import cash.p.terminal.core.adapters.Eip20Adapter
import cash.p.terminal.core.adapters.EvmAdapter
import cash.p.terminal.core.adapters.SolanaAdapter
import cash.p.terminal.core.adapters.TronAdapter
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.domain.usecase.ClearZCashWalletDataUseCase
import cash.p.terminal.wallet.IAccountCleaner
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import io.horizontalsystems.core.entities.BlockchainType

class AccountCleaner(
    private val clearZCashWalletDataUseCase: ClearZCashWalletDataUseCase,
    private val removeMoneroWalletFilesUseCase: RemoveMoneroWalletFilesUseCase,
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val moneroFileDao: MoneroFileDao
) : IAccountCleaner {

    override suspend fun clearAccounts(accountIds: List<String>) {
        accountIds.forEach { clearAccount(it) }
    }


    private suspend fun clearAccount(accountId: String) {
        BitcoinAdapter.clear(accountId)
        BitcoinCashAdapter.clear(accountId)
        ECashAdapter.clear(accountId)
        DashAdapter.clear(accountId)
        EvmAdapter.clear(accountId)
        Eip20Adapter.clear(accountId)
        clearWalletForAccount(accountId, BlockchainType.Monero)
        clearWalletForAccount(accountId, BlockchainType.Zcash)
        SolanaAdapter.clear(accountId)
        TronAdapter.clear(accountId)
    }

    /***
     * Clear wallet data for blockchains where we can change birthday height
     */
    override suspend fun clearWalletForAccount(accountId: String, blockchainType: BlockchainType) {
        if (isWalletActive(accountId, blockchainType)) return // do not clear active wallet

        when (blockchainType) {
            is BlockchainType.Zcash -> clearZCashWalletDataUseCase.invoke(accountId)
            is BlockchainType.Monero -> {
                accountManager.account(accountId)?.let {
                    removeMoneroWalletFilesUseCase(it)
                    moneroFileDao.deleteAssociatedRecord(it.id)
                }
            }

            else -> {

            }
        }
    }

    private fun isWalletActive(accountId: String, blockchainType: BlockchainType): Boolean {
        return walletManager.activeWallets.any {
            it.account.id == accountId && it.token.blockchainType == blockchainType
        }
    }

    override suspend fun clearWalletForCurrentAccount(blockchainType: BlockchainType) {
        accountManager.activeAccount?.let {
            clearWalletForAccount(it.id, blockchainType)
        }
    }
}
