package cash.p.terminal.modules.receive.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.eligibleTokens
import cash.p.terminal.core.utils.Utils
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.useCases.WalletUseCase
import org.koin.java.KoinJavaComponent.inject

class NetworkSelectViewModel(
    val activeAccount: Account,
    val fullCoin: FullCoin
) : ViewModel() {
    val eligibleTokens = fullCoin.eligibleTokens(activeAccount.type)

    private val walletUseCase: WalletUseCase by inject(WalletUseCase::class.java)

    suspend fun getOrCreateWallet(token: Token): Wallet? {
        return walletUseCase.getWallet(token)
            ?: createWallet(token)
    }

    private suspend fun createWallet(token: Token): Wallet? {
        val wallet = walletUseCase.createWalletIfNotExists(token) ?: return null

        Utils.waitUntil(1000L, 100L) {
            App.adapterManager.getReceiveAdapterForWallet(wallet) != null
        }

        return wallet
    }

    class Factory(
        private val activeAccount: Account,
        private val fullCoin: FullCoin
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NetworkSelectViewModel(activeAccount, fullCoin) as T
        }
    }
}
