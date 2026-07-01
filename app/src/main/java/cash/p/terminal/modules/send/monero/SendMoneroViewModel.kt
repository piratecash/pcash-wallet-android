package cash.p.terminal.modules.send.monero

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.EvmError
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendMoneroAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineMoneroSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineMoneroTransaction
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.ethereum.toCautionViewItem
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.BaseSendViewModel
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendErrorInsufficientBalance
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.SendUiState
import cash.p.terminal.modules.send.offline.OfflineSignCapableViewModel
import cash.p.terminal.modules.send.offline.OfflineSigningController
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.getMaxSendableBalance
import cash.z.ecc.android.sdk.ext.collectWith
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.net.UnknownHostException

class SendMoneroViewModel(
    wallet: Wallet,
    val sendToken: Token,
    val adapter: ISendMoneroAdapter,
    xRateService: XRateService,
    private val amountService: SendAmountService,
    private val addressService: SendMoneroAddressService,
    override val coinMaxAllowedDecimals: Int,
    private val showAddressInput: Boolean,
    private val contactsRepo: ContactsRepository,
    private val connectivityManager: ConnectivityManager,
    private val address: Address?,
    private val adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
    private val offlineTransactionPayloadEncoder: OfflineTransactionPayloadEncoder,
    private val offlineSignedTransactionRepository: OfflineSignedTransactionRepository,
) : BaseSendViewModel<SendUiState>(wallet, adapterManager), OfflineSignCapableViewModel {
    data class OfflineSignResult(
        val signedTransaction: SignedOfflineMoneroTransaction,
        val confirmationData: SendConfirmationData,
    )

    val blockchainType = wallet.token.blockchainType
    val feeTokenMaxAllowedDecimals = sendToken.decimals
    override val feeCoinMaxAllowedDecimals get() = feeTokenMaxAllowedDecimals
    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal

    @Suppress("UNCHECKED_CAST")
    private val offlineSignAdapter = adapter as? OfflineTransactionAdapter<SignedOfflineMoneroTransaction>
    override val offlineSigningController: OfflineSigningController<OfflineSignResult> by lazy {
        OfflineSigningController(
            scope = viewModelScope,
            dispatcherProvider = dispatcherProvider,
            payloadEncoder = offlineTransactionPayloadEncoder,
            repository = offlineSignedTransactionRepository,
            cautionFactory = ::createCaution,
            isSilentCancellation = { false },
        )
    }

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value

    override var coinRate by mutableStateOf(xRateService.getRate(sendToken.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(sendToken.coin.uid))
        private set
    var sendResult by mutableStateOf<SendResult?>(null)
        private set

    var memo by mutableStateOf<String?>(null)
        private set
    var feeInProgress by mutableStateOf<Boolean>(false)
        private set
    var fee by mutableStateOf<BigDecimal?>(null)
        private set

    var cautions by mutableStateOf<List<CautionViewItem>>(emptyList())
        private set

    private val decimalAmount: BigDecimal
        get() = amountState.amount ?: throw LocalizedException(R.string.send_error_amount_unavailable)

    private val destinationAddress: Address
        get() = addressState.address ?: throw LocalizedException(R.string.send_error_address_unavailable)

    val offlineSignSupported = offlineSignAdapter != null && !wallet.account.isWatchAccount

    init {
        amountService.stateFlow.collectWith(viewModelScope) {
            handleUpdatedAmountState(it)
            recalculateFee()
        }
        addressService.stateFlow.collectWith(viewModelScope) {
            handleUpdatedAddressState(it)
            recalculateFee()
        }
        adapter.balanceUpdatedFlow.collectWith(viewModelScope) {
            updateAvailableBalance()
        }
        xRateService.getRateFlow(sendToken.coin.uid).collectWith(viewModelScope) {
            coinRate = it
        }
        xRateService.getRateFlow(sendToken.coin.uid).collectWith(viewModelScope) {
            feeCoinRate = it
        }
        viewModelScope.launch {
            addressService.setAddress(address)
        }
    }

    override fun createState(): SendUiState {
        val poison = isAddressSuspicious(addressState.address?.hex)
        return SendUiState(
            availableBalance = amountState.availableBalance,
            addressError = addressState.addressError,
            amountCaution = amountState.amountCaution,
            canBeSend = amountState.canBeSend && addressState.canBeSend && (!poison || riskAccepted),
            showAddressInput = showAddressInput,
            address = addressState.address,
            cautions = cautions,
            isPoisonAddress = poison,
            riskAccepted = riskAccepted,
        )
    }

    fun onEnterAmount(amount: BigDecimal?) {
        amountService.setAmount(amount)
    }

    fun onEnterAddress(address: Address?) {
        resetRiskAccepted()
        addressService.setAddress(address)
    }

    override fun getConfirmationData(): SendConfirmationData {
        val address = destinationAddress
        val contact = contactsRepo.getContactsFiltered(
            blockchainType,
            addressQuery = address.hex
        ).firstOrNull()
        return SendConfirmationData(
            amount = decimalAmount,
            fee = fee,
            address = address,
            contact = contact,
            coin = wallet.coin,
            feeCoin = sendToken.coin,
            memo = memo,
        )
    }

    fun onEnterMemo(memoNew: String) {
        memo = memoNew.ifBlank { null }
        recalculateFee()
    }

    fun onClickSend() = viewModelScope.launch(dispatcherProvider.io) {
        if (!hasConnection()) {
            sendResult = SendResult.Failed(createCaution(UnknownHostException()))
            return@launch
        }

        try {
            sendResult = SendResult.Sending
            val address = destinationAddress.hex
            val amount = decimalAmount
            val fee = adapter.estimateFee(amount, address, memo)
            val totalAmount =
                (if (sendToken.type == TokenType.Native) amount else BigDecimal.ZERO) + fee

            val availableBalance = availableBalanceToSend()
            if (totalAmount > availableBalance)
                throw EvmError.InsufficientBalanceWithFee

            val txId = adapter.send(amount, address, memo)
            locallyCreatedTransactionRepository.markCreated(wallet, txId)

            onSendSuccess(address)
            sendResult = SendResult.Sent(txId)
        } catch (e: Throwable) {
            sendResult = SendResult.Failed(createCaution(e))
        }
    }

    private fun updateAvailableBalance() {
        amountService.updateAvailableBalance(availableBalanceToSend())
    }

    private fun availableBalanceToSend(): BigDecimal =
        adapterManager.getMaxSendableBalance(wallet, adapter.maxSpendableBalance)

    fun hasConnection(): Boolean {
        return connectivityManager.isConnected.value
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
                OfflineMoneroSignRequest(
                    amount = confirmationData.amount,
                    address = confirmationData.address.hex,
                    memo = confirmationData.memo,
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
            fee = signed.fee,
            toAddress = confirmationData.address.hex,
            rawHex = signed.rawHex,
            txHash = signed.txHash,
            inputOutpoints = emptyList(),
        )
    }

    private fun recalculateFee() {
        val address = addressState.address?.hex
        val amount = amountState.amount
        if (address == null || amount == null || amount == BigDecimal.ZERO) {
            cautions = emptyList()
            return
        }

        feeInProgress = true
        viewModelScope.launch(dispatcherProvider.default + CoroutineExceptionHandler { _, error ->
            fee = null
            cautions = listOf(createCaution(error).toCautionViewItem())
            feeInProgress = false
        }) {
            fee = adapter.estimateFee(amount, address, memo)
            cautions = emptyList()
            feeInProgress = false
        }
    }

    private fun createCaution(error: Throwable) = when (error) {
        is UnknownHostException -> HSCaution(TranslatableString.ResString(R.string.Hud_Text_NoInternet))
        is LocalizedException -> HSCaution(TranslatableString.ResString(error.errorTextRes))
        is EvmError.InsufficientBalanceWithFee -> SendErrorInsufficientBalance(sendToken.coin.code, amountState.availableBalance.toPlainString())
        else -> HSCaution(
            TranslatableString.PlainString(
                error.cause?.message ?: error.message ?: ""
            )
        )
    }

    private fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState

        emitState()
    }

    private fun handleUpdatedAddressState(addressState: SendMoneroAddressService.State) {
        this.addressState = addressState

        emitState()
    }

}
