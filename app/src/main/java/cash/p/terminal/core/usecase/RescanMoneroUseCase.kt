package cash.p.terminal.core.usecase

import cash.p.terminal.core.MoneroRescanException
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.core.storage.MoneroFileDao
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.useCases.RemoveMoneroWalletFilesUseCase
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Clears local Monero wallet data and restarts synchronization from [newHeight].
 */
class RescanMoneroUseCase(
    private val moneroKitManager: MoneroKitManager,
    private val removeMoneroWalletFilesUseCase: RemoveMoneroWalletFilesUseCase,
    private val moneroFileDao: MoneroFileDao,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val offlineModeManager: OfflineModeManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    // NonCancellable: the destructive reset (stop → delete files → re-derive → restart) must not be
    // interrupted by caller-scope cancellation (e.g. leaving the screen), which could otherwise leave
    // the wallet stopped with its files removed and never restarted.
    suspend operator fun invoke(account: Account, newHeight: Long) =
        withContext(dispatcherProvider.io + NonCancellable) {
            if (offlineModeManager.isNetworkPaused(account.id, BlockchainType.Monero)) {
                throw MoneroRescanException("Cannot rescan Monero wallet for account ${account.id} while offline")
            }
            if (moneroKitManager.rescanIfActive(account, newHeight)) return@withContext

            resetInactiveWallet(account, newHeight)
        }

    // Same fail-loud ordering as MoneroKitWrapper.resetWalletAndRestart: require the old files to be
    // gone before deleting the DAO record and committing the new height, so a failed removal can
    // never leave a stale wallet file behind a claimed new height.
    private suspend fun resetInactiveWallet(account: Account, newHeight: Long) {
        val removed = removeMoneroWalletFilesUseCase(account)
        if (!removed) {
            throw MoneroRescanException("Failed to remove Monero wallet files for account ${account.id}")
        }
        moneroFileDao.deleteAssociatedRecord(account.id)
        val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Monero)
        restoreSettings.birthdayHeight = newHeight
        restoreSettingsManager.save(restoreSettings, account, BlockchainType.Monero)
    }
}
