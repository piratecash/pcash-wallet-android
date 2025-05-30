package io.horizontalsystems.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

class BackgroundManager(application: Application) : Application.ActivityLifecycleCallbacks {

    private val scope = CoroutineScope(Executors.newFixedThreadPool(2).asCoroutineDispatcher())
    private val _stateFlow: MutableStateFlow<BackgroundManagerState> = MutableStateFlow(BackgroundManagerState.Unknown)
    val stateFlow: StateFlow<BackgroundManagerState>
        get() = _stateFlow

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    var currentActivity: WeakReference<Activity>? = null
        private set

    private var foregroundActivityCount: Int = 0
    private var aliveActivityCount: Int = 0

    @Synchronized
    override fun onActivityStarted(activity: Activity) {
        currentActivity = WeakReference(activity)
        if (foregroundActivityCount == 0) {
            scope.launch {
                _stateFlow.emit(BackgroundManagerState.EnterForeground)
            }
        }
        foregroundActivityCount++
    }

    @Synchronized
    override fun onActivityStopped(activity: Activity) {
        foregroundActivityCount--

        if (foregroundActivityCount == 0) {
            //App is in background
            scope.launch {
                _stateFlow.emit(BackgroundManagerState.EnterBackground)
            }
        }
    }

    @Synchronized
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivity = WeakReference(activity)
        aliveActivityCount++
    }

    @Synchronized
    override fun onActivityDestroyed(activity: Activity) {
        aliveActivityCount--

        if (aliveActivityCount == 0) {
            scope.launch {
                _stateFlow.emit(BackgroundManagerState.AllActivitiesDestroyed)
            }
        }

        if (currentActivity?.get() == activity) {
            currentActivity = null
        }
    }

    override fun onActivityPaused(p0: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

}

enum class BackgroundManagerState {
    Unknown,
    EnterForeground,
    EnterBackground,
    AllActivitiesDestroyed
}