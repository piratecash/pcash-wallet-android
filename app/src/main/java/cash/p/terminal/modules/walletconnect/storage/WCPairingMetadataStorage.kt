package cash.p.terminal.modules.walletconnect.storage

import cash.p.terminal.core.storage.AppDatabase
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Persists dApp metadata (name/url/icon) keyed by WalletConnect pairing topic. The SDK stores peer
 * metadata only on the (short-lived) session topic, so the pairings screen loses the name once the
 * session is disconnected. This store keeps it available for the bare pairing.
 */
class WCPairingMetadataStorage(
    appDatabase: AppDatabase,
    dispatcherProvider: DispatcherProvider,
) {

    private val dao: WCPairingMetadataDao by lazy {
        appDatabase.wcPairingMetadataDao()
    }

    // A single serialized lane over IO (limitedParallelism(1) serializes execution, no thread
    // affinity). Both save and reconcile run on it so an insert can never slip between a reconcile's
    // snapshot and its delete — otherwise a stale snapshot could drop a row just captured.
    private val reconcileLane = dispatcherProvider.io.limitedParallelism(1)

    // Blocks until the insert has run on the serialized lane, so the row exists before
    // onSessionProposal returns (the pairings screen, opened later, then always finds it) while
    // still being ordered against reconcile.
    fun save(topic: String, name: String, url: String, icon: String?) = runBlocking {
        withContext(reconcileLane) {
            dao.insert(
                WalletConnectV2PairingMetadata(topic = topic, name = name, url = url, icon = icon)
            )
        }
    }

    fun getByTopics(topics: List<String>): List<WalletConnectV2PairingMetadata> {
        return dao.getByTopics(topics)
    }

    suspend fun reconcile(currentTopics: () -> List<String>) = withContext(reconcileLane) {
        dao.deleteAllExcept(currentTopics())
    }

}
