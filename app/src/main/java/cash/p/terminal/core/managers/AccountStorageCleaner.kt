package cash.p.terminal.core.managers

import cash.p.terminal.core.usecase.OfflineModeUseCase
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/** App-database rows keyed by account id, wiped together when accounts are deleted. */
class AccountStorageCleaner(
    private val locallyCreatedTransactionRepository: LocallyCreatedTransactionRepository,
    private val offlineModeUseCase: OfflineModeUseCase,
) {
    /**
     * Every table is attempted, but the first failure is rethrown: the caller drops its
     * deleted-account records only on success, and those records are the retry list.
     */
    suspend fun clearAccounts(accountIds: List<String>) {
        val failures = listOfNotNull(
            failureOf("locally created transactions") {
                locallyCreatedTransactionRepository.deleteByAccountIds(accountIds)
            },
            failureOf("offline mode rows") {
                offlineModeUseCase.forgetAccounts(accountIds)
            },
        )
        failures.firstOrNull()?.let { throw it }
    }

    /** One failing table must not stop the others from being attempted. */
    private suspend fun failureOf(what: String, block: suspend () -> Unit): Exception? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Failed to clear $what of deleted accounts")
        e
    }
}
