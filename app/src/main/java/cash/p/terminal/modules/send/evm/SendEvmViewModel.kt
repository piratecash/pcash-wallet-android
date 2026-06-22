package cash.p.terminal.modules.send.evm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendEthereumAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineEvmTransaction
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceEvm
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.SendUiState
import cash.p.terminal.modules.send.offline.OfflineSignCapableViewModel
import cash.p.terminal.modules.send.offline.OfflineSigningController
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.z.ecc.android.sdk.ext.collectWith
import com.tangem.common.extensions.isZero
import cash.p.terminal.modules.send.BaseSendViewModel
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.horizontalsystems.ethereumkit.models.Address as EvmAddress
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.math.BigDecimal
import java.net.UnknownHostException

internal class SendEvmViewModel(
    wallet: Wallet,
    sendToken: Token,
    val adapter: ISendEthereumAdapter,
    private val sendTransactionService: SendTransactionServiceEvm,
    xRateService: XRateService,
    private val amountService: SendAmountService,
    private val addressService: SendEvmAddressService,
    override val coinMaxAllowedDecimals: Int,
    private val showAddressInput: Boolean,
    address: Address?,
    adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
) : BaseSendViewModel<SendUiState>(wallet, adapterManager), OfflineSignCapableViewModel {
    internal data class OfflineSignResult(
        val signedTransaction: SignedOfflineEvmTransaction,
        val confirmationData: SendConfirmationData,
    )

    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal
    val blockchainType = wallet.token.blockchainType

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value

    private val contactsRepository: ContactsRepository by inject(ContactsRepository::class.java)
    override val feeToken = getKoinInstance<EvmBlockchainManager>().getBaseToken(blockchainType)
        ?: throw IllegalArgumentException()
    val feeTokenMaxAllowedDecimals = feeToken.decimals
    override val feeCoinMaxAllowedDecimals get() = feeTokenMaxAllowedDecimals

    override var coinRate by mutableStateOf(xRateService.getRate(sendToken.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(feeToken.coin.uid))
        private set
    var sendResult by mutableStateOf<SendResult?>(null)
        private set

    @Suppress("UNCHECKED_CAST")
    private val offlineSignAdapter = adapter as? OfflineTransactionAdapter<SignedOfflineEvmTransaction>
    override val offlineSigningController: OfflineSigningController<OfflineSignResult> by lazy {
        OfflineSigningController(
            scope = viewModelScope,
            dispatcherProvider = dispatcherProvider,
            payloadEncoder = getKoinInstance<OfflineTransactionPayloadEncoder>(),
            repository = getKoinInstance<OfflineSignedTransactionRepository>(),
            cautionFactory = ::createCaution,
            isSilentCancellation = { it is TrezorCancelledException || it.isHardwareWalletUserCancelled() },
        )
    }

    val offlineSignSupported = offlineSignAdapter != null && !wallet.account.isWatchAccount

    init {
        amountService.stateFlow.collectWith(viewModelScope) {
            handleUpdatedAmountState(it)
        }
        addressService.stateFlow.collectWith(viewModelScope) {
            handleUpdatedAddressState(it)
        }
        xRateService.getRateFlow(sendToken.coin.uid).collectWith(viewModelScope) {
            coinRate = it
        }
        xRateService.getRateFlow(feeToken.coin.uid).collectWith(viewModelScope) {
            feeCoinRate = it
        }

        addressService.setAddress(address)

        sendTransactionService.stateFlow.collectWith(viewModelScope) {
            emitState()
        }

        sendTransactionService.start(viewModelScope)

        amountService.stateFlow.onEach { newAmountState ->
            addressState.address?.let { address ->
                val amount = newAmountState.amount ?: BigDecimal.ZERO
                sendTransactionService.setSendTransactionData(
                    SendTransactionData.Evm(
                        adapter.getTransactionData(
                            amount,
                            EvmAddress(address.hex)
                        ),
                        null,
                        amount = amount
                    )
                )
            }
        }.launchIn(viewModelScope)

    }

    override fun createState(): SendUiState {
        val txState = sendTransactionService.stateFlow.value
        val hasSendData = amountMoreThanZero() && addressState.address != null
        val poison = isAddressSuspicious(addressState.address?.hex)
        val canSend = amountState.canBeSend && addressState.canBeSend && txState.sendable &&
            (!poison || riskAccepted)
        return SendUiState(
            availableBalance = amountState.availableBalance,
            amountCaution = amountState.amountCaution,
            addressError = addressState.addressError,
            canBeSend = canSend,
            showAddressInput = showAddressInput,
            address = addressState.address,
            cautions = if (hasSendData) txState.cautions else emptyList(),
            fee = txState.networkFee?.primary?.value,
            feeLoading = hasSendData && txState.loading,
            isPoisonAddress = poison,
            riskAccepted = riskAccepted,
        )
    }

    private fun amountMoreThanZero(): Boolean {
        val amount = amountService.stateFlow.value.amount
        return amount != null && !amount.isZero()
    }

    fun onEnterAmount(amount: BigDecimal?) {
        amountService.setAmount(amount)
    }

    fun onEnterAddress(address: Address?) {
        resetRiskAccepted()
        addressService.setAddress(address)
    }

    fun onClickSend() = viewModelScope.launch(dispatcherProvider.default) {
        sendResult = try {
            val sendResult = sendTransactionService.sendTransaction()
            onSendSuccess(addressState.address?.hex)
            SendResult.Sent(sendResult.getRecordUid())
        } catch (e: TrezorCancelledException) {
            null
        } catch (e: Throwable) {
            if (e.isHardwareWalletUserCancelled()) {
                Timber.i("user cancelled")
                null
            } else {
                SendResult.Failed(createCaution(e))
            }
        }
    }

    override fun onClickSignOffline(format: OfflineTransactionFormat) {
        offlineSigningController.sign(
            format = format,
            producer = ::signedOfflineTransaction,
            draftBuilder = ::offlineSignedTransactionDraft,
        )
    }

    override fun getConfirmationData(): SendConfirmationData {
        val address = addressState.address
            ?: throw LocalizedException(R.string.send_error_address_unavailable)
        val amount = amountState.amount
            ?: throw LocalizedException(R.string.send_error_amount_unavailable)
        val fee = sendTransactionService.stateFlow.value.networkFee?.primary?.value

        val contact = contactsRepository.getContactsFiltered(
            wallet.token.blockchainType,
            addressQuery = address.hex
        ).firstOrNull()
        return SendConfirmationData(
            amount = amount,
            fee = fee,
            address = address,
            contact = contact,
            coin = wallet.token.coin,
            feeCoin = feeToken.coin,
            memo = null,
        )
    }

    private suspend fun signedOfflineTransaction(): OfflineSignResult {
        val confirmationData = getConfirmationData()
        val signingAdapter = offlineSignAdapter ?: throw LocalizedException(R.string.Error)
        return OfflineSignResult(
            signedTransaction = signingAdapter.signOffline(sendTransactionService.offlineSignRequest()),
            confirmationData = confirmationData,
        )
    }

    private fun offlineSignedTransactionDraft(result: OfflineSignResult): OfflineSignedTransactionDraft {
        val confirmationData = result.confirmationData
        return OfflineSignedTransactionDraft(
            wallet = wallet,
            amount = confirmationData.amount,
            fee = confirmationData.fee,
            toAddress = confirmationData.address.hex,
            rawHex = result.signedTransaction.rawHex,
            txHash = result.signedTransaction.txHash,
            inputOutpoints = emptyList(),
            feeToken = feeToken,
        )
    }

    private fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState
        updateSendTransactionData()
        emitState()
    }

    private fun handleUpdatedAddressState(addressState: SendEvmAddressService.State) {
        this.addressState = addressState
        updateSendTransactionData()
        emitState()
    }

    private fun updateSendTransactionData() {
        val amount = amountState.amount ?: return
        val address = addressState.address ?: return
        viewModelScope.launch {
            sendTransactionService.setSendTransactionData(
                SendTransactionData.Evm(
                    adapter.getTransactionData(
                        amount,
                        EvmAddress(address.hex)
                    ),
                    null
                )
            )
        }
    }

    private fun createCaution(error: Throwable) = when (error) {
        is UnknownHostException -> HSCaution(TranslatableString.ResString(R.string.Hud_Text_NoInternet))
        is LocalizedException -> HSCaution(TranslatableString.ResString(error.errorTextRes))
        is JsonRpc.ResponseError.RpcError -> HSCaution(TranslatableString.PlainString(error.error.message))
        else -> HSCaution(TranslatableString.PlainString(error.message ?: ""))
    }
}
