package cash.p.terminal.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.tryOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration

class MarketWidgetWorker(
    private val context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    private val marketWidgetManager: MarketWidgetManager by lazy { getKoinInstance() }

    companion object {
        private const val updatePeriodMillis: Long = 15 * 60 * 1000 // 15 minutes
        private const val periodicWorkName = "widget_update_work"
        private const val refreshWorkName = "widget_refresh_work"
        private const val appWidgetIdKey = "app_widget_id"
        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueuePeriodicRefresh(context: Context) {
            val manager = WorkManager.getInstance(context)
            val requestBuilder = PeriodicWorkRequestBuilder<MarketWidgetWorker>(Duration.ofMillis(updatePeriodMillis))
                .setConstraints(networkConstraints)

            manager.enqueueUniquePeriodicWork(
                periodicWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                requestBuilder.build()
            )
        }

        fun enqueueRefresh(context: Context, glanceId: GlanceId) {
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            val requestBuilder = OneTimeWorkRequestBuilder<MarketWidgetWorker>()
                .setInputData(workDataOf(appWidgetIdKey to appWidgetId))
                .setConstraints(networkConstraints)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$refreshWorkName:$appWidgetId",
                ExistingWorkPolicy.KEEP,
                requestBuilder.build()
            )
        }

        fun cancel(context: Context) {
            if (!hasEnabledWidgets(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(periodicWorkName)
            }
        }

        fun hasEnabledWidgets(context: Context): Boolean {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = getWidgetIds(context, appWidgetManager)
            widgetIds.forEach { widgetId ->
                if (appWidgetManager.getAppWidgetInfo(widgetId) != null) {
                    return true
                }
            }
            return false
        }

        private fun getWidgetIds(context: Context, appWidgetManager: AppWidgetManager): IntArray {
            val widgetComponent = ComponentName(context, MarketWidgetReceiver::class.java)
            return appWidgetManager.getAppWidgetIds(widgetComponent)
        }
    }

    override suspend fun doWork(): Result = coroutineScope {
        val appWidgetId = inputData.getInt(appWidgetIdKey, AppWidgetManager.INVALID_APPWIDGET_ID)
        targetGlanceIds(appWidgetId).map { glanceId ->
            async {
                marketWidgetManager.refreshSync(
                    glanceId = glanceId,
                    showLoading = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                )
            }
        }.awaitAll()

        Result.success()
    }

    private suspend fun targetGlanceIds(appWidgetId: Int): List<GlanceId> {
        val manager = GlanceAppWidgetManager(context)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return manager.getGlanceIds(MarketWidget::class.java)
        }

        return listOfNotNull(tryOrNull { manager.getGlanceIdBy(appWidgetId) })
    }

}
