package cash.p.terminal.modules.eip20approve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.adapters.Eip20Adapter
import cash.p.terminal.core.adapters.Trc20Adapter
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.isEvm
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.eip20approve.AllowanceMode.OnlyRequired
import cash.p.terminal.modules.eip20approve.AllowanceMode.Unlimited
import cash.p.terminal.modules.multiswap.FiatService
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceFactory
import cash.p.terminal.modules.send.SendModule
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.CurrencyManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.ethereumkit.models.Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

internal class Eip20ApproveViewModel(
    private val token: Token,
    private val requiredAllowance: BigDecimal,
    private val spenderAddress: String,
    private val adapterManager: IAdapterManager,
    val sendTransactionService: ISendTransactionService<*>,
    private val currencyManager: CurrencyManager,
    private val fiatService: FiatService,
    private val contactsRepository: ContactsRepository,
) : ViewModelUiState<Eip20ApproveUiState>() {
    private val currency = currencyManager.baseCurrency
    private var allowanceMode = OnlyRequired
    private var sendTransactionState = sendTransactionService.stateFlow.value
    private var fiatAmount: BigDecimal? = null
    private val contact = contactsRepository.getContactsFiltered(
        blockchainType = token.blockchainType,
        addressQuery = spenderAddress
    ).firstOrNull()

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
        approveEnabled = sendTransactionState.sendable
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

        viewModelScope.launch {
            sendTransactionService.stateFlow.collect { transactionState ->
                sendTransactionState = transactionState
                emitState()
            }
        }

        sendTransactionService.start(viewModelScope)
    }

    fun setAllowanceMode(allowanceMode: AllowanceMode) {
        this.allowanceMode = allowanceMode

        emitState()
    }

    fun freeze() {
        viewModelScope.launch {
            if (token.blockchainType.isEvm) {
                freezeEvm()
            } else if (token.blockchainType == BlockchainType.Tron) {
                freezeTron()
            }
        }
    }

    private suspend fun freezeEvm() {
        val eip20Adapter = adapterManager.getAdapterForToken<Eip20Adapter>(token)
        checkNotNull(eip20Adapter)

        val transactionData = when (allowanceMode) {
            OnlyRequired -> eip20Adapter.buildApproveTransactionData(
                Address(spenderAddress),
                requiredAllowance
            )

            Unlimited -> eip20Adapter.buildApproveUnlimitedTransactionData(Address(spenderAddress))
        }

        sendTransactionService.setSendTransactionData(SendTransactionData.Evm(transactionData, null))
    }

    private suspend fun freezeTron() {
        val trc20Adapter = adapterManager.getAdapterForToken<Trc20Adapter>(token)
        checkNotNull(trc20Adapter)

        val triggerSmartContract = when (allowanceMode) {
            OnlyRequired -> trc20Adapter.approveTrc20TriggerSmartContract(
                spenderAddress,
                requiredAllowance
            )

            Unlimited -> trc20Adapter.approveTrc20TriggerSmartContractUnlim(spenderAddress)
        }

        sendTransactionService.setSendTransactionData(SendTransactionData.Tron.WithContract(triggerSmartContract))
    }

    suspend fun approve() = withContext(Dispatchers.Default) {
        sendTransactionService.sendTransaction()
    }

    class Factory(
        private val token: Token,
        private val requiredAllowance: BigDecimal,
        private val spenderAddress: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val sendTransactionService = SendTransactionServiceFactory.create(token)

            return Eip20ApproveViewModel(
                token = token,
                requiredAllowance = requiredAllowance,
                spenderAddress = spenderAddress,
                adapterManager = App.adapterManager,
                sendTransactionService = sendTransactionService,
                currencyManager = App.currencyManager,
                fiatService = FiatService(App.marketKit),
                contactsRepository = App.contactsRepository
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
)

enum class AllowanceMode {
    OnlyRequired, Unlimited
}

