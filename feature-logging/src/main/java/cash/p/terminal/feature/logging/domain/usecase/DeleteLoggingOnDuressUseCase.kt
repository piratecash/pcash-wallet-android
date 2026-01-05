package cash.p.terminal.feature.logging.domain.usecase

import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ILoginRecordRepository
import io.horizontalsystems.core.ILoggingSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Use case for deleting logging data when duress mode is activated.
 *
 * When entering duress mode, checks if deletion setting is ENABLED on previous level.
 * If ENABLED, deletes logging data from that level onwards.
 *
 * Example: Entering level 1 -> check getDeleteAllAuthDataOnDuressEnabled(0)
 *          If ENABLED (true), delete logging for levels 0..maxLevel,
 * Example: Entering level 2 -> check getDeleteAllAuthDataOnDuressEnabled(1)
 *          If ENABLED (true), delete logging for levels 1..maxLevel,
 * etc.
 */
class DeleteLoggingOnDuressUseCase(
    private val loggingSettings: ILoggingSettings,
    private val loginRecordRepository: ILoginRecordRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    /**
     * Triggers deletion of logging data for lower levels if the setting is ENABLED.
     * This operation runs asynchronously using application-scoped coroutine to ensure
     * completion even if the PIN unlock screen is closed quickly.
     *
     * @param userLevel The user level that was entered (must be > 0 for duress mode)
     */
    fun deleteLoggingForLowerLevelsIfEnabled(userLevel: Int) {
        // Only applies to duress levels (level > 0)
        if (userLevel <= 0) return

        val previousLevel = userLevel - 1
        if (!loggingSettings.getDeleteAllAuthDataOnDuressEnabled(previousLevel)) return

        // Use application scope to ensure deletion completes even after screen is closed
        dispatcherProvider.applicationScope.launch(CoroutineExceptionHandler {
            _, exception ->
            // Log the exception or handle it as needed
            Timber.e(exception, "Error deleting logging data on duress for level $previousLevel")
        }) {
            // deleteAll(level) deletes all records where userLevel >= level
            loginRecordRepository.deleteAll(previousLevel)
        }
    }
}
