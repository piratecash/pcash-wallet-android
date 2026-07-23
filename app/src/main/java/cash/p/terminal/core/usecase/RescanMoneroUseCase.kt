package cash.p.terminal.core.usecase

import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Clears local Monero wallet data and restarts synchronization from [newHeight].
 */
class RescanMoneroUseCase(
    private val moneroKitManager: MoneroKitManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    // NonCancellable: the destructive reset (stop → delete files → re-derive → restart) must not be
    // interrupted by caller-scope cancellation (e.g. leaving the screen), which could otherwise leave
    // the wallet stopped with its files removed and never restarted.
    suspend operator fun invoke(account: Account, newHeight: Long) =
        withContext(dispatcherProvider.io + NonCancellable) {
            moneroKitManager.rescan(account, newHeight)
        }
}
