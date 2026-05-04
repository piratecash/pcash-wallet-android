package cash.p.terminal.core.notifications.polling

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.notifications.TransactionMonitor
import cash.p.terminal.core.notifications.TransactionNotificationManager
import cash.p.terminal.modules.premium.settings.PollingInterval
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import io.horizontalsystems.core.BackgroundManager
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Drives interval-based push notifications via WorkManager. One worker invocation
 * runs a single poll cycle and chains the next [OneTimeWorkRequest] with the
 * user-selected interval as initial delay. Avoids the Android 14+ 6h cap that
 * applies to dataSync foreground services.
 */
class TransactionPollingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val transactionMonitor: TransactionMonitor by lazy { getKoinInstance() }
    private val checkPremiumUseCase: CheckPremiumUseCase by lazy { getKoinInstance() }
    private val notificationManager: TransactionNotificationManager by lazy { getKoinInstance() }
    private val localStorage: ILocalStorage by lazy { getKoinInstance() }
    private val backgroundManager: BackgroundManager by lazy { getKoinInstance() }

    override suspend fun doWork(): Result {
        if (!shouldRun()) {
            Timber.tag(TAG).d("Conditions no longer met, stopping chain")
            return Result.success()
        }

        try {
            transactionMonitor.pollOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Polling cycle failed")
        }

        if (shouldRun()) {
            // APPEND, not REPLACE: we are the running work for this unique name.
            // REPLACE has external-restart semantics — it would cancel us. APPEND
            // queues the next cycle as a continuation that fires after we finish.
            enqueueChain(applicationContext, currentIntervalMinutes(), ExistingWorkPolicy.APPEND)
        }
        return Result.success()
    }

    private fun shouldRun(): Boolean {
        // Foreground means the coordinator owns lifecycle; orphan chain must die.
        if (backgroundManager.inForeground) return false
        val interval = localStorage.pushPollingInterval
        if (interval == PollingInterval.REALTIME) return false
        if (!checkPremiumUseCase.getPremiumType().isPremium()) return false
        if (!localStorage.pushNotificationsEnabled) return false
        if (localStorage.pushEnabledBlockchainUids.isEmpty()) return false
        if (!notificationManager.hasNotificationPermission()) return false
        if (!notificationManager.isTransactionChannelEnabled()) return false
        return true
    }

    private fun currentIntervalMinutes(): Long = localStorage.pushPollingInterval.minutes

    companion object {
        const val TAG = "TxPollingWorker"
        private const val UNIQUE_WORK_NAME = "transaction_polling_worker"

        // External entry point: fresh chain or restart with new interval.
        // REPLACE atomically cancels any prior chain so we own the lifecycle.
        fun start(context: Context, intervalMinutes: Long) {
            enqueueChain(context, intervalMinutes, ExistingWorkPolicy.REPLACE)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        private fun enqueueChain(
            context: Context,
            intervalMinutes: Long,
            policy: ExistingWorkPolicy,
        ) {
            // Polling needs network; without it the request errors out and burns a cycle.
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<TransactionPollingWorker>()
                .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                request,
            )
        }
    }
}
