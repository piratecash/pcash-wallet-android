package cash.p.terminal.core.usecase

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ZcashRescanException
import cash.p.terminal.core.managers.AdapterManager
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.domain.usecase.ClearZCashWalletDataUseCase
import cash.p.terminal.domain.usecase.ZcashEraseResult
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Clears local Zcash wallet data and restarts synchronization from [newHeight].
 */
class RescanZcashUseCase(
    private val adapterManager: AdapterManager,
    private val clearZCashWalletDataUseCase: ClearZCashWalletDataUseCase,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val localStorage: ILocalStorage,
    private val dispatcherProvider: DispatcherProvider,
) {
    // NonCancellable: the destructive section (erase → persist → adapter reconstruct) must run to
    // completion even if the caller's scope (e.g. the screen's viewModelScope) is cancelled midway,
    // otherwise the account could be left with a stopped/erased Zcash wallet and no restarted adapter.
    suspend operator fun invoke(account: Account, newHeight: Long) =
        withContext(dispatcherProvider.io + NonCancellable) {
            adapterManager.rescanZcashAccount(account.id) {
                // Only NONE (nothing erased) is a safe unchanged rollback. A PARTIAL erase has
                // already destroyed data, so it commits to the restore just like ALL — otherwise
                // the reconstruct-on-failure path would resume half-erased data as ExistingWallet.
                if (clearZCashWalletDataUseCase(account.id) == ZcashEraseResult.NONE) {
                    throw ZcashRescanException("Failed to erase Zcash wallet data for account ${account.id}")
                }

                // Persist the throwing DB write first; the SharedPreferences mutation below cannot
                // throw, so a failed save never leaves the init state half-applied for the
                // reconstruct-on-failure path. (Restored accounts already init as RestoreWallet and
                // honor restoreSettings.birthdayHeight, so no extra override flag is needed.)
                val restoreSettings = restoreSettingsManager.settings(account, BlockchainType.Zcash)
                restoreSettings.birthdayHeight = newHeight
                restoreSettingsManager.save(restoreSettings, account, BlockchainType.Zcash)

                localStorage.zcashAccountIds -= account.id
            }
        }
}
