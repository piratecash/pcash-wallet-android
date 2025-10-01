package cash.p.terminal.modules.tonconnect

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.wallet.IAccountManager
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TonConnectListViewModel(
    accountManager: IAccountManager
) : ViewModelUiState<TonConnectListUiState>() {

    private val tonConnectKit = App.tonConnectManager.kit

    private var dapps: Map<String, List<DAppEntity>> = emptyMap()
    private var dAppRequestEntity: DAppRequestEntity? = null
    private var error: Throwable? = null

    private val accountNamesById = accountManager.accounts.associate { it.id to it.name }

    override fun createState() = TonConnectListUiState(
        dapps = dapps,
        dAppRequestEntity = dAppRequestEntity,
        error = error
    )

    init {
        viewModelScope.launch {
            tonConnectKit.getDApps().collect {
                dapps = it.groupBy { entity ->
                    accountNamesById.getOrDefault(
                        entity.walletId,
                        entity.walletId
                    )
                }
                emitState()
            }
        }
    }

    fun setConnectionUri(v: String) {
        error = null

        try {
            dAppRequestEntity = tonConnectKit.readData(v)
        } catch (e: Throwable) {
            error = e
        }
        emitState()
    }

    fun onDappRequestHandled() {
        dAppRequestEntity = null
        emitState()
    }

    fun onErrorHandled() {
        error = null
        emitState()
    }

    fun disconnect(dapp: DAppEntity) {
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            this.error = error
            emitState()
        }) {
            tonConnectKit.disconnect(dapp)
        }
    }

}


data class TonConnectListUiState(
    val dapps: Map<String, List<DAppEntity>>,
    val dAppRequestEntity: DAppRequestEntity?,
    val error: Throwable?
)