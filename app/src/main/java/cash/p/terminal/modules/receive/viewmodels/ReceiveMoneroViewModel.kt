package cash.p.terminal.modules.receive.viewmodels

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.IMoneroReceiveAdapter
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.managers.MoneroSubaddressInfo
import cash.p.terminal.core.requiresTrezorPreparation
import cash.p.terminal.modules.receive.ReceiveModule
import cash.p.terminal.modules.send.hardwareWalletUserMessageRes
import cash.p.terminal.modules.send.isHardwareWalletCancelled
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.math.BigDecimal

class ReceiveMoneroViewModel(
    private val wallet: Wallet,
    private val adapterManager: IAdapterManager,
    private val localStorage: ILocalStorage,
    dispatcherProvider: DispatcherProvider,
) : ViewModelUiState<ReceiveMoneroUiState>() {

    private val addressUriService = AddressUriService(wallet.token)

    private var viewState: ViewState = ViewState.Loading
    private var address = ""
    private var currentAddressIndex = 0
    private var addressBadge = AddressBadge.UNUSED
    private var subaddresses: List<MoneroSubaddressInfo> = emptyList()
    private var amount: BigDecimal? = null
    private var isCreatingAddress = false
    private var isHardwareOperationInProgress = false
    @StringRes
    private var hardwareOperationError: Int? = null
    private var hardwareWallet = false
    private var spendReadiness = MoneroSpendReadiness.Ready
    private var isNewInSession = false
    private val watchAccount = wallet.account.isWatchAccount

    private var addressUriState = addressUriService.stateFlow.value
    private var readinessJob: Job? = null
    private var observedAdapter: IMoneroReceiveAdapter? = null

    init {
        viewModelScope.launch {
            addressUriService.stateFlow.collect {
                addressUriState = it
                emitState()
            }
        }
        viewModelScope.launch(dispatcherProvider.io) {
            adapterManager.adaptersReadyObservable.asFlow()
                .collect { fetchData() }
        }
        viewModelScope.launch(dispatcherProvider.io) {
            fetchData()
        }
    }

    private suspend fun fetchData() {
        val adapter = adapterManager.getAdapterForWallet<IMoneroReceiveAdapter>(wallet)
        if (adapter == null) {
            viewState = ViewState.Loading
            emitState()
            return
        }
        observeReadiness(adapter)
        val allSubaddresses = adapter.getSubaddresses()
        if (allSubaddresses.isEmpty()) {
            viewState = ViewState.Loading
            emitState()
            return
        }
        subaddresses = allSubaddresses
        val latest = allSubaddresses.last()
        currentAddressIndex = latest.index
        address = latest.address
        addressBadge = calculateBadge()
        addressUriService.setAddress(address)
        viewState = ViewState.Success
        emitState()
    }

    override fun createState() = ReceiveMoneroUiState(
        viewState = viewState,
        uri = addressUriState.uri,
        address = address,
        blockchainName = wallet.token.blockchain.name,
        watchAccount = watchAccount,
        amount = amount,
        addressBadge = addressBadge,
        hasAddressHistory = subaddresses.count { it.index != currentAddressIndex } > 0,
        isCreatingAddress = isCreatingAddress,
        hardwareWallet = hardwareWallet,
        spendReadiness = spendReadiness,
        isHardwareOperationInProgress = isHardwareOperationInProgress,
        hardwareOperationError = hardwareOperationError,
    )

    private fun observeReadiness(adapter: IMoneroReceiveAdapter) {
        hardwareWallet = adapter.hardwareWallet
        spendReadiness = adapter.spendReadiness.value
        if (observedAdapter === adapter) return
        observedAdapter = adapter
        readinessJob?.cancel()
        readinessJob = viewModelScope.launch {
            adapter.spendReadiness.collect {
                spendReadiness = it
                emitState()
            }
        }
    }

    fun createNewAddress() {
        if (isCreatingAddress) return
        isCreatingAddress = true
        emitState()

        viewModelScope.launch {
            try {
                val adapter = adapterManager.getAdapterForWallet<IMoneroReceiveAdapter>(wallet)
                    ?: return@launch
                val newAddress = adapter.createNewSubaddress()
                address = newAddress
                subaddresses = adapter.getSubaddresses()
                currentAddressIndex = subaddresses.lastOrNull()?.index ?: 0
                isNewInSession = true
                addressBadge = AddressBadge.NEW
                addressUriService.setAddress(address)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create new Monero subaddress")
            } finally {
                isCreatingAddress = false
                emitState()
            }
        }
    }

    fun refreshWithTrezor() {
        runHardwareOperation { it.refreshHardwareKeyImages() }
    }

    fun displayAddressOnDevice() {
        runHardwareOperation { it.displayAddressOnDevice(currentAddressIndex) }
    }

    private fun runHardwareOperation(
        operation: suspend (IMoneroReceiveAdapter) -> Unit,
    ) {
        if (isHardwareOperationInProgress) return
        isHardwareOperationInProgress = true
        hardwareOperationError = null
        emitState()
        viewModelScope.launch {
            try {
                adapterManager.getAdapterForWallet<IMoneroReceiveAdapter>(wallet)
                    ?.let { operation(it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error.isHardwareWalletCancelled()) return@launch
                Timber.e(error, "Monero hardware wallet operation failed")
                hardwareOperationError = error.hardwareWalletUserMessageRes()
            } finally {
                isHardwareOperationInProgress = false
                emitState()
            }
        }
    }

    fun setAmount(amount: BigDecimal?) {
        if (amount != null && amount <= BigDecimal.ZERO) {
            this.amount = null
        } else {
            this.amount = amount
        }
        addressUriService.setAmount(this.amount)
        emitState()
    }

    fun onErrorClick() {
        viewModelScope.launch {
            fetchData()
        }
    }

    val skipNewAddressConfirm: Boolean
        get() = localStorage.moneroSkipNewAddressConfirm

    fun setSkipNewAddressConfirm(skip: Boolean) {
        localStorage.moneroSkipNewAddressConfirm = skip
    }

    fun getSubaddressesForHistory(): List<MoneroSubaddressInfo> = subaddresses

    private fun calculateBadge(): AddressBadge {
        if (isNewInSession) return AddressBadge.NEW
        val current = subaddresses.find { it.index == currentAddressIndex }
        return if (current != null && current.receivedAmount > 0) {
            AddressBadge.USED
        } else {
            AddressBadge.UNUSED
        }
    }
}

data class ReceiveMoneroUiState(
    override val viewState: ViewState,
    override val alertText: ReceiveModule.AlertText? = null,
    override val uri: String = "",
    override val address: String = "",
    override val mainNet: Boolean = true,
    override val blockchainName: String? = null,
    override val addressFormat: String? = null,
    override val additionalItems: List<ReceiveModule.AdditionalData> = emptyList(),
    override val watchAccount: Boolean = false,
    override val amount: BigDecimal? = null,
    val addressBadge: AddressBadge = AddressBadge.UNUSED,
    val hasAddressHistory: Boolean = false,
    val isCreatingAddress: Boolean = false,
    val hardwareWallet: Boolean = false,
    val spendReadiness: MoneroSpendReadiness = MoneroSpendReadiness.Ready,
    val isHardwareOperationInProgress: Boolean = false,
    @StringRes val hardwareOperationError: Int? = null,
) : ReceiveModule.AbstractUiState() {
    val showTrezorUpdateAction: Boolean
        get() = spendReadiness.requiresTrezorPreparation()
}

enum class AddressBadge { NEW, UNUSED, USED }

@Parcelize
data class MoneroUsedAddressesParams(
    val subaddresses: List<MoneroSubaddressParcelable>,
) : Parcelable

@Parcelize
data class MoneroSubaddressParcelable(
    val index: Int,
    val address: String,
    val receivedAmount: Long,
) : Parcelable
