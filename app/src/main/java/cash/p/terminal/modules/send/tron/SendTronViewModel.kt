package cash.p.terminal.modules.send.tron

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import io.horizontalsystems.core.logger.AppLogger
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.OfflineTronSignRequest
import cash.p.terminal.core.SignedOfflineTronTransaction
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.OfflineTronRetryMetadata
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.BaseSendViewModel
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.offline.OfflineSignCapableViewModel
import cash.p.terminal.modules.send.offline.OfflineSigningController
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import com.tangem.common.extensions.isZero
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tronkit.transaction.Fee
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.net.UnknownHostException
import io.horizontalsystems.tronkit.models.Address as TronAddress

@Suppress("LongParameterList")
class SendTronViewModel(
    wallet: Wallet,
    private val sendToken: Token,
    override val feeToken: Token,
    private val adapter: ISendTronAdapter,
    private val xRateService: XRateService,
    private val amountService: SendAmountService,
    private val addressService: SendTronAddressService,
    override val coinMaxAllowedDecimals: Int,
    private val contactsRepo: ContactsRepository,
    private val showAddressInput: Boolean,
    private val connectivityManager: ConnectivityManager,
    private val address: Address?,
    adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
    private val recentAddressManager: RecentAddressManager,
    private val offlineTransactionPayloadEncoder: OfflineTransactionPayloadEncoder,
    private val offlineSignedTransactionRepository: OfflineSignedTransactionRepository,
) : BaseSendViewModel<SendUiState>(wallet, adapterManager), OfflineSignCapableViewModel {
    data class OfflineSignResult(
        val signedTransaction: SignedOfflineTronTransaction,
        val confirmationData: SendConfirmationData,
    )

    val logger: AppLogger = AppLogger("send-tron")

    val blockchainType = wallet.token.blockchainType
    val feeTokenMaxAllowedDecimals = feeToken.decimals
    override val feeCoinMaxAllowedDecimals get() = feeTokenMaxAllowedDecimals
    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal

    @Suppress("UNCHECKED_CAST")
    private val offlineSignAdapter = adapter as? OfflineTransactionAdapter<SignedOfflineTronTransaction>
    override val offlineSigningController: OfflineSigningController<OfflineSignResult> by lazy {
        OfflineSigningController(
            scope = viewModelScope,
            dispatcherProvider = dispatcherProvider,
            payloadEncoder = offlineTransactionPayloadEncoder,
            repository = offlineSignedTransactionRepository,
            cautionFactory = ::createCaution,
            isSilentCancellation = { it.isHardwareWalletUserCancelled() },
        )
    }

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value
    private var feeState: FeeState = FeeState.Loading
    private var hasEnoughFeeBalance: Boolean = true
    private var cautions: List<HSCaution> = listOf()
    private var currentFee: BigDecimal? = null
    private var feeLoading: Boolean = false
    private var feeEstimationJob: Job? = null

    override var coinRate by mutableStateOf(xRateService.getRate(sendToken.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(feeToken.coin.uid))
        private set
    var confirmationData by mutableStateOf<SendTronConfirmationData?>(null)
        private set
    var sendResult by mutableStateOf<SendResult?>(null)
        private set

    val offlineSignSupported = offlineSignAdapter != null && !wallet.account.isWatchAccount

    private val decimalAmount: BigDecimal
        get() = amountState.amount ?: throw LocalizedException(R.string.send_error_amount_unavailable)

    private val destinationAddress: Address
        get() = addressState.address ?: throw LocalizedException(R.string.send_error_address_unavailable)

    private val destinationTronAddress: TronAddress
        get() = addressState.tronAddress ?: throw LocalizedException(R.string.send_error_address_unavailable)

    override fun getEstimatedFee(): BigDecimal? = confirmationData?.fee
    override fun onSendRequested() = onClickSend()

    init {
        viewModelScope.launch {
            amountService.stateFlow.collect {
                handleUpdatedAmountState(it)
            }
        }
        viewModelScope.launch {
            addressService.stateFlow.collect {
                handleUpdatedAddressState(it)
            }
        }
        viewModelScope.launch {
            xRateService.getRateFlow(sendToken.coin.uid).collect {
                coinRate = it
            }
        }
        viewModelScope.launch {
            xRateService.getRateFlow(feeToken.coin.uid).collect {
                feeCoinRate = it
            }
        }
        viewModelScope.launch {
            addressService.setAddress(address)
        }
    }

    override fun createState(): SendUiState {
        val hasSendData = amountState.amount?.let { !it.isZero() } == true && addressState.address != null
        val poison = isAddressSuspicious(addressState.address?.hex)
        return SendUiState(
            availableBalance = amountState.availableBalance,
            amountCaution = amountState.amountCaution,
            addressError = addressState.addressError,
            proceedEnabled = amountState.canBeSend && addressState.canBeSend && (!poison || riskAccepted),
            sendEnabled = feeState is FeeState.Success && hasEnoughFeeBalance && cautions.isEmpty(),
            feeViewState = feeState.viewState,
            cautions = cautions,
            showAddressInput = showAddressInput,
            address = addressState.address,
            fee = currentFee,
            feeLoading = hasSendData && feeLoading,
            isPoisonAddress = poison,
            riskAccepted = riskAccepted,
        )
    }

    fun onEnterAmount(amount: BigDecimal?) {
        amountService.setAmount(amount)
    }

    fun onEnterAddress(address: Address?) {
        resetRiskAccepted()
        viewModelScope.launch {
            addressService.setAddress(address)
        }
    }

    fun onNavigateToConfirmation() {
        val address = destinationAddress

        confirmationData = SendTronConfirmationData(
            amount = decimalAmount,
            fee = null,
            activationFee = null,
            resourcesConsumed = null,
            address = address,
            contact = contact(address),
            coin = wallet.coin,
            feeCoin = feeToken.coin,
            isInactiveAddress = addressState.isInactiveAddress
        )

        viewModelScope.launch {
            estimateFee()
            validateBalance()
        }
    }

    private fun validateBalance() {
        val confirmationData = confirmationData ?: return
        val trxAmount = if (sendToken == feeToken) confirmationData.amount else BigDecimal.ZERO
        val totalFee = confirmationData.fee ?: return
        val availableBalance = adapter.trxBalanceData.available

        hasEnoughFeeBalance = trxAmount + totalFee <= availableBalance

        cautions = if (sendToken == feeToken && confirmationData.amount <= BigDecimal.ZERO) {
            listOf(
                HSCaution(
                    TranslatableString.ResString(
                        R.string.Tron_ZeroAmountTrxNotAllowed,
                        sendToken.coin.code
                    )
                )
            )
        } else {
            listOf()
        }
        emitState()
    }

    private suspend fun estimateFee() {
        try {
            feeState = FeeState.Loading
            emitState()

            val amount = decimalAmount
            val fees = adapter.estimateFee(amount, destinationTronAddress)

            var activationFee: BigDecimal? = null
            var bandwidth: String? = null
            var energy: String? = null

            fees.forEach { fee ->
                when (fee) {
                    is Fee.AccountActivation -> {
                        activationFee = fee.feeInSuns.toBigDecimal().movePointLeft(feeToken.decimals)
                    }

                    is Fee.Bandwidth -> {
                        bandwidth = "${fee.points} Bandwidth"
                    }

                    is Fee.Energy -> {
                        val formattedEnergy = App.numberFormatter.formatNumberShort(fee.required.toBigDecimal(), 0)
                        energy = "$formattedEnergy Energy"
                    }
                }
            }

            val resourcesConsumed = if (bandwidth != null) {
                bandwidth + (energy?.let { " \n + $it" } ?: "")
            } else {
                energy
            }

            feeState = FeeState.Success(fees)
            emitState()

            val fee = fees.totalFee(feeToken.decimals)

            confirmationData = confirmationData?.copy(
                amount = amount,
                fee = fee,
                activationFee = activationFee,
                resourcesConsumed = resourcesConsumed
            )
        } catch (error: Throwable) {
            logger.warning("estimate error", error)

            cautions = listOf(createCaution(error))
            feeState = FeeState.Error(error)
            emitState()

            confirmationData = confirmationData?.copy(fee = null, activationFee = null, resourcesConsumed = null)
        }
    }

    private fun onClickSend() {
        logger.info("click send button")

        viewModelScope.launch {
            send()
        }
    }

    fun hasConnection(): Boolean {
        return connectivityManager.isConnected.value
    }

    private suspend fun send() = withContext(dispatcherProvider.io) {
        try {
            val confirmationData = confirmationData ?: return@withContext
            sendResult = SendResult.Sending
            logger.info("sending tx")

            val amount = confirmationData.amount
            val address = destinationAddress
            val txId = adapter.send(amount, destinationTronAddress, feeLimitForTransaction())
            locallyCreatedTransactionRepository.markCreated(wallet, txId)

            onSendSuccess(address.hex)
            sendResult = SendResult.Sent(txId)
            logger.info("success")

            recentAddressManager.setRecentAddress(address, BlockchainType.Tron)
        } catch (e: Throwable) {
            if (e.isHardwareWalletUserCancelled()) {
                sendResult = null
                logger.info("user cancelled")
                return@withContext
            }
            sendResult = SendResult.Failed(createCaution(e))
            logger.warning("failed", e)
        }
    }

    override fun getConfirmationData(): SendConfirmationData {
        val address = destinationAddress
        return SendConfirmationData(
            amount = decimalAmount,
            fee = confirmationData?.fee ?: currentFee,
            address = address,
            contact = contact(address),
            coin = wallet.coin,
            feeCoin = feeToken.coin,
            memo = null,
        )
    }

    override fun onClickSignOffline(format: OfflineTransactionFormat) {
        offlineSigningController.sign(
            format = format,
            producer = ::signedOfflineTransaction,
            draftBuilder = ::offlineSignedTransactionDraft,
        )
    }

    private suspend fun signedOfflineTransaction(): OfflineSignResult {
        val confirmationData = getConfirmationData()
        val signingAdapter = offlineSignAdapter
            ?: throw LocalizedException(R.string.offline_broadcast_unsupported_blockchain)
        return OfflineSignResult(
            signedTransaction = signingAdapter.signOffline(
                OfflineTronSignRequest(
                    amount = confirmationData.amount,
                    address = destinationTronAddress,
                    feeLimit = feeLimitForTransaction(),
                )
            ),
            confirmationData = confirmationData,
        )
    }

    private fun offlineSignedTransactionDraft(result: OfflineSignResult): OfflineSignedTransactionDraft {
        val confirmationData = result.confirmationData
        val signed = result.signedTransaction
        return OfflineSignedTransactionDraft(
            wallet = wallet,
            amount = confirmationData.amount,
            fee = confirmationData.fee,
            toAddress = confirmationData.address.hex,
            rawHex = signed.rawHex,
            txHash = signed.txHash,
            inputOutpoints = emptyList(),
            feeToken = feeToken,
            tronRetryMetadata = OfflineTronRetryMetadata(signed.expiration),
        )
    }

    private suspend fun feeLimitForTransaction(): Long? =
        if (sendToken.type == TokenType.Native) {
            null
        } else {
            feeState.feeLimit ?: adapter.estimateFee(decimalAmount, destinationTronAddress).feeLimit
                ?: throw LocalizedException(R.string.send_error_fee_rate_unavailable)
        }

    private fun contact(address: Address) =
        contactsRepo.getContactsFiltered(
            blockchainType = blockchainType,
            addressQuery = address.hex
        ).firstOrNull()

    private fun createCaution(error: Throwable) = when (error) {
        is UnknownHostException -> HSCaution(TranslatableString.ResString(R.string.Hud_Text_NoInternet))
        is LocalizedException -> HSCaution(TranslatableString.ResString(error.errorTextRes))
        else -> HSCaution(TranslatableString.PlainString(error.message ?: ""))
    }

    private fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState
        estimateFeeIfReady()
    }

    private fun handleUpdatedAddressState(addressState: SendTronAddressService.State) {
        this.addressState = addressState
        estimateFeeIfReady()
    }

    private fun estimateFeeIfReady() {
        val amount = amountState.amount
        val address = addressState.address
        if (amount == null || amount.isZero() || address == null) {
            feeEstimationJob?.cancel()
            currentFee = null
            feeState = FeeState.Loading
            feeLoading = false
            emitState()
            return
        }

        feeEstimationJob?.cancel()
        feeEstimationJob = viewModelScope.launch {
            feeState = FeeState.Loading
            feeLoading = true
            emitState()
            try {
                val tronAddress = TronAddress.fromBase58(address.hex)
                val fees = adapter.estimateFee(amount, tronAddress)
                currentFee = fees.totalFee(feeToken.decimals)
                feeState = FeeState.Success(fees)
            } catch (error: Throwable) {
                currentFee = null
                feeState = FeeState.Error(error)
            }
            feeLoading = false
            emitState()
        }
    }
}

sealed class FeeState {
    object Loading : FeeState()
    data class Success(val fees: List<Fee>) : FeeState()
    data class Error(val error: Throwable) : FeeState()

    val viewState: ViewState
        get() = when (this) {
            is Error -> ViewState.Error(error)
            Loading -> ViewState.Loading
            is Success -> ViewState.Success
        }

    val feeLimit: Long?
        get() = when (this) {
            is Error -> null
            Loading -> null
            is Success -> {
                fees.feeLimit
            }
        }
}

private val List<Fee>.feeLimit: Long?
    get() = (find { it is Fee.Energy } as? Fee.Energy)?.feeInSuns

private fun List<Fee>.totalFee(decimals: Int): BigDecimal =
    sumOf { it.feeInSuns }.toBigInteger().toBigDecimal().movePointLeft(decimals)
