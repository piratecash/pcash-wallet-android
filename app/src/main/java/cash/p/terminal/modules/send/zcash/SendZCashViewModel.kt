package cash.p.terminal.modules.send.zcash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.OfflineZcashSignRequest
import cash.p.terminal.core.SignedOfflineZcashTransaction
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.PendingTransactionDraft
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.offline.OfflineSignCapableViewModel
import cash.p.terminal.modules.send.offline.OfflineSigningController
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.z.ecc.android.sdk.ext.collectWith
import io.grpc.StatusRuntimeException
import cash.p.terminal.modules.send.BaseSendViewModel
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.core.toHexReversed
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.net.UnknownHostException

@Suppress("LongParameterList")
class SendZCashViewModel(
    private val adapter: ISendZcashAdapter,
    wallet: Wallet,
    xRateService: XRateService,
    private val amountService: SendAmountService,
    private val addressService: SendZCashAddressService,
    private val memoService: SendZCashMemoService,
    private val contactsRepo: ContactsRepository,
    private val address: Address?,
    private val showAddressInput: Boolean,
    private val pendingRegistrar: PendingTransactionRegistrar,
    private val adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
    private val offlineTransactionPayloadEncoder: OfflineTransactionPayloadEncoder,
    private val offlineSignedTransactionRepository: OfflineSignedTransactionRepository,
) : BaseSendViewModel<SendZCashUiState>(wallet, adapterManager), OfflineSignCapableViewModel {
    data class OfflineSignResult(
        val signedTransaction: SignedOfflineZcashTransaction,
        val confirmationData: SendConfirmationData,
    )

    val blockchainType = wallet.token.blockchainType
    override val coinMaxAllowedDecimals = wallet.token.decimals
    override val feeCoinMaxAllowedDecimals = wallet.token.decimals
    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal
    val memoMaxLength by memoService::memoMaxLength

    @Suppress("UNCHECKED_CAST")
    private val offlineSignAdapter = adapter as? OfflineTransactionAdapter<SignedOfflineZcashTransaction>
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

    private val fee = adapter.fee
    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value
    private var memoState = memoService.stateFlow.value
    private var pendingTxId: String? = null

    override var coinRate by mutableStateOf(xRateService.getRate(wallet.coin.uid))
        private set
    var sendResult by mutableStateOf<SendResult?>(null)
        private set

    private val logger = AppLogger("Send-${wallet.coin.code}")

    private val decimalAmount: BigDecimal
        get() = amountState.amount ?: throw LocalizedException(R.string.send_error_amount_unavailable)

    private val destinationAddress: Address
        get() = addressState.address ?: throw LocalizedException(R.string.send_error_address_unavailable)

    val offlineSignSupported =
        offlineSignAdapter != null && wallet.account.type is AccountType.Mnemonic

    init {
        xRateService.getRateFlow(wallet.coin.uid).collectWith(viewModelScope) {
            coinRate = it
        }
        amountService.stateFlow.collectWith(viewModelScope) {
            handleUpdatedAmountState(it)
        }
        addressService.stateFlow.collectWith(viewModelScope) {
            handleUpdatedAddressState(it)
        }
        memoService.stateFlow.collectWith(viewModelScope) {
            handleUpdatedMemoState(it)
        }
        adapter.balanceUpdatedFlow.collectWith(viewModelScope) {
            updateAvailableBalance()
        }
        fee.collectWith(viewModelScope) {
            updateAvailableBalance()
        }
        viewModelScope.launch {
            addressService.setAddress(address)
        }
    }

    companion object {
        fun createCaution(error: Throwable) = when (error) {
            is UnknownHostException -> HSCaution(TranslatableString.ResString(R.string.Hud_Text_NoInternet))
            is LocalizedException -> HSCaution(TranslatableString.ResString(error.errorTextRes))
            else -> HSCaution(TranslatableString.PlainString(error.message ?: ""))
        }
    }

    override fun createState(): SendZCashUiState {
        val poison = isAddressSuspicious(addressState.address?.hex)
        return SendZCashUiState(
            fee = fee.value,
            availableBalance = amountState.availableBalance,
            addressError = addressState.addressError,
            amountCaution = amountState.amountCaution,
            memoIsAllowed = memoState.memoIsAllowed,
            canBeSend = amountState.canBeSend && addressState.canBeSend && (!poison || riskAccepted),
            showAddressInput = showAddressInput,
            address = addressState.address,
            isPoisonAddress = poison,
            riskAccepted = riskAccepted,
        )
    }

    private fun updateAvailableBalance() {
        amountService.updateAvailableBalance(adapterManager.getZcashAvailableToSend(wallet, adapter))
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

    fun onEnterMemo(memo: String) {
        memoService.setMemo(memo)
    }

    private fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState

        emitState()
    }

    private fun handleUpdatedAddressState(addressState: SendZCashAddressService.State) {
        this.addressState = addressState

        addressState.address?.hex?.let {
            memoService.setAddress(it)
        }

        emitState()
    }

    private fun handleUpdatedMemoState(memoState: SendZCashMemoService.State) {
        this.memoState = memoState

        emitState()
    }

    override fun getConfirmationData(): SendConfirmationData {
        val address = destinationAddress
        val contact = contactsRepo.getContactsFiltered(
            blockchainType,
            addressQuery = address.hex
        ).firstOrNull()
        return SendConfirmationData(
            amount = decimalAmount,
            fee = fee.value,
            address = address,
            contact = contact,
            coin = wallet.coin,
            feeCoin = wallet.coin,
            memo = memoState.memo
        )
    }

    fun onClickSend() {
        viewModelScope.launch {
            send()
        }
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
                OfflineZcashSignRequest(
                    amount = confirmationData.amount,
                    address = confirmationData.address.hex,
                    memo = confirmationData.memo.orEmpty(),
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
            feeToken = wallet.token,
        )
    }

    private suspend fun send() = withContext(dispatcherProvider.io) {
        val logger = logger.getScopedUnique()
        logger.info("click send button")

        try {
            sendResult = SendResult.Sending
            // 1. Create pending transaction draft BEFORE sending
            val sdkBalance = adapterManager.getZcashSdkBalance(wallet, amountState.availableBalance)
            val amount = decimalAmount
            val address = destinationAddress
            val draft = PendingTransactionDraft(
                wallet = wallet,
                token = wallet.token,
                amount = amount,
                fee = fee.value,
                sdkBalanceAtCreation = sdkBalance,
                fromAddress = "",  // ZCash doesn't require from address
                toAddress = address.hex,
                memo = memoState.memo,
                txHash = null  // ZCash doesn't return hash immediately
            )

            // 2. Register pending transaction
            pendingTxId = pendingRegistrar.register(draft)

            // 3. Broadcast transaction
            val txId = adapter.send(
                amount,
                address.hex,
                memoState.memo,
                logger
            )
            pendingTxId?.let {
                pendingRegistrar.updateTxId(it, txId.byteArray.toHexReversed())
            }

            onSendSuccess(address.hex)
            sendResult = SendResult.Sent(txId.byteArray.toHexReversed())
            logger.info("success")
        } catch (e: StatusRuntimeException) {
            // Delete pending transaction on error
            pendingTxId?.let {
                pendingRegistrar.deleteFailed(it)
            }
            logger.warning("failed", e)
            sendResult =
                SendResult.Failed(HSCaution(TranslatableString.ResString(R.string.transaction_error_need_to_check)))
        } catch (e: Throwable) {
            // Delete pending transaction on error
            pendingTxId?.let {
                pendingRegistrar.deleteFailed(it)
            }
            logger.warning("failed", e)
            sendResult = SendResult.Failed(createCaution(e))
        }
    }
}

data class SendZCashUiState(
    val fee: BigDecimal,
    val availableBalance: BigDecimal,
    val addressError: Throwable?,
    val amountCaution: HSCaution?,
    val memoIsAllowed: Boolean,
    val canBeSend: Boolean,
    val showAddressInput: Boolean,
    val address: Address?,
    val isPoisonAddress: Boolean = false,
    val riskAccepted: Boolean = false,
)
