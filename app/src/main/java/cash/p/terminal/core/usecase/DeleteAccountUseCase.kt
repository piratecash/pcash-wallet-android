package cash.p.terminal.core.usecase

import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import timber.log.Timber

class DeleteAccountUseCase(
    private val accountManager: IAccountManager,
    private val moneroKitManager: MoneroKitManager,
    private val adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend operator fun invoke(account: Account) = start(account).await()
    fun start(account: Account): Deferred<Unit> {
        val deletion = dispatcherProvider.applicationScope.async { deleteAccount(account) }
        deletion.invokeOnCompletion { error ->
            if (error != null && error !is CancellationException) {
                Timber.e(error, "Failed to delete account")
            }
        }
        return deletion
    }
    private suspend fun deleteAccount(account: Account) {
        if (account.type !is AccountType.TrezorDevice) return deleteAccountWithRetry(account)
        moneroKitManager.deleteForAccount(
            account = account,
            stopAdapters = { adapterManager.stopAdapters(listOf(account.id), BlockchainType.Monero) },
            deleteAccount = { deleteAccountWithRetry(account) },
        )
    }
    private suspend fun deleteAccountWithRetry(account: Account) {
        try {
            accountManager.delete(account.id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            accountManager.delete(account.id)
        }
    }
}
