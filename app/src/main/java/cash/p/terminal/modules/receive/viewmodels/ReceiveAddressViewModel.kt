package cash.p.terminal.modules.receive.viewmodels

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.entities.UsedAddress
import cash.p.terminal.core.factories.uriScheme
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.entities.AddressUri
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.receive.ReceiveModule
import cash.p.terminal.modules.receive.ReceiveModule.AdditionalData
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.wallet.accountTypeDerivation
import cash.p.terminal.wallet.bitcoinCashCoinType
import cash.p.terminal.wallet.entities.TokenType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal

class ReceiveAddressViewModel(
    private val wallet: Wallet,
    private val adapterManager: IAdapterManager
) : ViewModelUiState<ReceiveModule.UiState>() {

    private var viewState: ViewState = ViewState.Loading
    private var address = ""
    private var usedAddresses: List<UsedAddress> = listOf()
    private var usedChangeAddresses: List<UsedAddress> = listOf()
    private var uri = ""
    private var amount: BigDecimal? = null
    private var accountActive = true
    private var blockchainName: String? = null
    private var addressFormat: String? = null
    private var mainNet = true
    private var watchAccount = wallet.account.isWatchAccount
    private var alertText: ReceiveModule.AlertText? = getAlertText(watchAccount)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            adapterManager.adaptersReadyObservable.asFlow()
                .collect {
                    setData()
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            setData()
        }
        setNetworkName()
    }

    override fun createState() = ReceiveModule.UiState(
        viewState = viewState,
        address = address,
        usedAddresses = usedAddresses,
        usedChangeAddresses = usedChangeAddresses,
        showTronAlert = !accountActive,
        uri = uri,
        watchAccount = watchAccount,
        additionalItems = getAdditionalData(),
        amount = amount,
        alertText = alertText,
        mainNet = mainNet,
        blockchainName = blockchainName,
        addressFormat = addressFormat,
    )

    private fun setNetworkName() {
        when (val tokenType = wallet.token.type) {
            is TokenType.Derived -> {
                addressFormat = "${tokenType.derivation.accountTypeDerivation.addressType} (${tokenType.derivation.accountTypeDerivation.rawName})"
            }

            is TokenType.AddressTyped -> {
                addressFormat = tokenType.type.bitcoinCashCoinType.title
            }

            else -> {
                blockchainName = wallet.token.blockchain.name
            }
        }
        emitState()
    }

    private fun getAlertText(watchAccount: Boolean): ReceiveModule.AlertText? {
        return if (watchAccount) ReceiveModule.AlertText.Normal(
            Translator.getString(R.string.Balance_Receive_WatchAddressAlert)
        )
        else null
    }

    private suspend fun setData() {
        val adapter = adapterManager.getReceiveAdapterForWallet(wallet)
        if (adapter != null) {
            address = adapter.receiveAddress
            usedAddresses = adapter.usedAddresses(false)
            usedChangeAddresses = adapter.usedAddresses(true)
            uri = getUri()
            mainNet = adapter.isMainNet
            viewState = ViewState.Success

            accountActive = try {
                adapter.isAddressActive(adapter.receiveAddress)
            } catch (e: Exception) {
                viewState = ViewState.Error(e)
                false
            }
        } else {
            viewState = ViewState.Error(NullPointerException())
        }
        emitState()
    }

    private fun getUri(): String {
        var newUri = address
        amount?.let {
            val parser = AddressUriParser(wallet.token.blockchainType, wallet.token.type)
            val addressUri = AddressUri(wallet.token.blockchainType.uriScheme ?: "")
            addressUri.address = newUri
            addressUri.parameters[AddressUri.Field.amountField(wallet.token.blockchainType)] = it.toString()
            addressUri.parameters[AddressUri.Field.BlockchainUid] = wallet.token.blockchainType.uid
            if (wallet.token.type !is TokenType.Derived && wallet.token.type !is TokenType.AddressTyped) {
                addressUri.parameters[AddressUri.Field.TokenUid] = wallet.token.type.id
            }
            newUri = parser.uri(addressUri)
        }

        return newUri
    }

    private fun getAdditionalData(): List<AdditionalData> {
        val items = mutableListOf<AdditionalData>()

        if (!accountActive) {
            items.add(AdditionalData.AccountNotActive)
        }

        amount?.let {
            items.add(
                AdditionalData.Amount(
                    value = it.toString()
                )
            )
        }

        return items
    }

    fun onErrorClick() {
        viewModelScope.launch(Dispatchers.IO) {
            setData()
        }
    }

    fun setAmount(amount: BigDecimal?) {
        amount?.let {
            if (it <= BigDecimal.ZERO) {
                this.amount = null
                emitState()
                return
            }
        }
        this.amount = amount
        uri = getUri()
        emitState()
    }

}
