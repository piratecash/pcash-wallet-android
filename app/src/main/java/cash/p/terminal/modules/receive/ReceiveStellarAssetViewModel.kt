package cash.p.terminal.modules.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.adapters.stellar.StellarAssetAdapter
import cash.p.terminal.modules.receive.viewmodels.AddressUriService
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal

class ReceiveStellarAssetViewModel(
    private val wallet: Wallet,
    val adapterManager: IAdapterManager,
) : ViewModelUiState<ReceiveStellarAssetUiState>() {
    private val watchAccount = wallet.account.isWatchAccount
    private val blockchainName = wallet.token.blockchain.name
    private var address: String = ""
    private var mainNet = true
    private var amount: BigDecimal? = null
    private var viewState: ViewState = ViewState.Loading

    private val addressUriService = AddressUriService(wallet.token)
    private var trustlineEstablished: Boolean? = null

    private var addressUriState = addressUriService.stateFlow.value

    init {
        viewModelScope.launch {
            addressUriService.stateFlow.collect {
                handleUpdatedAddressUriState(it)
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            fetchAddress()

            emitState()
        }
    }

    private suspend fun fetchAddress() {
        try {
            val adapter = adapterManager.getAdapterForWallet<StellarAssetAdapter>(wallet)
                ?: throw ReceiveStellarAssetError.NoAdapter
            mainNet = adapter.isMainNet
            trustlineEstablished = adapter.isTrustlineEstablished()

            viewState = ViewState.Success
            setAddress(adapter.receiveAddress)
        } catch (e: Throwable) {
            viewState = ViewState.Error(e)
        }
    }

    private fun handleUpdatedAddressUriState(state: AddressUriService.State) {
        addressUriState = state

        emitState()
    }

    override fun createState() = ReceiveStellarAssetUiState(
        viewState = viewState,
        uri = addressUriState.uri,
        address = address,
        mainNet = mainNet,
        blockchainName = blockchainName,
        watchAccount = watchAccount,
        amount = amount,
        activationRequired = trustlineEstablished == false,
        coinCode = wallet.coin.code,
        trustlineEstablished = trustlineEstablished,
    )

    fun setAmount(amount: BigDecimal?) {
        this.amount = amount

        addressUriService.setAmount(amount)

        emitState()
    }

    fun onErrorClick() {
        viewModelScope.launch(Dispatchers.Default) {
            fetchAddress()

            emitState()
        }
    }

    class Factory(private val wallet: Wallet) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReceiveStellarAssetViewModel(wallet, App.adapterManager) as T
        }
    }

    private fun setAddress(receiveAddress: String) {
        address = receiveAddress

        addressUriService.setAddress(address)
    }

    fun onActivationResult(activated: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            if (activated) {
                fetchAddress()

                emitState()
            }
        }

    }
}

sealed class ReceiveStellarAssetError : Throwable() {
    object NoAdapter : ReceiveStellarAssetError()
}

data class ReceiveStellarAssetUiState(
    override val viewState: ViewState,
    override val uri: String,
    override val address: String,
    override val mainNet: Boolean,
    override val blockchainName: String,
    override val watchAccount: Boolean,
    override val amount: BigDecimal?,
    val activationRequired: Boolean,
    val coinCode: String,
    val trustlineEstablished: Boolean?,
) : ReceiveModule.AbstractUiState() {
    override val additionalItems = listOf<ReceiveModule.AdditionalData>()
    override val addressFormat = null
    override val alertText = null
}
