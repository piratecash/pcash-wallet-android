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
import cash.p.terminal.modules.send.isHardwareWalletCancelled
import cash.p.terminal.modules.send.offline.OfflineSignCapableViewModel
import cash.p.terminal.modules.send.offline.OfflineSigningController
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.send.userMessageRes
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.getMaxSendableBalance
import cash.z.ecc.android.sdk.ext.collectWith
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

    // Monero needs the daemon (ring decoys) to BUILD the transaction, so build+sign it up front while
    // online and cache it to be returned when signing offline. Rebuilt on every input change.
    private var offlineSignJob: Job? = null
    private var offlineSignResult: OfflineSignResult? = null

    override var coinRate by mutableStateOf(xRateService.getRate(sendToken.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(sendToken.coin.uid))
        private set
    override var sendResult by mutableStateOf<SendResult?>(null)
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

    override val offlineSignSupported =
        offlineSignAdapter != null &&
            !wallet.account.isWatchAccount &&
            wallet.account.type !is AccountType.TrezorDevice

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
        // Reconnecting must re-prepare the offline transaction even if the inputs did not change,
        // otherwise the "prepare online, then sign offline" instruction never takes effect.
        isConnectedFlow.collectWith(viewModelScope) { connected ->
            // StateFlow only re-emits on a real transition, so a reconnect drives a fresh prepare;
            // prepareOfflineSignedTransaction cancels any in-flight (superseded) build safely.
            if (connected && offlineSignResult == null) {
                prepareOfflineSignedTransaction()
            }
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

    fun onClickSend() {
        sendResult = SendResult.Sending
        viewModelScope.launch(dispatcherProvider.io) { send() }
    }

    private suspend fun send() {
        if (!hasConnection()) {
            sendResult = SendResult.Failed(createCaution(UnknownHostException()))
            return
        }

        try {
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
            sendResult = if (e.isHardwareWalletCancelled()) {
                null
            } else {
                SendResult.Failed(createCaution(e))
            }
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

    // Reactively build+sign while online (needs the daemon for ring decoys) so an offline sign just
    // returns the cached result. Rebuilds on every input change; a Monero tx has no expiration, so no
    // block/sync invalidation is needed.
    private fun prepareOfflineSignedTransaction() {
        offlineSignJob?.cancel()
        offlineSignResult = null
        if (!offlineSignSupported) return
        val amount = amountState.amount
        if (amount == null || amount <= BigDecimal.ZERO || addressState.address == null || !hasConnection()) return
        offlineSignJob = viewModelScope.launch {
            delay(OFFLINE_BUILD_DEBOUNCE_MS)
            val result = try {
                buildOfflineSignedTransaction()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            // cancel() is cooperative: a superseded build must not overwrite a newer result.
            ensureActive()
            offlineSignResult = result
        }
    }

    private suspend fun buildOfflineSignedTransaction(): OfflineSignResult {
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

    private suspend fun signedOfflineTransaction(): OfflineSignResult {
        offlineSignJob?.join()
        return offlineSignResult
            ?: throw LocalizedException(R.string.offline_transaction_anchor_required)
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
        prepareOfflineSignedTransaction()
        val address = addressState.address?.hex
        val amount = amountState.amount
        if (address == null || amount == null || amount == BigDecimal.ZERO) {
            cautions = emptyList()
            return
        }

        feeInProgress = true
        viewModelScope.launch(dispatcherProvider.default + CoroutineExceptionHandler { _, error ->
            fee = null
            cautions = if (error.isHardwareWalletCancelled()) {
                emptyList()
            } else {
                listOf(createCaution(error).toCautionViewItem())
            }
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
        is HardwareWalletOperationException ->
            HSCaution(TranslatableString.ResString(error.userMessageRes()))
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

private const val OFFLINE_BUILD_DEBOUNCE_MS = 600L
