package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.entities.BlockchainType

/**
 * Single source of truth for "chains the notification/polling subsystem may touch": the user's
 * saved push-enabled set, minus chains paused for the active account. Read-only — never mutates
 * [ILocalStorage.pushEnabledBlockchainUids].
 */
class EffectiveMonitoredChains(
    private val localStorage: ILocalStorage,
    private val accountManager: IAccountManager,
    private val offlineModeManager: OfflineModeManager,
) {
    fun chains(): Set<BlockchainType> {
        val accountId = accountManager.activeAccount?.id ?: return emptySet()
        return localStorage.pushEnabledBlockchainUids
            .map { BlockchainType.fromUid(it) }
            .filterNot { offlineModeManager.isNetworkPaused(accountId, it) }
            .toSet()
    }
}
