package cash.p.terminal.modules.multiswap.sendtransaction.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import cash.p.terminal.core.App
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.isNative
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.send.SendModule
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.tron.SendTronAddressService
import cash.p.terminal.modules.send.tron.SendTronFeeService
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.RoundingMode

class SendTransactionServiceTron(
    token: Token
) : ISendTransactionService<ISendTronAdapter>(token) {

    val amountValidator = AmountValidator()
    val coinMaxAllowedDecimals = wallet.token.decimals

    val amountService = SendAmountService(
        amountValidator = amountValidator,
        coinCode = wallet.token.coin.code,
        availableBalance = adapter.balanceData.available.setScale(
            coinMaxAllowedDecimals,
            RoundingMode.DOWN
        ),
        leaveSomeBalanceForFee = wallet.token.type.isNative
    )
    val addressService = SendTronAddressService(adapter, wallet.token)
    val xRateService = XRateService(App.marketKit, App.currencyManager.baseCurrency)
    val feeToken = App.coinManager.getToken(TokenQuery(BlockchainType.Tron, TokenType.Native))
        ?: throw IllegalArgumentException()
    private val feeService = SendTronFeeService(adapter, feeToken)

    val logger: AppLogger = AppLogger("send-tron")

    val blockchainType = wallet.token.blockchainType
    val feeTokenMaxAllowedDecimals = feeToken.decimals
    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal
    private var feeState = feeService.stateFlow.value

    private var networkFee: SendModule.AmountData? = null
    private var sendTransactionData: SendTransactionData.Tron? = null

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value

    private val _sendTransactionSettingsFlow = MutableStateFlow(
        SendTransactionSettings.Common
    )
    override val sendTransactionSettingsFlow: StateFlow<SendTransactionSettings> =
        _sendTransactionSettingsFlow.asStateFlow()


    var coinRate by mutableStateOf(xRateService.getRate(token.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(feeToken.coin.uid))
        private set

    private var cautions: List<CautionViewItem> = listOf()
    private var fields = listOf<DataField>()

    override fun createState() = SendTransactionServiceState(
        availableBalance = adapter.balanceData.available,
        networkFee = networkFee,
        cautions = cautions,
        sendable = sendTransactionData != null || (amountState.canBeSend && feeState.canBeSend && addressState.canBeSend),
        loading = false,
        fields = fields
    )

    override fun start(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            amountService.stateFlow.collect {
                handleUpdatedAmountState(it)
            }
        }
        coroutineScope.launch {
            addressService.stateFlow.collect {
                handleUpdatedAddressState(it)
            }
        }
        coroutineScope.launch {
            xRateService.getRateFlow(token.coin.uid).collect {
                coinRate = it
            }
        }
        coroutineScope.launch {
            xRateService.getRateFlow(feeToken.coin.uid).collect {
                feeCoinRate = it
            }
        }

        coroutineScope.launch {
            feeService.stateFlow.collect {
                handleUpdatedFeeState(it)
            }
        }
    }

    override suspend fun setSendTransactionData(data: SendTransactionData) {
        check(data is SendTransactionData.Tron)

        sendTransactionData = data

        when (data) {
            is SendTransactionData.Tron.WithContract -> {
                feeService.setContract(data.contract)
            }

            is SendTransactionData.Tron.WithCreateTransaction -> {
                feeService.setFeeLimit(data.transaction.raw_data.fee_limit)
            }

            is SendTransactionData.Tron.Regular -> {
                amountService.setAmount(data.amount)
                addressService.setAddress(Address(data.address))
            }
        }
        emitState()
    }

    override suspend fun sendTransaction(mevProtectionEnabled: Boolean): SendTransactionResult {
        val transactionId = try {
            when (val tmpSendTransactionData = sendTransactionData) {
                is SendTransactionData.Tron.WithContract -> {
                    adapter.send(tmpSendTransactionData.contract, feeState.feeLimit)
                }

                is SendTransactionData.Tron.WithCreateTransaction -> {
                    adapter.send(tmpSendTransactionData.transaction)
                }

                is SendTransactionData.Tron.Regular ->
                    adapter.send(
                        amount = amountState.amount!!,
                        to = addressState.tronAddress!!,
                        feeLimit = feeState.feeLimit
                    )

                null -> {
                    adapter.send(
                        amountState.amount!!,
                        addressState.tronAddress!!,
                        feeState.feeLimit
                    )
                }

            }
        } catch (e: Throwable) {
            cautions = listOf(createCaution(e))
            emitState()
            throw e
        }

        return SendTransactionResult.Tron(SendResult.Sent(transactionId))
    }

    private fun handleUpdatedFeeState(state: SendTronFeeService.State) {
        feeState = state

        networkFee = feeState.fee?.let {
            getAmountData(CoinValue(feeToken, it))
        }

        emitState()
    }

    private suspend fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState
        feeService.setAmount(amountState.amount)
        emitState()
    }

    private suspend fun handleUpdatedAddressState(addressState: SendTronAddressService.State) {
        this.addressState = addressState
        feeService.setTronAddress(addressState.tronAddress)
        emitState()
    }

    override fun hasSettings() = false

    @Composable
    override fun GetSettingsContent(navController: NavController) = Unit
}