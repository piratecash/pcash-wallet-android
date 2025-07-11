package cash.p.terminal.modules.depositcex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.core.providers.CexAsset
import cash.p.terminal.core.providers.CexDepositNetwork
import cash.p.terminal.core.providers.CexProviderManager
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.receive.ReceiveModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal

class DepositAddressViewModel(
    private val cexAsset: CexAsset,
    private val network: CexDepositNetwork?,
    cexProviderManager: CexProviderManager
) : ViewModelUiState<ReceiveModule.UiState>() {
    private val cexProvider = cexProviderManager.cexProviderFlow.value

    private var viewState: ViewState = ViewState.Loading
    private var address = ""
    private var uri = ""
    private var amount: BigDecimal? = null
    private var memo: String? = null
    private val networkName = network?.name ?: cexAsset.depositNetworks.firstOrNull()?.name ?: ""
    private val watchAccount = false

    init {
        setInitialData()
    }

    override fun createState() = ReceiveModule.UiState(
        viewState = viewState,
        address = address,
        usedAddresses = listOf(),
        usedChangeAddresses = listOf(),
        uri = uri,
        networkName = networkName,
        watchAccount = watchAccount,
        additionalItems = getAdditionalData(),
        amount = amount,
        alertText = getAlertText(memo != null)
    )

    private fun setInitialData() {
        viewState = ViewState.Loading
        emitState()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cexAddress = cexProvider?.getAddress(cexAsset.id, network?.id)
                if (cexAddress == null) {
                    viewState = ViewState.Error(Throwable("No address"))
                } else {
                    if (cexAddress.tag.isNotBlank()) {
                        memo = cexAddress.tag
                    }
                    address = cexAddress.address
                    uri = cexAddress.address
                    viewState = ViewState.Success
                }
            } catch (t: Throwable) {
                viewState = ViewState.Error(t)
            }
            emitState()
        }
    }

    private fun getAdditionalData(): List<ReceiveModule.AdditionalData> {
        val items = mutableListOf<ReceiveModule.AdditionalData>()

        memo?.let {
            items.add(
                ReceiveModule.AdditionalData.Memo(
                    value = it
                )
            )
        }

        amount?.let {
            items.add(
                ReceiveModule.AdditionalData.Amount(
                    value = it.toString()
                )
            )
        }

        return items
    }

    private fun getAlertText(hasMemo: Boolean): ReceiveModule.AlertText? {
        return if (hasMemo) ReceiveModule.AlertText.Critical(
            cash.p.terminal.strings.helpers.Translator.getString(R.string.Balance_Receive_AddressMemoAlert)
        )
        else null
    }

    fun onErrorClick() {
        setInitialData()
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
        emitState()
    }

    class Factory(private val cexAsset: CexAsset, private val network: CexDepositNetwork?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DepositAddressViewModel(cexAsset, network, App.cexProviderManager) as T
        }
    }
}
