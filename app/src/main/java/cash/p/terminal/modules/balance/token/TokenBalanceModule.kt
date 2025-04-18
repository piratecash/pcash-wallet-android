package cash.p.terminal.modules.balance.token

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.modules.balance.BalanceAdapterRepository
import cash.p.terminal.modules.balance.BalanceCache
import cash.p.terminal.modules.balance.BalanceViewItem
import cash.p.terminal.modules.balance.BalanceViewItemFactory
import cash.p.terminal.modules.balance.DefaultBalanceXRateRepository
import cash.p.terminal.modules.balance.TotalBalance
import cash.p.terminal.modules.balance.TotalService
import cash.p.terminal.modules.transactions.NftMetadataService
import cash.p.terminal.modules.transactions.TransactionSyncStateRepository
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.TransactionsRateRepository
import cash.p.terminal.wallet.Wallet

class TokenBalanceModule {

    class Factory(private val wallet: Wallet) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val balanceService = TokenBalanceService(
                wallet = wallet,
                xRateRepository = DefaultBalanceXRateRepository("wallet", App.currencyManager, App.marketKit),
                balanceAdapterRepository = BalanceAdapterRepository(
                    App.adapterManager,
                    BalanceCache(App.appDatabase.enabledWalletsCacheDao())
                ),
            )

            val tokenTransactionsService = TokenTransactionsService(
                wallet = wallet,
                rateRepository = TransactionsRateRepository(App.currencyManager, App.marketKit),
                transactionSyncStateRepository = TransactionSyncStateRepository(App.transactionAdapterManager),
                contactsRepository = App.contactsRepository,
                nftMetadataService = NftMetadataService(App.nftMetadataManager),
                spamManager = App.spamManager
            )

            val totalService = TotalService(
                currencyManager = App.currencyManager,
                marketKit = App.marketKit,
                baseTokenManager = App.baseTokenManager,
                balanceHiddenManager = App.balanceHiddenManager
            )

            return TokenBalanceViewModel(
                totalBalance = TotalBalance(
                    totalService = totalService,
                    balanceHiddenManager = App.balanceHiddenManager
                ),
                wallet = wallet,
                balanceService = balanceService,
                balanceViewItemFactory = BalanceViewItemFactory(),
                transactionsService = tokenTransactionsService,
                transactionViewItem2Factory = getKoinInstance(),
                balanceHiddenManager = App.balanceHiddenManager,
                connectivityManager = App.connectivityManager,
                accountManager = App.accountManager,
                transactionHiddenManager = getKoinInstance(),
                getChangeNowAssociatedCoinTickerUseCase = getKoinInstance()
            ) as T
        }
    }

    data class TokenBalanceUiState(
        val title: String,
        val balanceViewItem: BalanceViewItem?,
        val transactions: Map<String, List<TransactionViewItem>>?,
        val hasHiddenTransactions: Boolean
    )
}
