package cash.p.terminal.modules.send.evm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendEthereumAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceEvm
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.SendUiState
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.z.ecc.android.sdk.ext.collectWith
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal
import java.net.UnknownHostException

internal class SendEvmViewModel(
    val wallet: Wallet,
    sendToken: Token,
    val adapter: ISendEthereumAdapter,
    private val sendTransactionService: SendTransactionServiceEvm,
    xRateService: XRateService,
    private val amountService: SendAmountService,
    private val addressService: SendEvmAddressService,
    val coinMaxAllowedDecimals: Int,
    private val showAddressInput: Boolean,
    private val address: Address?
) : ViewModelUiState<SendUiState>() {
    val fiatMaxAllowedDecimals = App.appConfigProvider.fiatDecimal
    val blockchainType = wallet.token.blockchainType

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value

    private val contactsRepository: ContactsRepository by inject(ContactsRepository::class.java)

    var coinRate by mutableStateOf(xRateService.getRate(sendToken.coin.uid))
        private set
    var sendResult by mutableStateOf<SendResult?>(null)
        private set

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

        addressService.setAddress(address)

        viewModelScope.launch {
            sendTransactionService.stateFlow.collect { transactionState ->
                emitState()
            }
        }

        sendTransactionService.start(viewModelScope)

        amountService.stateFlow.onEach {
            val tmpAmount = amountState.amount ?: return@onEach
            addressState.address?.let { address ->
                sendTransactionService.setSendTransactionData(
                    SendTransactionData.Evm(
                        adapter.getTransactionData(
                            tmpAmount,
                            io.horizontalsystems.ethereumkit.models.Address(address.hex)
                        ),
                        null
                    )
                )
            }
        }.launchIn(viewModelScope)

    }

    override fun createState() = SendUiState(
        availableBalance = amountState.availableBalance,
        amountCaution = amountState.amountCaution,
        addressError = addressState.addressError,
        canBeSend = amountState.canBeSend && addressState.canBeSend,
        showAddressInput = showAddressInput,
        address = addressState.address,
    )

    fun onEnterAmount(amount: BigDecimal?) {
        amountService.setAmount(amount)
    }

    fun onEnterAddress(address: Address?) {
        addressService.setAddress(address)
    }

    fun onClickSend() = viewModelScope.launch(Dispatchers.Default) {
        sendResult = try {
            val sendResult = sendTransactionService.sendTransaction()
            sendResult.fullTransaction.transaction.hash
            SendResult.Sent()
        } catch (e: Exception) {
            SendResult.Failed(createCaution(e))
        }
    }

    fun getConfirmationData(): SendConfirmationData {
        val address = requireNotNull(addressState.address)
        val fee = sendTransactionService.stateFlow.value.networkFee?.primary?.value

        val contact = contactsRepository.getContactsFiltered(
            wallet.token.blockchainType,
            addressQuery = address.hex
        ).firstOrNull()
        return SendConfirmationData(
            amount = amountState.amount!!,
            fee = fee,
            address = address,
            contact = contact,
            coin = wallet.token.coin,
            feeCoin = wallet.token.coin,
            memo = null,
        )
    }

    private fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState

        emitState()
    }

    private fun handleUpdatedAddressState(addressState: SendEvmAddressService.State) {
        this.addressState = addressState

        emitState()
    }

    private fun createCaution(error: Throwable) = when (error) {
        is UnknownHostException -> HSCaution(TranslatableString.ResString(R.string.Hud_Text_NoInternet))
        is LocalizedException -> HSCaution(TranslatableString.ResString(error.errorTextRes))
        else -> HSCaution(TranslatableString.PlainString(error.message ?: ""))
    }
}
