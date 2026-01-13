package cash.p.terminal.modules.multiswap.sendtransaction.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.ISendMoneroAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.entities.PendingTransactionDraft
import cash.p.terminal.modules.address.AddressHandlerMonero
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
import cash.p.terminal.modules.send.monero.SendMoneroAddressService
import cash.p.terminal.modules.send.monero.SendMoneroFeeService
import cash.p.terminal.modules.send.ton.FeeStatus
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

class SendTransactionServiceMonero(
    token: Token
) : ISendTransactionService<ISendMoneroAdapter>(token) {
    private val amountValidator = AmountValidator()

    private val adjustedAvailableBalance: BigDecimal
        get() = adapterManager.getAdjustedBalanceData(wallet)?.available
            ?: adapter.balanceData.available

    private val amountService = SendAmountService(
        amountValidator = amountValidator,
        coinCode = wallet.coin.code,
        availableBalance = adjustedAvailableBalance,
        leaveSomeBalanceForFee = true
    )

    private val addressService = SendMoneroAddressService()
    private val addressHandlerMonero = AddressHandlerMonero()
    private val feeService = SendMoneroFeeService(adapter, coroutineScope)
    private val xRateService = XRateService(App.marketKit, App.currencyManager.baseCurrency)

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value
    private var feeState = feeService.stateFlow.value
    private var memo: String? = null

    private val pendingRegistrar: PendingTransactionRegistrar by inject(PendingTransactionRegistrar::class.java)
    private var pendingTxId: String? = null

    var coinRate by mutableStateOf(xRateService.getRate(token.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(feeToken.coin.uid))
        private set

    private var feeWallet: Wallet? = null
    private val accountManager: IAccountManager by inject(IAccountManager::class.java)
    private var feeCaution: CautionViewItem? = null
    private var hasEnoughFeeAmount: Boolean = true

    override fun createState() = SendTransactionServiceState(
        availableBalance = adjustedAvailableBalance,
        networkFee = feeAmountData,
        cautions = cautions + listOfNotNull(feeCaution),
        sendable = sendable,
        loading = loading,
        fields = fields
    )

    private fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState
        feeService.setAmount(amountState.amount)
        emitState()
    }

    private fun handleUpdatedAddressState(addressState: SendMoneroAddressService.State) {
        this.addressState = addressState
        feeService.setAddress(addressState.address?.hex)
        emitState()
    }

    private fun handleUpdatedFeeState(feeState: SendMoneroFeeService.State) {
        this.feeState = feeState
        checkFeeBalance(feeState)

        if (feeState.feeStatus is FeeStatus.Success) {
            val primaryAmountInfo =
                SendModule.AmountInfo.CoinValueInfo(CoinValue(feeToken, feeState.feeStatus.fee))
            val secondaryAmountInfo = rate?.let {
                SendModule.AmountInfo.CurrencyValueInfo(
                    CurrencyValue(it.currency, it.value * feeState.feeStatus.fee)
                )
            }
            feeAmountData = SendModule.AmountData(primaryAmountInfo, secondaryAmountInfo)
        }
        loading = feeState.inProgress
        emitState()
    }

    private val _sendTransactionSettingsFlow = MutableStateFlow(SendTransactionSettings.Common)
    override val sendTransactionSettingsFlow: StateFlow<SendTransactionSettings> =
        _sendTransactionSettingsFlow.asStateFlow()

    private var feeAmountData: SendModule.AmountData? = null
    private var cautions: List<CautionViewItem> = listOf()
    private val sendable: Boolean
        get() = hasEnoughFeeAmount && amountState.canBeSend && addressState.canBeSend
    private var loading = true
    private var fields = listOf<DataField>()

    override fun start(coroutineScope: CoroutineScope) {
        coroutineScope.launch(Dispatchers.Default) {
            amountService.stateFlow.collect { handleUpdatedAmountState(it) }
        }
        coroutineScope.launch(Dispatchers.Default) {
            addressService.stateFlow.collect { handleUpdatedAddressState(it) }
        }
        coroutineScope.launch(Dispatchers.Default) {
            feeService.stateFlow.collect { handleUpdatedFeeState(it) }
        }
        coroutineScope.launch(Dispatchers.Default) {
            xRateService.getRateFlow(token.coin.uid).collect { coinRate = it }
        }
        coroutineScope.launch(Dispatchers.Default) {
            xRateService.getRateFlow(feeToken.coin.uid).collect { feeCoinRate = it }
        }

        coroutineScope.launch(Dispatchers.Default) {
            if (accountManager.activeAccount?.isHardwareWalletAccount == true) return@launch
            feeWallet = walletUseCase.createWalletIfNotExists(feeToken)?.also {
                adapterManager.awaitAdapterForWallet<ISendMoneroAdapter>(it)
            }
        }
    }

    override suspend fun setSendTransactionData(data: SendTransactionData) {
        check(data is SendTransactionData.Monero)
        amountService.setAmount(data.amount)
        addressService.setAddress(addressHandlerMonero.parseAddress(data.address))
    }

    override suspend fun sendTransaction(mevProtectionEnabled: Boolean): SendTransactionResult {
        try {
            val sdkBalance = adapterManager.getBalanceAdapterForWallet(wallet)
                ?.balanceData?.available ?: amountState.availableBalance
                ?: throw IllegalStateException("Balance unavailable")

            val draft = PendingTransactionDraft(
                wallet = wallet,
                token = token,
                amount = amountState.amount!!,
                fee = (feeState.feeStatus as? FeeStatus.Success)?.fee,
                sdkBalanceAtCreation = sdkBalance,
                fromAddress = "",
                toAddress = addressState.address!!.hex,
                memo = memo,
                txHash = null
            )

            pendingTxId = pendingRegistrar.register(draft)

            val txid = adapter.send(amountState.amount!!, addressState.address!!.hex, memo)

            pendingTxId?.let { pendingRegistrar.updateTxId(it, txid) }

            return SendTransactionResult.Monero(SendResult.Sent(recordUid = txid))
        } catch (e: Throwable) {
            pendingTxId?.let { pendingRegistrar.deleteFailed(it) }
            cautions = listOf(createCaution(e))
            emitState()
            throw e
        }
    }

    private fun checkFeeBalance(feeState: SendMoneroFeeService.State) {
        if (feeState.feeStatus !is FeeStatus.Success) {
            feeCaution = null
            hasEnoughFeeAmount = true
            return
        }
        val fee = feeState.feeStatus.fee
        val feeWalletLocal = feeWallet
        if (feeWalletLocal != null) {
            val availableBalance = adapterManager.getAdjustedBalanceData(feeWalletLocal)?.available
            if (availableBalance != null) {
                feeCaution = if (availableBalance < fee) {
                    createCaution(LocalizedException(R.string.check_fee_warning, fee.toPlainString() + " " + feeToken.coin.code))
                } else null
                hasEnoughFeeAmount = feeCaution == null
            }
        } else {
            val neededFee = fee.stripTrailingZeros().toPlainString() + " " + feeToken.coin.code
            feeCaution = createCaution(LocalizedException(R.string.check_fee_warning, neededFee))
            hasEnoughFeeAmount = false
        }
    }

    override fun hasSettings() = false

    @Composable
    override fun GetSettingsContent(navController: NavController) = Unit
}
