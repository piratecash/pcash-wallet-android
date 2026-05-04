package cash.p.terminal.core.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.notifications.polling.TransactionPollingWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.android.inject
import timber.log.Timber

class TransactionNotificationService : Service() {

    private val notificationManager: TransactionNotificationManager by inject()
    private val transactionMonitor: TransactionMonitor by inject()
    private val localStorage: ILocalStorage by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var foregroundStarted = false
    private var stopped = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A stopped instance should not accept new work; later starts get a fresh Service instance.
        if (stopped) return START_NOT_STICKY

        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        try {
            startForeground(
                TransactionNotificationManager.SERVICE_NOTIFICATION_ID,
                notificationManager.buildServiceNotification(),
            )
            foregroundStarted = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to enter foreground for transaction notifications")
            // startForeground failed before monitor.start, so resetting the polling baseline is safe.
            activateFallback(resetBaseline = true)
            stopMonitoring()
            return START_NOT_STICKY
        }

        transactionMonitor.onPremiumExpired = { stopMonitoring() }
        transactionMonitor.start(serviceScope)

        return START_STICKY
    }

    // Android 15+ enforces a 6h/24h cumulative cap on dataSync foreground services
    // and invokes onTimeout shortly before throwing ForegroundServiceDidNotStopInTimeException.
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        if (stopped) return
        Timber.w("Foreground service timeout (type=%d), switching to polling fallback", fgsType)
        activateFallback(resetBaseline = false)
        stopMonitoring()
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopMonitoring() {
        if (stopped) return
        stopped = true
        transactionMonitor.onPremiumExpired = null
        transactionMonitor.stop()
        serviceScope.cancel()
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    private fun activateFallback(resetBaseline: Boolean) {
        if (resetBaseline) {
            transactionMonitor.resetPollingBaseline()
        }
        TransactionPollingWorker.startFallback(applicationContext, localStorage)
    }

    companion object {
        private const val ACTION_STOP = "cash.p.terminal.STOP_MONITORING"

        fun start(context: Context): Boolean {
            try {
                val intent = Intent(context, TransactionNotificationService::class.java)
                context.startForegroundService(intent)
                return true
            } catch (e: Exception) {
                Timber.e(e, "Failed to start TransactionNotificationService")
                return false
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, TransactionNotificationService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop TransactionNotificationService")
            }
        }
    }
}
