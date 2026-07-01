package cash.p.terminal.modules.receive.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.modules.receive.ui.AddressFormatItem
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.bitcoinCashCoinType
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.zCashCoinType

class BchAddressTypeSelectViewModel(coinUid: String, walletManager: IWalletManager) : ViewModel() {
    val items = walletManager.activeWallets
        .filter {
            it.coin.uid == coinUid
        }
        .mapNotNull { wallet ->
            when (val tokenType = wallet.token.type) {
                is TokenType.AddressTyped -> {
                    val bitcoinCashCoinType = tokenType.type.bitcoinCashCoinType
                    AddressFormatItem(
                        title = bitcoinCashCoinType.title,
                        subtitle = bitcoinCashCoinType.value.uppercase(),
                        wallet = wallet
                    )
                }

                is TokenType.AddressSpecTyped -> {
                    val zCashCoinType = tokenType.type.zCashCoinType
                    AddressFormatItem(
                        title = zCashCoinType.title,
                        subtitle = zCashCoinType.value.uppercase(),
                        wallet = wallet
                    )
                }

                else -> null
            }
        }

    class Factory(private val coinUid: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BchAddressTypeSelectViewModel(coinUid, App.walletManager) as T
        }
    }
}
