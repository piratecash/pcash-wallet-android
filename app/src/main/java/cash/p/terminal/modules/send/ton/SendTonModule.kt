package cash.p.terminal.modules.send.ton

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.isNative
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.getMaxSendableBalance
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import org.koin.java.KoinJavaComponent.inject

object SendTonModule {
    class Factory(
        private val wallet: Wallet,
        private val address: Address?,
        private val hideAddress: Boolean,
        private val adapter: ISendTonAdapter
    ) : ViewModelProvider.Factory {
        private val pendingRegistrar: PendingTransactionRegistrar by inject(PendingTransactionRegistrar::class.java)
        private val adapterManager: IAdapterManager by inject(IAdapterManager::class.java)
        private val dispatcherProvider: DispatcherProvider by inject(DispatcherProvider::class.java)
        private val recentAddressManager: RecentAddressManager by inject(RecentAddressManager::class.java)
        private val payloadEncoder: OfflineTransactionPayloadEncoder by inject(OfflineTransactionPayloadEncoder::class.java)
        private val offlineRepository: OfflineSignedTransactionRepository by inject(
            OfflineSignedTransactionRepository::class.java
        )

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when (modelClass) {
                SendTonViewModel::class.java -> {
                    val amountValidator = AmountValidator()
                    val coinMaxAllowedDecimals = wallet.token.decimals

                    val availableBalance = adapterManager.getMaxSendableBalance(wallet, adapter.maxSpendableBalance)
                    val amountService = SendTonAmountService(
                        amountValidator = amountValidator,
                        coinCode = wallet.coin.code,
                        availableBalance = availableBalance,
                        leaveSomeBalanceForFee = wallet.token.type.isNative
                    )
                    val addressService = SendTonAddressService()
                    val feeService = SendTonFeeService(adapter)
                    val xRateService = XRateService(App.marketKit, App.currencyManager.baseCurrency)
                    val feeToken = App.coinManager.getToken(TokenQuery(BlockchainType.Ton, TokenType.Native)) ?: throw IllegalArgumentException()

                    SendTonViewModel(
                        wallet = wallet,
                        sendToken = wallet.token,
                        feeToken = feeToken,
                        adapter = adapter,
                        xRateService = xRateService,
                        amountService = amountService,
                        addressService = addressService,
                        feeService = feeService,
                        coinMaxAllowedDecimals = coinMaxAllowedDecimals,
                        contactsRepo = App.contactsRepository,
                        showAddressInput = !hideAddress,
                        address = address,
                        pendingRegistrar = pendingRegistrar,
                        adapterManager = adapterManager,
                        dispatcherProvider = dispatcherProvider,
                        recentAddressManager = recentAddressManager,
                        offlineTransactionPayloadEncoder = payloadEncoder,
                        offlineSignedTransactionRepository = offlineRepository,
                    ) as T
                }

                else -> throw IllegalArgumentException()
            }
        }
    }

}

