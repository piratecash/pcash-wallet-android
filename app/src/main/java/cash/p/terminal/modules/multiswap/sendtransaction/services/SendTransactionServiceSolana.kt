package cash.p.terminal.modules.multiswap.sendtransaction.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import cash.p.terminal.core.App
import cash.p.terminal.core.EvmError
import cash.p.terminal.core.ISendSolanaAdapter
import cash.p.terminal.core.adapters.SolanaAdapter
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.ethereum.toCautionViewItem
import cash.p.terminal.core.isNative
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.entities.PendingTransactionDraft
import org.koin.java.KoinJavaComponent.inject
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
import cash.p.terminal.modules.send.solana.SendSolanaAddressService
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.z.ecc.android.sdk.ext.collectWith
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.solanakit.SolanaKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.RoundingMode

class SendTransactionServiceSolana(
    token: Token
) : ISendTransactionService<ISendSolanaAdapter>(token) {

    private val coinMaxAllowedDecimals = wallet.token.decimals

    private val adjustedAvailableBalance: BigDecimal
        get() = adapterManager.getAdjustedBalanceData(wallet)?.available ?: adapter.maxSpendableBalance

    private val amountService = SendAmountService(
        amountValidator = AmountValidator(),
        coinCode = wallet.token.coin.code,
        availableBalance = adjustedAvailableBalance.setScale(
            coinMaxAllowedDecimals,
            RoundingMode.DOWN
        ),
        leaveSomeBalanceForFee = wallet.token.type.isNative
    )
    private val solToken =
        App.coinManager.getToken(TokenQuery(BlockchainType.Solana, TokenType.Native))
            ?: throw IllegalArgumentException()
    private val balance = App.solanaKitManager.solanaKitWrapper?.solanaKit?.balance ?: 0L
    private val solBalance =
        SolanaAdapter.balanceInBigDecimal(balance, solToken.decimals) - SolanaKit.accountRentAmount
    private val addressService = SendSolanaAddressService()
    private val xRateService = XRateService(App.marketKit, App.currencyManager.baseCurrency)
    private val pendingRegistrar: PendingTransactionRegistrar by inject(PendingTransactionRegistrar::class.java)

    val blockchainType = wallet.token.blockchainType
    val feeTokenMaxAllowedDecimals = token.decimals
    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value

    private var fee = SolanaKit.fee
    private var rawTransaction: ByteArray? = null
    private var rawTransactionAddress: String? = null
    private var rawTransactionAmount: BigDecimal? = null

    var coinRate by mutableStateOf(xRateService.getRate(token.coin.uid))
        private set
    private val decimalAmount: BigDecimal
        get() = amountState.amount!!

    private var cautions: List<CautionViewItem> = listOf()
    private var sendable = false
    private var loading = true
    private var fields = listOf<DataField>()

    private val _sendTransactionSettingsFlow = MutableStateFlow(
        SendTransactionSettings.Common
    )
    override val sendTransactionSettingsFlow: StateFlow<SendTransactionSettings> =
        _sendTransactionSettingsFlow.asStateFlow()

    private val feeAmountData: SendModule.AmountData by lazy {
        val coinValue = CoinValue(feeToken, SolanaKit.fee)
        val primaryAmountInfo = SendModule.AmountInfo.CoinValueInfo(coinValue)
        val secondaryAmountInfo = rate?.let {
            SendModule.AmountInfo.CurrencyValueInfo(
                CurrencyValue(
                    it.currency,
                    it.value * SolanaKit.fee
                )
            )
        }

        SendModule.AmountData(primaryAmountInfo, secondaryAmountInfo)
    }

    override fun createState() = SendTransactionServiceState(
        availableBalance = adjustedAvailableBalance,
        networkFee = feeAmountData,
        cautions = cautions,
        sendable = sendable,
        loading = loading,
        fields = fields
    )

    override fun start(coroutineScope: CoroutineScope) {
        amountService.stateFlow.collectWith(coroutineScope) {
            handleUpdatedAmountState(it)
        }
        addressService.stateFlow.collectWith(coroutineScope) {
            handleUpdatedAddressState(it)
        }
        xRateService.getRateFlow(token.coin.uid).collectWith(coroutineScope) {
            coinRate = it
        }
    }

    override fun hasSettings() = false

    @Composable
    override fun GetSettingsContent(navController: NavController) = Unit

    override suspend fun setSendTransactionData(data: SendTransactionData) {
        when (data) {
            is SendTransactionData.Solana.Regular -> {
                addressService.setAddress(
                    Address(
                        hex = data.address,
                        blockchainType = blockchainType
                    )
                )
                amountService.setAmount(data.amount)
            }

            is SendTransactionData.Solana.WithRawTransaction -> {
                val rawTransaction = data.rawTransactionStr.hexToByteArray()
                this.rawTransaction = rawTransaction
                this.rawTransactionAddress = data.rawTransactionAddress
                this.rawTransactionAmount = data.rawTransactionAmount

                fee = adapter.estimateFee(rawTransaction)
            }

            else -> {
                throw IllegalArgumentException("Unsupported data type: ${data::class.java}")
            }
        }

        emitState()
    }

    private fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState
        sendable = amountState.canBeSend
        cautions = amountState.amountCaution?.toCautionViewItem()?.let { listOf(it) } ?: listOf()
        loading = false
        emitState()
    }

    private fun handleUpdatedAddressState(addressState: SendSolanaAddressService.State) {
        this.addressState = addressState

        emitState()
    }

    override suspend fun sendTransaction(mevProtectionEnabled: Boolean): SendTransactionResult {
        private var pendingTxId: String? = null
        try {
            val sdkBalance = adapterManager.getBalanceAdapterForWallet(wallet)
                ?.balanceData?.available ?: adjustedAvailableBalance
            val fromAddress = adapterManager.getReceiveAdapterForWallet(wallet)?.receiveAddress ?: ""

            val tmpRawTransaction = rawTransaction
            val transaction = if (tmpRawTransaction != null) {
                val draft = PendingTransactionDraft(
                    wallet = wallet,
                    token = wallet.token,
                    amount = rawTransactionAmount ?: fee,
                    fee = fee,
                    sdkBalanceAtCreation = sdkBalance,
                    fromAddress = fromAddress,
                    toAddress = rawTransactionAddress.orEmpty(),
                    txHash = null
                )
                pendingTxId = pendingRegistrar.register(draft)

                adapter.send(tmpRawTransaction)
            } else {
                val totalSolAmount =
                    (if (token.type == TokenType.Native) decimalAmount else BigDecimal.ZERO) + SolanaKit.fee

                if (totalSolAmount > solBalance)
                    throw EvmError.InsufficientBalanceWithFee

                val draft = PendingTransactionDraft(
                    wallet = wallet,
                    token = wallet.token,
                    amount = decimalAmount,
                    fee = SolanaKit.fee,
                    sdkBalanceAtCreation = sdkBalance,
                    fromAddress = fromAddress,
                    toAddress = addressState.solanaAddress?.publicKey?.toString() ?: "",
                    txHash = null
                )
                pendingTxId = pendingRegistrar.register(draft)

                adapter.send(decimalAmount, addressState.solanaAddress!!)
            }

            pendingTxId?.let { pendingRegistrar.updateTxId(it, transaction.transaction.hash) }

            return SendTransactionResult.Solana(SendResult.Sent(transaction.transaction.hash))
        } catch (e: Throwable) {
            pendingTxId?.let {
                pendingRegistrar.deleteFailed(it)
            }

            cautions = listOf(createCaution(e))
            emitState()
            throw e
        }
    }
}
