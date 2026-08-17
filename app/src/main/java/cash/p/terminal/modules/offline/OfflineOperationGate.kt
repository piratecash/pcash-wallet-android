package cash.p.terminal.modules.offline

import cash.p.terminal.R
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType

/**
 * Availability of a user operation. Offline is kept apart from plain unavailability so the UI can
 * keep the control clickable and route the click to the recovery sheet instead of greying it out.
 */
enum class OperationAvailability {
    Available,
    BlockedOffline,
    Unavailable;

    val clickable: Boolean get() = this != Unavailable
}

/** Refusal for paths that have no recovery UI of their own (WalletConnect, TON Connect, signing). */
class OfflineOperationBlockedException(blockchainName: String) : Exception(
    Translator.getString(R.string.offline_mode_operation_blocked, blockchainName)
)

/** The single place that decides whether an operation is blocked by offline mode. */
class OfflineOperationGate(private val offlineModeManager: OfflineModeManager) {

    /** Last cancellable checkpoint before an irreversible signature or broadcast. */
    fun requireOnline(wallet: Wallet) {
        if (isBlocked(wallet)) {
            throw OfflineOperationBlockedException(wallet.token.blockchain.name)
        }
    }

    fun isBlocked(wallet: Wallet): Boolean =
        isBlocked(wallet.account.id, wallet.token.blockchainType)

    fun isBlocked(account: Account, blockchainType: BlockchainType): Boolean =
        isBlocked(account.id, blockchainType)

    fun isBlocked(accountId: String, blockchainType: BlockchainType): Boolean =
        offlineModeManager.isNetworkPaused(accountId, blockchainType)

    fun availability(wallet: Wallet, enabled: Boolean): OperationAvailability =
        availability(enabled, isBlocked(wallet))

    companion object {
        /** Offline never enables anything: a base predicate that says no stays [Unavailable]. */
        fun availability(enabled: Boolean, offline: Boolean): OperationAvailability = when {
            !enabled -> OperationAvailability.Unavailable
            offline -> OperationAvailability.BlockedOffline
            else -> OperationAvailability.Available
        }
    }
}
