package cash.p.terminal.modules.send

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.launch

abstract class BaseSendViewModel<T>(
    val wallet: Wallet,
    adapterManager: IAdapterManager
) : ViewModelUiState<T>() {
    var isSynced by mutableStateOf(true)
        private set

    init {
        adapterManager.getBalanceAdapterForWallet(wallet)?.let { adapter ->
            isSynced = adapter.balanceState is AdapterState.Synced
            viewModelScope.launch {
                adapter.balanceStateUpdatedFlow.collect {
                    isSynced = adapter.balanceState is AdapterState.Synced
                }
            }
        }
    }
}
