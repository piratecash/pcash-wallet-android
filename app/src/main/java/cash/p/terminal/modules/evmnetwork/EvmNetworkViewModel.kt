package cash.p.terminal.modules.evmnetwork

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.uris
import cash.p.terminal.entities.EvmSyncSource
import io.horizontalsystems.core.entities.Blockchain
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class EvmNetworkViewModel(
    val blockchain: Blockchain,
    private val evmSyncSourceManager: EvmSyncSourceManager
) : ViewModel() {

    private var currentSyncSource = evmSyncSourceManager.getSyncSource(blockchain.type)

    var viewState by mutableStateOf(ViewState(emptyList(), emptyList()))
        private set

    val title: String = blockchain.name

    init {
        evmSyncSourceManager.syncSourcesUpdatedFlow
            .onEach { syncState() }
            .launchIn(viewModelScope)

        syncState()
    }

    private fun syncState() {
        viewState = ViewState(
            defaultItems = viewItems(evmSyncSourceManager.defaultSyncSources(blockchain.type)),
            customItems = viewItems(evmSyncSourceManager.customSyncSources(blockchain.type))
        )
    }

    private fun viewItems(evmSyncSources: List<EvmSyncSource>): List<ViewItem> {
        currentSyncSource = evmSyncSourceManager.getSyncSource(blockchain.type)
        return evmSyncSources.map { evmSyncSource ->
            val url = if (evmSyncSource.rpcSource.uris.size == 1)
                evmSyncSource.rpcSource.uris.first().toString()
            else
                cash.p.terminal.strings.helpers.Translator.getString(R.string.NetworkSettings_SwithesAutomatically)

            val currentSyncSourceId = currentSyncSource.id

            ViewItem(
                syncSource = evmSyncSource,
                id = evmSyncSource.id,
                name = evmSyncSource.name,
                url = url,
                selected = evmSyncSource.id == currentSyncSourceId
            )
        }
    }

    fun onSelectSyncSource(syncSource: EvmSyncSource) {
        if (currentSyncSource == syncSource) return

        evmSyncSourceManager.save(syncSource, blockchain.type)

        syncState()
    }

    fun onRemoveCustomRpc(syncSource: EvmSyncSource) {
        evmSyncSourceManager.delete(syncSource, blockchain.type)
    }

    data class ViewItem(
        val syncSource: EvmSyncSource,
        val id: String,
        val name: String,
        val url: String,
        val selected: Boolean,
    )

    data class ViewState(
        val defaultItems: List<ViewItem>,
        val customItems: List<ViewItem>,
    )
}
