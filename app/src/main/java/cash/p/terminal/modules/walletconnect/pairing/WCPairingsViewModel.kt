package cash.p.terminal.modules.walletconnect.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reown.android.Core
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.modules.walletconnect.storage.WCPairingMetadataStorage
import cash.p.terminal.modules.walletconnect.storage.WalletConnectV2PairingMetadata
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WCPairingsViewModel(
    private val pairingMetadataStorage: WCPairingMetadataStorage,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModelUiState<WCPairingsUiState>() {

    // null until the first off-main load completes, so the screen does not close-on-empty while loading.
    private var pairings: List<PairingViewItem>? = null

    init {
        // A single collector so refreshes run one at a time — concurrent loads could otherwise
        // publish a stale snapshot. flowOf(Unit) drives the initial load from inside the same
        // collection, so the event subscriptions are established before the first snapshot and no
        // proposal is missed. Reacts to pairing changes and to proposals captured while this screen
        // is already alive (e.g. underneath the proposal sheet), replacing the unnamed placeholder.
        viewModelScope.launch {
            merge(
                flowOf(Unit),
                WCDelegate.pairingEvents,
                WCDelegate.walletEvents.filterIsInstance<Wallet.Model.SessionProposal>().map { },
            ).collect {
                reconcileAndLoad()
            }
        }
    }

    override fun createState() = WCPairingsUiState(
        pairings = pairings.orEmpty(),
        closeScreen = pairings?.isEmpty() == true,
    )

    private suspend fun reconcileAndLoad() {
        reconcile()
        load()
    }

    private suspend fun load() {
        val items = withContext(dispatcherProvider.io) { buildPairings() }
        pairings = items
        emitState()
    }

    private fun buildPairings(): List<PairingViewItem> {
        val corePairings = WCDelegate.getPairings()
        val storedByTopic = pairingMetadataStorage
            .getByTopics(corePairings.map { it.topic })
            .associateBy { it.topic }
        return corePairings.map { getPairingViewItem(it, storedByTopic[it.topic]) }
    }

    private fun getPairingViewItem(
        pairing: Core.Model.Pairing,
        fallback: WalletConnectV2PairingMetadata?,
    ): PairingViewItem {
        val metaData = pairing.peerAppMetaData

        return PairingViewItem(
            icon = metaData?.icons?.lastOrNull()?.ifBlank { null } ?: fallback?.icon,
            name = metaData?.name?.ifBlank { null } ?: fallback?.name,
            url = metaData?.url?.ifBlank { null } ?: fallback?.url,
            topic = pairing.topic
        )
    }

    // Keep the metadata store bounded: drop rows for pairings the SDK no longer reports.
    // A failed async disconnect leaves the pairing present, so its fallback metadata is retained.
    // The snapshot is taken inside the storage's serialized lane (not here) so a concurrent proposal
    // capture cannot be clobbered by a stale snapshot.
    private suspend fun reconcile() {
        pairingMetadataStorage.reconcile { WCDelegate.getPairings().map { it.topic } }
    }

    fun delete(pairing: PairingViewItem) {
        WCDelegate.deletePairing(topic = pairing.topic)
    }

    fun deleteAll() {
        WCDelegate.deleteAllPairings()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WCPairingsViewModel(getKoinInstance(), getKoinInstance()) as T
        }
    }
}

data class PairingViewItem(
    val icon: String?,
    val name: String?,
    val url: String?,
    val topic: String
)

data class WCPairingsUiState(
    val pairings: List<PairingViewItem>,
    val closeScreen: Boolean
)
