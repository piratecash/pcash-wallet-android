package cash.p.terminal.modules.multiswap.sendtransaction.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import cash.p.terminal.core.App
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.isNative
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.modules.address.AddressHandlerTon
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.multiswap.ui.DataFieldSlippage
import cash.p.terminal.modules.send.SendModule
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.ton.SendTonAddressService
import cash.p.terminal.modules.send.ton.SendTonAmountService
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class SendTransactionServiceTonSwap(
    token: Token
) : ISendTransactionService<ISendTonAdapter>(token) {
    private val amountValidator = AmountValidator()

    private val amountService = SendTonAmountService(
        amountValidator = amountValidator,
        coinCode = wallet.coin.code,
        availableBalance = adapter.availableBalance,
        leaveSomeBalanceForFee = wallet.token.type.isNative
    )
    private val addressService = SendTonAddressService()

    private val addressHandlerTon = AddressHandlerTon()

    private val xRateService = XRateService(App.marketKit, App.currencyManager.baseCurrency)
    private val feeToken = requireNotNull(
        App.coinManager.getToken(TokenQuery(BlockchainType.Ton, TokenType.Native))) {
        "Ton fee token not found"
    }

    val blockchainType = wallet.token.blockchainType
    val feeTokenMaxAllowedDecimals = feeToken.decimals
    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value
    private var tonSwapData: SendTransactionData.TonSwap? = null
    private var routerMasterAddress: String? = null
    private var tonSwapQueryId: Long? = null
    private val ptonTransferFeeTon = BigDecimal("0.01")
    private val ptonTransferFeeNano =
        ptonTransferFeeTon.movePointRight(token.decimals).setScale(0, RoundingMode.UNNECESSARY)

    var coinRate by mutableStateOf(xRateService.getRate(token.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(feeToken.coin.uid))
        private set

    override fun createState() = SendTransactionServiceState(
        availableBalance = adapter.availableBalance,
        networkFee = feeAmountData,
        cautions = cautions,
        sendable = sendable,
        loading = loading,
        fields = fields
    )

    private fun handleUpdatedAmountState(amountState: SendTonAmountService.State) {
        this.amountState = amountState
        emitState()
    }

    private fun handleUpdatedAddressState(addressState: SendTonAddressService.State) {
        this.addressState = addressState
        emitState()
    }

    private fun updatedFee(fee: BigDecimal?) {
        fee?.let {
            val feeNative = fee.movePointLeft(feeToken.decimals).stripTrailingZeros()
            val primaryAmountInfo =
                SendModule.AmountInfo.CoinValueInfo(CoinValue(feeToken, feeNative))
            val secondaryAmountInfo = rate?.let {
                SendModule.AmountInfo.CurrencyValueInfo(
                    CurrencyValue(
                        it.currency,
                        it.value * feeNative
                    )
                )
            }

            feeAmountData = SendModule.AmountData(primaryAmountInfo, secondaryAmountInfo)
        }
        loading = false
        emitState()
    }

    private val _sendTransactionSettingsFlow = MutableStateFlow(
        SendTransactionSettings.Common
    )
    override val sendTransactionSettingsFlow: StateFlow<SendTransactionSettings> =
        _sendTransactionSettingsFlow.asStateFlow()

    private var feeAmountData: SendModule.AmountData? = null
    private var cautions: List<CautionViewItem> = listOf()
    private val sendable: Boolean
        get() = amountState.canBeSend && addressState.canBeSend
    private var loading = true
    private var fields = listOf<DataField>()

    override fun start(coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.Default) {
            amountService.stateFlow.collect {
                handleUpdatedAmountState(it)
            }
        }
        coroutineScope.launch(Dispatchers.Default) {
            addressService.stateFlow.collect {
                handleUpdatedAddressState(it)
            }
        }
        coroutineScope.launch(Dispatchers.Default) {
            xRateService.getRateFlow(token.coin.uid).collect {
                coinRate = it
            }
        }
        coroutineScope.launch(Dispatchers.Default) {
            xRateService.getRateFlow(feeToken.coin.uid).collect {
                feeCoinRate = it
            }
        }
    }

    override suspend fun setSendTransactionData(data: SendTransactionData) {
        tonSwapData = checkNotNull(data as? SendTransactionData.TonSwap) {
            "SendTransactionData should be SendTransactionData.TonSwap"
        }

        routerMasterAddress = data.routerMasterAddress
        tonSwapQueryId = data.queryId

        loading = true // to show loading until fee is set
        emitState()

        fields = listOf(
            DataFieldSlippage(data.slippage),
        )

        // just to check if adapter is correct
        when {
            data.ptonWalletAddress != null -> addressService.setAddress(
                addressHandlerTon.parseAddress(data.ptonWalletAddress)
            )

            routerMasterAddress != null -> addressService.setAddress(
                addressHandlerTon.parseAddress(routerMasterAddress!!)
            )
        }

        // Set amount from swap data
        val amount = data.offerUnits.movePointLeft(token.decimals)
        amountService.setAmount(amount)

        updatedFee(data.forwardGas)
    }

    override suspend fun sendTransaction(mevProtectionEnabled: Boolean): SendTransactionResult {
        try {
            val tonSwapData = checkNotNull(tonSwapData) { "Nothing to send" }

            val destinationAddress = checkNotNull(tonSwapData.ptonWalletAddress) { "Destination address is missing" }

            val amount = tonSwapData.gasBudget
                ?: (tonSwapData.offerUnits + tonSwapData.forwardGas + ptonTransferFeeNano)
            adapter.sendWithPayload(
                amount = amount.toBigInteger(),
                address = destinationAddress,
                payload = tonSwapData.payload
            )

            return SendTransactionResult.Ton(SendResult.Sent())
        } catch (e: Throwable) {
            cautions = listOf(createCaution(e))
            emitState()
            throw e
        }
    }

    override fun hasSettings() = false

    @Composable
    override fun GetSettingsContent(navController: NavController) = Unit
}
