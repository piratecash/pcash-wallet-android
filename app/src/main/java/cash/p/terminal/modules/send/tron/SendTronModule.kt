package cash.p.terminal.modules.send.tron

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.isNative
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.getMaxSendableBalance
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.java.KoinJavaComponent.inject
import java.math.RoundingMode

object SendTronModule {

    class Factory(
        private val wallet: Wallet,
        private val address: Address?,
        private val hideAddress: Boolean,
        private val adapter: ISendTronAdapter
    ) : ViewModelProvider.Factory {
        private val adapterManager: IAdapterManager by inject(IAdapterManager::class.java)
        private val dispatcherProvider: DispatcherProvider by inject(DispatcherProvider::class.java)
        private val recentAddressManager: RecentAddressManager by inject(RecentAddressManager::class.java)
        private val offlineTransactionPayloadEncoder: OfflineTransactionPayloadEncoder by inject(OfflineTransactionPayloadEncoder::class.java)
        private val offlineSignedTransactionRepository: OfflineSignedTransactionRepository by inject(OfflineSignedTransactionRepository::class.java)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when (modelClass) {
                SendTronViewModel::class.java -> {
                    val amountValidator = AmountValidator()
                    val coinMaxAllowedDecimals = wallet.token.decimals

                    val availableBalance = adapterManager.getMaxSendableBalance(wallet, adapter.maxSpendableBalance)
                    val amountService = SendAmountService(
                        amountValidator,
                        wallet.token.coin.code,
                        availableBalance.setScale(coinMaxAllowedDecimals, RoundingMode.DOWN),
                        wallet.token.type.isNative,
                    )
                    val addressService = SendTronAddressService(adapter, wallet.token)
                    val xRateService = XRateService(App.marketKit, App.currencyManager.baseCurrency)
                    val feeToken = App.coinManager.getToken(TokenQuery(BlockchainType.Tron, TokenType.Native)) ?: throw IllegalArgumentException()

                    SendTronViewModel(
                        wallet = wallet,
                        sendToken = wallet.token,
                        feeToken = feeToken,
                        adapter = adapter,
                        xRateService = xRateService,
                        amountService = amountService,
                        addressService = addressService,
                        coinMaxAllowedDecimals = coinMaxAllowedDecimals,
                        contactsRepo = App.contactsRepository,
                        showAddressInput = !hideAddress,
                        connectivityManager = App.connectivityManager,
                        address = address,
                        adapterManager = adapterManager,
                        dispatcherProvider = dispatcherProvider,
                        recentAddressManager = recentAddressManager,
                        offlineTransactionPayloadEncoder = offlineTransactionPayloadEncoder,
                        offlineSignedTransactionRepository = offlineSignedTransactionRepository,
                    ) as T
                }

                else -> throw IllegalArgumentException()
            }
        }
    }
}
