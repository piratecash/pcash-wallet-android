package cash.p.terminal.core.notifications

import android.app.Application
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.BackgroundKeepAliveManager
import cash.p.terminal.core.notifications.polling.TransactionPollingWorker
import cash.p.terminal.modules.premium.settings.PollingInterval
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.wallet.IWalletManager
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class TransactionNotificationCoordinator(
    private val application: Application,
    private val localStorage: ILocalStorage,
    private val notificationManager: TransactionNotificationManager,
    private val backgroundManager: BackgroundManager,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val keepAliveManager: BackgroundKeepAliveManager,
    private val walletManager: IWalletManager,
    private val transactionMonitor: TransactionMonitor,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    @Volatile
    private var activeTransport: Transport = Transport.None

    fun start() {
        // We must NOT cancel the polling chain here: WorkManager wakes the
        // process to run the worker, which triggers Application.onCreate and
        // therefore start(). Cancelling on every start would kill the worker
        // that just woke us. Reconciliation lives in two cheaper places:
        //  - EnterForeground unconditionally cancels (UI is back, we own state).
        //  - Worker checks backgroundManager.inForeground in shouldRun().
        backgroundManager.onBeforeEnterBackground = {
            if (shouldStartMonitoring()) {
                startMonitoring()
            }
        }

        scope.launch {
            backgroundManager.stateFlow.collect { state ->
                if (state == BackgroundManagerState.EnterForeground) {
                    stopMonitoring()
                }
            }
        }
    }

    private fun shouldStartMonitoring(): Boolean {
        if (!checkPremiumUseCase.getPremiumType().isPremium()) return false
        if (!localStorage.pushNotificationsEnabled) return false
        if (localStorage.pushEnabledBlockchainUids.isEmpty()) return false
        if (!notificationManager.hasNotificationPermission()) return false
        if (!notificationManager.isTransactionChannelEnabled()) return false
        // Service channel only matters for the realtime FGS path.
        if (localStorage.pushPollingInterval == PollingInterval.REALTIME &&
            !notificationManager.isServiceChannelEnabled()
        ) {
            return false
        }
        return true
    }

    private fun startMonitoring() {
        val interval = localStorage.pushPollingInterval
        if (interval == PollingInterval.REALTIME) {
            startRealtime()
        } else {
            startPolling(interval)
        }
    }

    private fun startRealtime() {
        val enabledUids = localStorage.pushEnabledBlockchainUids
        val monitoredTypes = walletManager.activeWallets
            .map { it.token.blockchainType }
            .filter { it.uid in enabledUids }
            .toSet()
        keepAliveManager.setKeepAlive(monitoredTypes)

        Timber.d("Starting realtime transaction notification service")
        if (TransactionNotificationService.start(application)) {
            activeTransport = Transport.Service
        } else {
            Timber.w("Realtime transaction notification service did not start, switching to polling fallback")
            keepAliveManager.setKeepAlive(emptySet())
            val fallbackStarted = startWorkerWithBaseline {
                TransactionPollingWorker.startFallback(application, localStorage)
            }
            activeTransport = if (fallbackStarted) {
                Transport.Worker
            } else {
                Transport.None
            }
        }
    }

    private fun startPolling(interval: PollingInterval) {
        // Each poll cycle brings up adapters via startForPolling/stopForPolling.
        keepAliveManager.setKeepAlive(emptySet())

        Timber.d("Scheduling polling worker, interval=%dm", interval.minutes)
        val workerStarted = startWorkerWithBaseline {
            TransactionPollingWorker.start(application, interval.minutes)
        }
        activeTransport = if (workerStarted) {
            Transport.Worker
        } else {
            Transport.None
        }
    }

    private fun startWorkerWithBaseline(startWorker: () -> Boolean): Boolean {
        val started = startWorker()
        if (started) {
            transactionMonitor.resetPollingBaseline()
        }
        return started
    }

    private fun stopMonitoring() {
        // If the realtime FGS timed out and handed off to the worker, the
        // service is dead; sending ACTION_STOP would just spawn a fresh
        // throwaway Service instance. The flag is the source of truth here
        // because activeTransport is not updated when the timeout happens
        // out-of-band inside the service process.
        val fallbackActive = localStorage.pushRealtimeFallbackPollingActive
        if (activeTransport == Transport.Service && !fallbackActive) {
            Timber.d("Stopping realtime transaction notification service")
            TransactionNotificationService.stop(application)
        }
        // Always cancel the polling chain — covers orphan chains from a previous
        // process where activeTransport was lost on cold start.
        Timber.d("Cancelling polling worker chain")
        TransactionPollingWorker.cancel(application)
        localStorage.pushRealtimeFallbackPollingActive = false
        keepAliveManager.clear()
        activeTransport = Transport.None
    }

    private enum class Transport { None, Service, Worker }
}
