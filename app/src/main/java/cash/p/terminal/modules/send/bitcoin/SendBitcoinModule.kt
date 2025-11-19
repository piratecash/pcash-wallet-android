package cash.p.terminal.modules.send.bitcoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.core.factories.FeeRateProviderFactory
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType

object SendBitcoinModule {
    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val wallet: Wallet,
        private val address: Address?,
        private val hideAddress: Boolean,
        private val adapter: ISendBitcoinAdapter
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val provider = FeeRateProviderFactory.provider(wallet.token.blockchainType)!!
            val feeService = SendBitcoinFeeService(adapter)
            val feeRateService = SendBitcoinFeeRateService(provider)
            val amountService =
                SendBitcoinAmountService(adapter, wallet.coin.code, AmountValidator())
            val addressService = SendBitcoinAddressService(adapter)
            val pluginService = SendBitcoinPluginService(wallet.token.blockchainType)
            return SendBitcoinViewModel(
                adapter = adapter,
                wallet = wallet,
                feeRateService = feeRateService,
                feeService = feeService,
                amountService = amountService,
                addressService = addressService,
                pluginService = pluginService,
                xRateService = XRateService(App.marketKit, App.currencyManager.baseCurrency),
                btcBlockchainManager = App.btcBlockchainManager,
                contactsRepo = App.contactsRepository,
                showAddressInput = !hideAddress,
                localStorage = App.localStorage,
                address = address
            ) as T
        }
    }

    data class UtxoData(
        val type: UtxoType? = null,
        val value: String = "0 / 0",
    )

    enum class UtxoType {
        Auto,
        Manual
    }

    val BlockchainType.rbfSupported: Boolean
        get() = when (this) {
            BlockchainType.Bitcoin,
            BlockchainType.Litecoin -> true

            else -> false
        }
}
