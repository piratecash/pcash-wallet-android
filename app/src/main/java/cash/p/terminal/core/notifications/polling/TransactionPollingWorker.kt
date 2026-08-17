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
import cash.p.terminal.core.managers.EffectiveMonitoredChains
import cash.p.terminal.core.notifications.TransactionMonitor
import cash.p.terminal.core.notifications.TransactionNotificationManager
import cash.p.terminal.modules.premium.settings.PollingInterval
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import io.horizontalsystems.core.BackgroundManager
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Inputs to [TransactionPollingWorker.evaluateShouldRun], bundled to stay under the parameter-count limit. */
internal data class ShouldRunGateInputs(
    val inForeground: Boolean,
    val interval: PollingInterval,
    val fallbackActive: Boolean,
    val isPremium: Boolean,
    val pushNotificationsEnabled: Boolean,
    val hasChainsToPoll: Boolean,
    val hasNotificationPermission: Boolean,
    val isTransactionChannelEnabled: Boolean,
)

/**
 * Drives interval-based push notifications via WorkManager. One worker invocation
 * runs a single poll cycle and chains the next [OneTimeWorkRequest] with the
 * user-selected interval as initial delay. The same worker is also the fallback
 * transport when Android 15 exhausts the dataSync foreground-service budget.
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
    private val effectiveMonitoredChains: EffectiveMonitoredChains by lazy { getKoinInstance() }

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

    private fun shouldRun(): Boolean = evaluateShouldRun(
        ShouldRunGateInputs(
            inForeground = backgroundManager.inForeground,
            interval = localStorage.pushPollingInterval,
            fallbackActive = localStorage.pushRealtimeFallbackPollingActive,
            isPremium = checkPremiumUseCase.getPremiumType().isPremium(),
            pushNotificationsEnabled = localStorage.pushNotificationsEnabled,
            hasChainsToPoll = effectiveMonitoredChains.chains().isNotEmpty(),
            hasNotificationPermission = notificationManager.hasNotificationPermission(),
            isTransactionChannelEnabled = notificationManager.isTransactionChannelEnabled(),
        )
    )

    private fun currentIntervalMinutes(): Long {
        val interval = localStorage.pushPollingInterval
        return if (interval == PollingInterval.REALTIME) {
            FALLBACK_INTERVAL_MINUTES
        } else {
            interval.minutes
        }
    }

    companion object {
        const val TAG = "TxPollingWorker"

        // Sole place that defines the FGS-timeout fallback cadence.
        val FALLBACK_INTERVAL_MINUTES: Long = PollingInterval.MIN_5.minutes

        private const val UNIQUE_WORK_NAME = "transaction_polling_worker"

        /**
         * Pure gating logic extracted from [shouldRun] so it is unit-testable without a
         * WorkManager test harness. Foreground means the coordinator owns lifecycle; an
         * orphan chain must die.
         */
        internal fun evaluateShouldRun(inputs: ShouldRunGateInputs): Boolean = with(inputs) {
            if (inForeground) return false
            val intervalAllows = interval != PollingInterval.REALTIME || fallbackActive
            return intervalAllows &&
                isPremium &&
                pushNotificationsEnabled &&
                hasChainsToPoll &&
                hasNotificationPermission &&
                isTransactionChannelEnabled
        }

        /**
         * External entry point: fresh chain or restart with new interval.
         * REPLACE atomically cancels any prior chain so we own the lifecycle.
         * Catches WorkManager initialization/IPC errors so callers running on
         * background threads (BackgroundManager pool) cannot crash the process.
         * Returns true if the worker was actually enqueued.
         */
        fun start(context: Context, intervalMinutes: Long): Boolean {
            return try {
                enqueueChain(context, intervalMinutes, ExistingWorkPolicy.REPLACE)
                true
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to start polling worker")
                false
            }
        }

        /**
         * Activates the FGS-timeout fallback: flips the persistence flag, then
         * enqueues the chain at the fallback interval. The flag MUST flip before
         * enqueue — the worker reads it in evaluateShouldRun and would
         * self-cancel on the first cycle when interval == REALTIME otherwise.
         * On enqueue failure the flag is rolled back so the next background
         * transition can retry from a clean state.
         */
        fun startFallback(context: Context, localStorage: ILocalStorage): Boolean {
            localStorage.pushRealtimeFallbackPollingActive = true
            return if (start(context, FALLBACK_INTERVAL_MINUTES)) {
                true
            } else {
                localStorage.pushRealtimeFallbackPollingActive = false
                false
            }
        }

        /**
         * Cancels the unique polling chain. Catches WorkManager errors so callers
         * (notably stopMonitoring on the foreground transition) can finish their
         * cleanup — flag clear, keep-alive clear, transport reset — even if
         * WorkManager itself is unhappy. Returns true on success.
         */
        fun cancel(context: Context): Boolean {
            return try {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                true
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to cancel polling worker chain")
                false
            }
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
