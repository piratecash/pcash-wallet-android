package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.OfflineModeStorage
import cash.p.terminal.entities.OfflineBlockchain
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

data class OfflineKey(val accountId: String, val blockchainType: BlockchainType)

/**
 * Owns the offline mode of every (account, blockchain) pair: the persisted flag, the two temporary
 * overrides that outrank it, and the "last synced" stamp shown while the network is paused.
 */
class OfflineModeManager(
    private val storage: OfflineModeStorage,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val writeMutex = Mutex()

    private val persisted = MutableStateFlow<Map<OfflineKey, OfflineBlockchain>>(
        storage.getAll().associateBy { OfflineKey(it.accountId, it.blockchainType) }
    )
    private val inFlight = MutableStateFlow<Map<OfflineKey, Set<Long>>>(emptyMap())
    private val temporaryOnline = MutableStateFlow<Map<OfflineKey, Set<Long>>>(emptyMap())

    /** Accounts whose rows are being wiped; every write for them is dropped until they are saved again. */
    private val forgotten = AtomicReference(emptySet<String>())

    /** Synced/not-synced baseline per live adapter. A wallet missing here has no adapter to trust. */
    private val syncedByWallet = AtomicReference(emptyMap<Wallet, SyncBaseline>())

    val stateFlow: StateFlow<Map<OfflineKey, OfflineBlockchain>> = persisted.asStateFlow()

    val effectiveFlow: Flow<Set<OfflineKey>> =
        combine(persisted, inFlight, temporaryOnline) { rows, transitions, overrides ->
            val paused = rows.filterValues { it.offline }.keys + transitions.keys
            paused - overrides.keys
        }.distinctUntilChanged()

    fun isNetworkPaused(key: OfflineKey): Boolean {
        if (temporaryOnline.value.containsKey(key)) return false
        return inFlight.value.containsKey(key) || persisted.value[key]?.offline == true
    }

    fun lastSyncedAt(key: OfflineKey): Long? = persisted.value[key]?.lastSyncedAt

    fun isForgotten(accountId: String): Boolean = accountId in forgotten.get()

    /** Records the pre-start sync state of a freshly created adapter. Never suspends, so the
     * caller can install its collector without losing an emission. */
    fun onSubscribed(wallet: Wallet, adapter: IBalanceAdapter, state: AdapterState?) {
        syncedByWallet.updateAndGet {
            it + (wallet to SyncBaseline(adapter, state is AdapterState.Synced))
        }
    }

    /** Seeds the date from the locally stored block time when nothing was ever stamped for the pair. */
    suspend fun seedLastSynced(wallet: Wallet, lastBlockTimestampSec: Long?) {
        if (lastBlockTimestampSec == null) return
        writeLastSynced(
            key = wallet.offlineKey,
            millis = lastBlockTimestampSec * MILLIS_PER_SECOND,
            skipIfPresent = true,
        )
    }

    /** Stamps the pair only on a not-synced → synced edge, and only while its network is live. */
    suspend fun onBalanceState(wallet: Wallet, adapter: IBalanceAdapter, state: AdapterState?) {
        if (!markSynced(wallet, adapter, state is AdapterState.Synced)) return

        val key = wallet.offlineKey
        if (isNetworkPaused(key)) return

        writeLastSynced(key, System.currentTimeMillis(), skipIfPresent = false)
    }

    fun onAdapterGone(wallet: Wallet) {
        syncedByWallet.updateAndGet { it - wallet }
    }

    suspend fun persistAndPublish(accountId: String, blockchainType: BlockchainType, offline: Boolean) {
        val key = OfflineKey(accountId, blockchainType)
        writeMutex.withLock {
            if (isForgotten(accountId)) return@withLock

            val existing = persisted.value[key]
            val enabledAt = if (offline) System.currentTimeMillis() else existing?.enabledAt
            withContext(dispatcherProvider.io) {
                storage.setMode(accountId, blockchainType, offline, enabledAt)
            }
            persisted.value = persisted.value + (key to OfflineBlockchain(
                accountId = accountId,
                blockchainType = blockchainType,
                offline = offline,
                enabledAt = enabledAt,
                lastSyncedAt = existing?.lastSyncedAt,
            ))
        }
    }

    suspend fun forgetAccounts(accountIds: List<String>) {
        accountIds.forEach { forgetAccount(it) }
    }

    private suspend fun forgetAccount(accountId: String) {
        forgotten.updateAndGet { it + accountId }
        writeMutex.withLock {
            persisted.value = persisted.value.filterKeys { it.accountId != accountId }
            inFlight.value = inFlight.value.filterKeys { it.accountId != accountId }
            temporaryOnline.value = temporaryOnline.value.filterKeys { it.accountId != accountId }
            withContext(dispatcherProvider.io) {
                storage.deleteByAccount(accountId)
            }
        }
    }

    /**
     * Drops the pair's row once its blockchain leaves the wallet. The whole row goes, not just the
     * flag: a chain re-added weeks later must start online and without a stale "last synced" date.
     */
    suspend fun resetChain(accountId: String, blockchainType: BlockchainType) {
        val key = OfflineKey(accountId, blockchainType)
        writeMutex.withLock {
            if (persisted.value[key] == null) return@withLock
            withContext(dispatcherProvider.io) {
                storage.deleteChain(accountId, blockchainType)
            }
            persisted.value = persisted.value - key
        }
    }

    suspend fun beginTransition(key: OfflineKey, token: Long) =
        writeMutex.withLock { inFlight.value = inFlight.value.withToken(key, token) }

    suspend fun endTransition(key: OfflineKey, token: Long) =
        writeMutex.withLock { inFlight.value = inFlight.value.withoutToken(key, token) }

    suspend fun enterTemporaryOnline(key: OfflineKey, token: Long) =
        writeMutex.withLock { temporaryOnline.value = temporaryOnline.value.withToken(key, token) }

    suspend fun exitTemporaryOnline(key: OfflineKey, token: Long) =
        writeMutex.withLock { temporaryOnline.value = temporaryOnline.value.withoutToken(key, token) }

    /**
     * Compare-and-write against the live-adapter baseline: an emission from an adapter that is gone —
     * or replaced by a newer one for the same wallet — finds no matching record and is dropped.
     */
    private fun markSynced(wallet: Wallet, adapter: IBalanceAdapter, synced: Boolean): Boolean {
        var transitioned = false
        syncedByWallet.updateAndGet { current ->
            val baseline = current[wallet]?.takeIf { it.adapter === adapter }
            transitioned = baseline != null && synced && !baseline.synced
            if (baseline == null) current else current + (wallet to baseline.copy(synced = synced))
        }
        return transitioned
    }

    /** The tombstone check, the row write and its publication share one critical section. */
    private suspend fun writeLastSynced(key: OfflineKey, millis: Long, skipIfPresent: Boolean) {
        writeMutex.withLock {
            if (isForgotten(key.accountId)) return@withLock
            val existing = persisted.value[key]
            if (skipIfPresent && existing?.lastSyncedAt != null) return@withLock

            try {
                withContext(dispatcherProvider.io) {
                    storage.setLastSynced(key.accountId, key.blockchainType, millis)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to store last synced date for ${key.blockchainType.uid}")
                return@withLock
            }
            persisted.value = persisted.value + (key to (existing?.copy(lastSyncedAt = millis)
                ?: OfflineBlockchain(
                    accountId = key.accountId,
                    blockchainType = key.blockchainType,
                    offline = false,
                    enabledAt = null,
                    lastSyncedAt = millis,
                )))
        }
    }

    private data class SyncBaseline(val adapter: IBalanceAdapter, val synced: Boolean)

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

private val Wallet.offlineKey: OfflineKey
    get() = OfflineKey(account.id, token.blockchainType)

// Top-level so the class's member count stays under detekt's TooManyFunctions threshold.
fun OfflineModeManager.isNetworkPaused(accountId: String, blockchainType: BlockchainType): Boolean =
    isNetworkPaused(OfflineKey(accountId, blockchainType))

fun OfflineModeManager.isNetworkPaused(account: Account, blockchainType: BlockchainType): Boolean =
    isNetworkPaused(account.id, blockchainType)

fun OfflineModeManager.lastSyncedAt(accountId: String, blockchainType: BlockchainType): Long? =
    lastSyncedAt(OfflineKey(accountId, blockchainType))

private fun Map<OfflineKey, Set<Long>>.withToken(key: OfflineKey, token: Long) =
    this + (key to (this[key].orEmpty() + token))

private fun Map<OfflineKey, Set<Long>>.withoutToken(
    key: OfflineKey,
    token: Long,
): Map<OfflineKey, Set<Long>> {
    val remaining = this[key]?.minus(token) ?: return this
    return if (remaining.isEmpty()) this - key else this + (key to remaining)
}
