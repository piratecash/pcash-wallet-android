package cash.p.terminal.modules.send.ton

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import io.horizontalsystems.core.logger.AppLogger
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.managers.RecentAddressManager
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal
import java.net.UnknownHostException
import kotlin.getValue
import kotlin.math.abs

class SendTonViewModel(
    val wallet: Wallet,
    private val sendToken: Token,
    val feeToken: Token,
    val adapter: ISendTonAdapter,
    private val xRateService: XRateService,
    private val amountService: SendTonAmountService,
    private val addressService: SendTonAddressService,
    private val feeService: SendTonFeeService,
    val coinMaxAllowedDecimals: Int,
    private val contactsRepo: ContactsRepository,
    private val showAddressInput: Boolean,
    private val address: Address,
): ViewModelUiState<SendTonUiState>() {
    val blockchainType = wallet.token.blockchainType
    val feeTokenMaxAllowedDecimals = feeToken.decimals
    val fiatMaxAllowedDecimals = App.appConfigProvider.fiatDecimal

    private val recentAddressManager: RecentAddressManager by inject(RecentAddressManager::class.java)

    // Calculate the decimal rate between the send token and the fee token
    private val decimalDiff = sendToken.decimals - feeToken.decimals
    private val decimalRate = if(decimalDiff < 0) {
        1.toBigDecimal().divide(BigDecimal.TEN.pow(abs(decimalDiff)))
    } else {
        BigDecimal.TEN.pow(decimalDiff)
    }

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value
    private var feeState = feeService.stateFlow.value
    private var memo: String? = null

    var coinRate by mutableStateOf(xRateService.getRate(sendToken.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(feeToken.coin.uid))
        private set
    var sendResult by mutableStateOf<SendResult?>(null)
        private set

    private val logger: AppLogger = AppLogger("send-ton")

    init {
        addCloseable(feeService)

        viewModelScope.launch(Dispatchers.Default) {
            amountService.stateFlow.collect {
                handleUpdatedAmountState(it)
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            addressService.stateFlow.collect {
                handleUpdatedAddressState(it)
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            feeService.stateFlow.collect {
                handleUpdatedFeeState(it)
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            xRateService.getRateFlow(sendToken.coin.uid).collect {
                coinRate = it
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            xRateService.getRateFlow(feeToken.coin.uid).collect {
                feeCoinRate = it
            }
        }

        addressService.setAddress(address)
    }

    override fun createState() = SendTonUiState(
        availableBalance = amountState.availableBalance,
        amountCaution = amountState.amountCaution
            ?: if(feeState.feeStatus is FeeStatus.NoEnoughBalance) { HSCaution(
                TranslatableString.ResString(R.string.not_enough_ton_for_fee),
                HSCaution.Type.Error
            ) } else { null },
        addressError = addressState.addressError,
        canBeSend = (feeState.feeStatus is FeeStatus.Success) && amountState.canBeSend && addressState.canBeSend,
        showAddressInput = showAddressInput,
        fee = (feeState.feeStatus as? FeeStatus.Success)?.fee?.multiply(decimalRate),
        feeInProgress = feeState.inProgress,
        address = address
    )

    fun onEnterAmount(amount: BigDecimal?) {
        amountService.setAmount(amount)
    }

    fun onEnterAddress(address: Address?) {
        addressService.setAddress(address)
    }

    fun getConfirmationData(): SendConfirmationData {
        val address = addressState.address!!
        val contact = contactsRepo.getContactsFiltered(
            blockchainType,
            addressQuery = address.hex
        ).firstOrNull()
        return SendConfirmationData(
            amount = amountState.amount!!,
            fee = (feeState.feeStatus as? FeeStatus.Success)?.fee?.multiply(decimalRate) ?: BigDecimal.ZERO,
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

    fun onEnterMemo(memo: String) {
        viewModelScope.launch(Dispatchers.Default) {
            this@SendTonViewModel.memo = memo.ifBlank { null }
            feeService.setMemo(this@SendTonViewModel.memo)
        }
    }

    private suspend fun send() = withContext(Dispatchers.IO) {
        try {
            sendResult = SendResult.Sending
            logger.info("sending tx")

            adapter.send(amountState.amount!!, addressState.tonAddress!!, memo)

            sendResult = SendResult.Sent()
            logger.info("success")

            recentAddressManager.setRecentAddress(addressState.address!!, BlockchainType.Ton)
        } catch (e: Throwable) {
            sendResult = SendResult.Failed(createCaution(e))
            logger.warning("failed", e)
        }
    }

    private fun createCaution(error: Throwable) = when (error) {
        is UnknownHostException -> HSCaution(TranslatableString.ResString(R.string.Hud_Text_NoInternet))
        is LocalizedException -> HSCaution(TranslatableString.ResString(error.errorTextRes))
        else -> HSCaution(TranslatableString.PlainString(error.message ?: ""))
    }

    private fun handleUpdatedAmountState(amountState: SendTonAmountService.State) {
        this.amountState = amountState

        feeService.setAmount(amountState.amount)

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
    val address: Address,
)
