package cash.p.terminal.modules.walletconnect.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.ui_compose.entities.DataState
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.core.ViewModelUiState

class WCRequestPreViewModel : ViewModelUiState<DataState<WCRequestPreUiState>>() {
    private val sessionRequest = WCDelegate.sessionRequestEvent
    private val wcAction = App.wcManager.getActionForRequest(sessionRequest)

    override fun createState() = when {
        sessionRequest == null -> {
            DataState.Error(Exception("No request"))
        }

        wcAction == null -> {
            DataState.Error(Exception("No action for request"))
        }

        else -> {
            DataState.Success(
                WCRequestPreUiState(
                    wcAction = wcAction,
                    sessionRequest = sessionRequest,
                )
            )
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WCRequestPreViewModel() as T
        }
    }
}

data class WCRequestPreUiState(
    val wcAction: AbstractWCAction,
    val sessionRequest: Wallet.Model.SessionRequest
)