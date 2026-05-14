package cash.p.terminal.modules.eip20approve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.eip20approve.AllowanceMode.OnlyRequired
import cash.p.terminal.modules.eip20allowance.Eip20AllowanceSendTransactionFactory
import cash.p.terminal.modules.eip20allowance.collectSendTransactionServiceState
import cash.p.terminal.modules.multiswap.FiatService
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.send.SendModule
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.CurrencyManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.Currency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

internal class Eip20ApproveViewModel(
    private val token: Token,
    private val requiredAllowance: BigDecimal,
    private val spenderAddress: String,
    private val adapterManager: IAdapterManager,
    private val currencyManager: CurrencyManager,
    private val fiatService: FiatService,
    private val contactsRepository: ContactsRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModelUiState<Eip20ApproveUiState>() {
    private val currency = currencyManager.baseCurrency
    private var allowanceMode = OnlyRequired
    private val sendTransactionServiceFlow = MutableStateFlow<ISendTransactionService<*>?>(null)
    val sendTransactionService: ISendTransactionService<*>?
        get() = sendTransactionServiceFlow.value
    private var sendTransactionState = Eip20AllowanceSendTransactionFactory.emptyServiceState()
    private var preparing = false
    private var fiatAmount: BigDecimal? = null
    private val contact = contactsRepository.getContactsFiltered(
        blockchainType = token.blockchainType,
        addressQuery = spenderAddress
    ).firstOrNull()
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    override fun createState() = Eip20ApproveUiState(
        token = token,
        requiredAllowance = requiredAllowance,
        allowanceMode = allowanceMode,
        networkFee = sendTransactionState.networkFee,
        cautions = sendTransactionState.cautions,
        currency = currency,
        fiatAmount = fiatAmount,
        spenderAddress = spenderAddress,
        contact = contact,
        approveEnabled = sendTransactionService != null && sendTransactionState.sendable,
        preparing = preparing
    )

    init {
        fiatService.setCurrency(currency)
        fiatService.setToken(token)
        fiatService.setAmount(requiredAllowance)
        addCloseable(fiatService)

        viewModelScope.launch {
            fiatService.stateFlow.collect {
                fiatAmount = it.fiatAmount
                emitState()
            }
        }

        viewModelScope.collectSendTransactionServiceState(sendTransactionServiceFlow) {
            sendTransactionState = it
            emitState()
        }
    }

    fun setAllowanceMode(allowanceMode: AllowanceMode) {
        if (preparing) return

        this.allowanceMode = allowanceMode

        emitState()
    }

    fun prepareApprove() {
        if (preparing) return

        preparing = true
        emitState()

        viewModelScope.launch {
            try {
                val transactionData = Eip20AllowanceSendTransactionFactory.buildApproveTransactionData(
                    token = token,
                    spenderAddress = spenderAddress,
                    amount = requiredAllowance,
                    allowanceMode = allowanceMode,
                    adapterManager = adapterManager
                )
                val service = sendTransactionService
                    ?: Eip20AllowanceSendTransactionFactory.createSendTransactionService(token)
                        .also(::bindSendTransactionService)

                service.setSendTransactionData(transactionData)
                _events.send(Event.NavigateToConfirm)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _events.send(Event.ShowError(Eip20AllowanceSendTransactionFactory.userMessage(t)))
            } finally {
                preparing = false
                emitState()
            }
        }
    }

    private fun bindSendTransactionService(sendTransactionService: ISendTransactionService<*>) {
        sendTransactionServiceFlow.value = sendTransactionService
        emitState()
        sendTransactionService.start(viewModelScope)
    }

    sealed class Event {
        data object NavigateToConfirm : Event()
        data class ShowError(val message: String) : Event()
    }

    suspend fun approve() = withContext(dispatcherProvider.default) {
        checkNotNull(sendTransactionService) { "Send transaction service is not prepared" }
            .sendTransaction()
    }

    class Factory(
        private val token: Token,
        private val requiredAllowance: BigDecimal,
        private val spenderAddress: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dispatcherProvider: DispatcherProvider by inject(DispatcherProvider::class.java)

            return Eip20ApproveViewModel(
                token = token,
                requiredAllowance = requiredAllowance,
                spenderAddress = spenderAddress,
                adapterManager = App.adapterManager,
                currencyManager = App.currencyManager,
                fiatService = FiatService(App.marketKit),
                contactsRepository = App.contactsRepository,
                dispatcherProvider = dispatcherProvider
            ) as T
        }
    }
}

data class Eip20ApproveUiState(
    val token: Token,
    val requiredAllowance: BigDecimal,
    val allowanceMode: AllowanceMode,
    val networkFee: SendModule.AmountData?,
    val cautions: List<CautionViewItem>,
    val currency: Currency,
    val fiatAmount: BigDecimal?,
    val spenderAddress: String,
    val contact: Contact?,
    val approveEnabled: Boolean,
    val preparing: Boolean,
)

enum class AllowanceMode {
    OnlyRequired, Unlimited
}
