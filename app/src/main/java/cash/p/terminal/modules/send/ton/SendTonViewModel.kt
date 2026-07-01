package cash.p.terminal.modules.send.ton

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import io.horizontalsystems.core.logger.AppLogger
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineTonSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineTonTransaction
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.send.BaseSendViewModel
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.OfflineTonRetryMetadata
import cash.p.terminal.entities.PendingTransactionDraft
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.offline.OfflineSignCapableViewModel
import cash.p.terminal.modules.send.offline.OfflineSigningController
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.net.UnknownHostException

@Suppress("LongParameterList")
class SendTonViewModel(
    wallet: Wallet,
    private val sendToken: Token,
    override val feeToken: Token,
    val adapter: ISendTonAdapter,
    private val xRateService: XRateService,
    private val amountService: SendTonAmountService,
    private val addressService: SendTonAddressService,
    private val feeService: SendTonFeeService,
    override val coinMaxAllowedDecimals: Int,
    private val contactsRepo: ContactsRepository,
    private val showAddressInput: Boolean,
    address: Address?,
    private val pendingRegistrar: PendingTransactionRegistrar,
    private val adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
    private val recentAddressManager: RecentAddressManager,
    private val offlineTransactionPayloadEncoder: OfflineTransactionPayloadEncoder,
    private val offlineSignedTransactionRepository: OfflineSignedTransactionRepository,
) : BaseSendViewModel<SendTonUiState>(wallet, adapterManager), OfflineSignCapableViewModel {
    data class OfflineSignResult(
        val signedTransaction: SignedOfflineTonTransaction,
        val confirmationData: SendConfirmationData,
    )

    val blockchainType = wallet.token.blockchainType
    val feeTokenMaxAllowedDecimals = feeToken.decimals
    override val feeCoinMaxAllowedDecimals get() = feeTokenMaxAllowedDecimals
    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal

    @Suppress("UNCHECKED_CAST")
    private val offlineSignAdapter = adapter as? OfflineTransactionAdapter<SignedOfflineTonTransaction>
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
    private var feeState = feeService.stateFlow.value
    private var memo: String? = null
    private var pendingTxId: String? = null

    override var coinRate by mutableStateOf(xRateService.getRate(sendToken.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(feeToken.coin.uid))
        private set
    var sendResult by mutableStateOf<SendResult?>(null)
        private set

    val offlineSignSupported = offlineSignAdapter != null && !wallet.account.isWatchAccount

    private val decimalAmount: BigDecimal
        get() = amountState.amount ?: throw LocalizedException(R.string.send_error_amount_unavailable)

    private val destinationAddress: Address
        get() = addressState.address ?: throw LocalizedException(R.string.send_error_address_unavailable)

    private val destinationTonAddress
        get() = addressState.tonAddress ?: throw LocalizedException(R.string.send_error_address_unavailable)

    override fun getEstimatedFee(): BigDecimal? = (feeState.feeStatus as? FeeStatus.Success)?.fee
    override fun onSendRequested() = onClickSend()

    private val logger: AppLogger = AppLogger("send-ton")

    init {
        addCloseable(feeService)

        viewModelScope.launch(dispatcherProvider.default) {
            amountService.stateFlow.collect {
                handleUpdatedAmountState(it)
            }
        }
        viewModelScope.launch(dispatcherProvider.default) {
            addressService.stateFlow.collect {
                handleUpdatedAddressState(it)
            }
        }
        viewModelScope.launch(dispatcherProvider.default) {
            feeService.stateFlow.collect {
                handleUpdatedFeeState(it)
            }
        }
        viewModelScope.launch(dispatcherProvider.default) {
            xRateService.getRateFlow(sendToken.coin.uid).collect {
                coinRate = it
            }
        }
        viewModelScope.launch(dispatcherProvider.default) {
            xRateService.getRateFlow(feeToken.coin.uid).collect {
                feeCoinRate = it
            }
        }

        addressService.setAddress(address)
    }

    override fun createState(): SendTonUiState {
        val poison = isAddressSuspicious(addressState.address?.hex)
        return SendTonUiState(
            availableBalance = amountState.availableBalance,
            amountCaution = amountState.amountCaution
                ?: if(feeState.feeStatus is FeeStatus.NoEnoughBalance) { HSCaution(
                    TranslatableString.ResString(
                        R.string.not_enough_ton_for_fee,
                        amountState.availableBalance?.toPlainString() ?: "0"
                    ),
                    HSCaution.Type.Error
                ) } else { null },
            addressError = addressState.addressError,
            canBeSend = (feeState.feeStatus is FeeStatus.Success) && amountState.canBeSend && addressState.canBeSend && (!poison || riskAccepted),
            showAddressInput = showAddressInput,
            fee = (feeState.feeStatus as? FeeStatus.Success)?.fee,
            feeInProgress = feeState.inProgress,
            address = addressState.address,
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
            fee = (feeState.feeStatus as? FeeStatus.Success)?.fee,
            address = address,
            contact = contact,
            coin = wallet.coin,
            feeCoin = feeToken.coin,
            memo = memo,
        )
    }

    private fun onClickSend() {
        logger.info("click send button")

        viewModelScope.launch {
            send()
        }
    }

    fun onEnterMemo(memo: String) {
        viewModelScope.launch(dispatcherProvider.default) {
            this@SendTonViewModel.memo = memo.ifBlank { null }
            amountService.setMemo(this@SendTonViewModel.memo)
            feeService.setMemo(this@SendTonViewModel.memo)
        }
    }

    override fun onClickSignOffline(format: OfflineTransactionFormat) {
        offlineSigningController.sign(
            format = format,
            producer = ::signedOfflineTransaction,
            draftBuilder = ::offlineSignedTransactionDraft,
        )
    }

    private suspend fun send() = withContext(dispatcherProvider.io) {
        try {
            sendResult = SendResult.Sending
            logger.info("sending tx")

            // 1. Create pending transaction draft BEFORE sending
            val sdkBalance = adapterManager.getBalanceAdapterForWallet(wallet)
                ?.balanceData?.available ?: amountState.availableBalance ?: throw IllegalStateException("Balance unavailable")
            val draft = PendingTransactionDraft(
                wallet = wallet,
                token = sendToken,
                amount = decimalAmount,
                fee = (feeState.feeStatus as? FeeStatus.Success)?.fee,
                sdkBalanceAtCreation = sdkBalance,
                fromAddress = "",  // TON doesn't require from address
                toAddress = destinationAddress.hex,
                memo = memo,
                txHash = null  // TON doesn't return hash immediately
            )

            // 2. Register pending transaction
            pendingTxId = pendingRegistrar.register(draft)

            // 3. Broadcast transaction
            adapter.send(decimalAmount, destinationTonAddress, memo)

            onSendSuccess(destinationAddress.hex)
            sendResult = SendResult.Sent()
            logger.info("success")

            recentAddressManager.setRecentAddress(destinationAddress, BlockchainType.Ton)
        } catch (e: Throwable) {
            // Delete pending transaction on error
            pendingTxId?.let {
                pendingRegistrar.deleteFailed(it)
            }
            if (e.isHardwareWalletUserCancelled()) {
                sendResult = null
                logger.info("user cancelled")
                return@withContext
            }
            sendResult = SendResult.Failed(createCaution(e))
            logger.warning("failed", e)
        }
    }

    private suspend fun signedOfflineTransaction(): OfflineSignResult {
        val confirmationData = getConfirmationData()
        val signingAdapter = offlineSignAdapter
            ?: throw LocalizedException(R.string.offline_broadcast_unsupported_blockchain)
        return OfflineSignResult(
            signedTransaction = signingAdapter.signOffline(
                OfflineTonSignRequest(
                    amount = confirmationData.amount,
                    address = destinationTonAddress,
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
            feeToken = feeToken,
            tonRetryMetadata = OfflineTonRetryMetadata(
                validUntil = signed.validUntil,
                senderAddress = signed.senderAddress,
                seqno = signed.seqno,
            ),
        )
    }

    private fun createCaution(error: Throwable) = when (error) {
        is UnknownHostException -> HSCaution(TranslatableString.ResString(R.string.Hud_Text_NoInternet))
        is LocalizedException -> HSCaution(TranslatableString.ResString(error.errorTextRes))
        else -> HSCaution(TranslatableString.PlainString(error.message ?: ""))
    }

    private fun handleUpdatedAmountState(amountState: SendTonAmountService.State) {
        this.amountState = amountState

        feeService.setAmount(amountState.amount)
        feeService.setMemo(amountState.memo)

        emitState()
    }

    private fun handleUpdatedAddressState(addressState: SendTonAddressService.State) {
        this.addressState = addressState

        feeService.run { setTonAddress(addressState.tonAddress) }

        emitState()
    }

    private fun handleUpdatedFeeState(feeState: SendTonFeeService.State) {
        this.feeState = feeState

        emitState()
    }

}

data class SendTonUiState(
    val availableBalance: BigDecimal?,
    val amountCaution: HSCaution?,
    val addressError: Throwable?,
    val canBeSend: Boolean,
    val showAddressInput: Boolean,
    val fee: BigDecimal?,
    val feeInProgress: Boolean,
    val address: Address?,
    val isPoisonAddress: Boolean = false,
    val riskAccepted: Boolean = false,
)
