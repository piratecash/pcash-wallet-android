package cash.p.terminal.core.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.android.inject
import timber.log.Timber

class TransactionNotificationService : Service() {

    private val notificationManager: TransactionNotificationManager by inject()
    private val transactionMonitor: TransactionMonitor by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            TransactionNotificationManager.SERVICE_NOTIFICATION_ID,
            notificationManager.buildServiceNotification(),
        )

        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }

            ACTION_UPDATE -> {
                transactionMonitor.stop()
                transactionMonitor.start(serviceScope)
            }

            else -> {
                transactionMonitor.onPremiumExpired = { stopMonitoring() }
                transactionMonitor.start(serviceScope)
            }
        }

        return START_STICKY
    }

    // Android 15+ enforces a 6h/24h cumulative cap on dataSync foreground services
    // and invokes onTimeout shortly before throwing ForegroundServiceDidNotStopInTimeException.
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.w("Foreground service timeout (type=%d), stopping", fgsType)
        stopMonitoring()
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopMonitoring() {
        transactionMonitor.onPremiumExpired = null
        transactionMonitor.stop()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val ACTION_STOP = "cash.p.terminal.STOP_MONITORING"
        private const val ACTION_UPDATE = "cash.p.terminal.UPDATE_MONITORING"

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

        fun update(context: Context) {
            try {
                val intent = Intent(context, TransactionNotificationService::class.java).apply {
                    action = ACTION_UPDATE
                }
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update TransactionNotificationService")
            }
        }
    }
}
