package cash.p.terminal.modules.send.stellar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendStellarAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineStellarSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineStellarTransaction
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.toResString
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.OfflineStellarRetryMetadata
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.BaseSendViewModel
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.offline.OfflineSignCapableViewModel
import cash.p.terminal.modules.send.offline.OfflineSigningController
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.net.UnknownHostException

@Suppress("LongParameterList")
class SendStellarViewModel(
    wallet: Wallet,
    private val sendToken: Token,
    override val feeToken: Token,
    private val adapter: ISendStellarAdapter,
    override val coinMaxAllowedDecimals: Int,
    private val xRateService: XRateService,
    address: Address?,
    private val showAddressInput: Boolean,
    private val amountService: SendAmountService,
    private val addressService: SendStellarAddressService,
    private val contactsRepo: ContactsRepository,
    private val minimumAmountService: SendStellarMinimumAmountService,
    adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
    private val recentAddressManager: RecentAddressManager,
    private val offlineTransactionPayloadEncoder: OfflineTransactionPayloadEncoder,
    private val offlineSignedTransactionRepository: OfflineSignedTransactionRepository,
) : BaseSendViewModel<SendStellarUiState>(wallet, adapterManager), OfflineSignCapableViewModel {
    data class OfflineSignResult(
        val signedTransaction: SignedOfflineStellarTransaction,
        val confirmationData: SendConfirmationData,
    )

    private val fee = adapter.sendFee

    val blockchainType = wallet.token.blockchainType
    val feeTokenMaxAllowedDecimals = feeToken.decimals
    override val feeCoinMaxAllowedDecimals get() = feeTokenMaxAllowedDecimals
    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal

    @Suppress("UNCHECKED_CAST")
    private val offlineSignAdapter = adapter as? OfflineTransactionAdapter<SignedOfflineStellarTransaction>
    override val offlineSigningController: OfflineSigningController<OfflineSignResult> by lazy {
        OfflineSigningController(
            scope = viewModelScope,
            dispatcherProvider = dispatcherProvider,
            payloadEncoder = offlineTransactionPayloadEncoder,
            repository = offlineSignedTransactionRepository,
            cautionFactory = ::createCaution,
            isSilentCancellation = { it is TrezorCancelledException || it.isHardwareWalletUserCancelled() },
        )
    }

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value
    private var minimumAmountState = minimumAmountService.stateFlow.value
    private var memo: String? = null

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

    private val logger: AppLogger = AppLogger("send-stellar")

    override fun getEstimatedFee(): BigDecimal = fee
    override fun onSendRequested() = onClickSend()

    init {
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
            minimumAmountService.stateFlow.collect {
                handleUpdatedMinimumAmountState(it)
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

    override fun createState(): SendStellarUiState {
        val poison = isAddressSuspicious(addressState.address?.hex)
        return SendStellarUiState(
            availableBalance = amountState.availableBalance,
            amountCaution = amountState.amountCaution,
            addressError = addressState.addressError,
            minimumAmountError = minimumAmountState.error,
            canBeSend = amountState.canBeSend && addressState.canBeSend && minimumAmountState.canBeSend && (!poison || riskAccepted),
            showAddressInput = showAddressInput,
            fee = fee,
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

    fun onEnterMemo(memo: String) {
        this.memo = memo.ifBlank { null }
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
            feeCoin = feeToken.coin,
            memo = memo,
        )
    }

    fun onClickSend() {
        logger.info("click send button")

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

    private suspend fun send() = withContext(dispatcherProvider.io) {
        try {
            sendResult = SendResult.Sending
            logger.info("sending tx")

            val address = destinationAddress
            val txHash = adapter.send(decimalAmount, address.hex, memo)
            locallyCreatedTransactionRepository.markCreated(wallet, txHash)

            onSendSuccess(address.hex)
            sendResult = SendResult.Sent(txHash)
            logger.info("success")

            recentAddressManager.setRecentAddress(address, BlockchainType.Stellar)
        } catch (error: TrezorCancelledException) {
            sendResult = null
            logger.info("user cancelled")
        } catch (error: Throwable) {
            if (error.isHardwareWalletUserCancelled()) {
                sendResult = null
                logger.info("user cancelled")
                return@withContext
            }
            sendResult = SendResult.Failed(createCaution(error))
            logger.warning("failed", error)
        }
    }

    private suspend fun signedOfflineTransaction(): OfflineSignResult {
        val confirmationData = getConfirmationData()
        val signingAdapter = offlineSignAdapter
            ?: throw LocalizedException(R.string.offline_broadcast_unsupported_blockchain)
        return OfflineSignResult(
            signedTransaction = signingAdapter.signOffline(
                OfflineStellarSignRequest(
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
            feeToken = feeToken,
            stellarRetryMetadata = OfflineStellarRetryMetadata(
                sourceAccountId = signed.sourceAccountId,
                sequenceNumber = signed.sequenceNumber,
                validUntil = signed.validUntil,
            ),
        )
    }

    private fun createCaution(error: Throwable) = when (error) {
        is UnknownHostException -> HSCaution(TranslatableString.ResString(R.string.Hud_Text_NoInternet))
        is LocalizedException -> HSCaution(error.toResString())
        else -> HSCaution(TranslatableString.PlainString(error.message ?: ""))
    }

    private fun handleUpdatedMinimumAmountState(state: SendStellarMinimumAmountService.State) {
        minimumAmountState = state

        amountService.setMinimumSendAmount(minimumAmountState.minimumAmount)

        emitState()
    }

    private fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState

        emitState()
    }

    private suspend fun handleUpdatedAddressState(addressState: SendStellarAddressService.State) {
        this.addressState = addressState

        minimumAmountService.setValidAddress(addressState.validAddress)

        emitState()
    }
}

data class SendStellarUiState(
    val availableBalance: BigDecimal?,
    val amountCaution: HSCaution?,
    val addressError: Throwable?,
    val minimumAmountError: Throwable?,
    val canBeSend: Boolean,
    val showAddressInput: Boolean,
    val fee: BigDecimal?,
    val address: Address?,
    val isPoisonAddress: Boolean = false,
    val riskAccepted: Boolean = false,
)
