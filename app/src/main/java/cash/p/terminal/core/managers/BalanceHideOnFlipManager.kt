package cash.p.terminal.core.managers

import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.ui_compose.components.HudHelper
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Policy owner for the "hide balance on flip" feature. Drives [DeviceFlipDetector] from
 * foreground && enabled && !locked && an active screen owner. This keeps the accelerometer
 * listener running only while the feature is usable on the current screen, routes each flip to the serialized
 * [BalanceHiddenManager.toggleBalanceHiddenOnFlip], and exposes a durable [pendingInfo] latch that
 * keeps the "balance hidden" info sheet pending until consumed even when the flip happened on a
 * non-Balance screen.
 */
class BalanceHideOnFlipManager(
    private val deviceFlipDetector: DeviceFlipDetector,
    private val balanceHiddenManager: BalanceHiddenManager,
    backgroundManager: BackgroundManager,
    private val localStorage: ILocalStorage,
    private val pinComponent: IPinComponent,
    dispatcherProvider: DispatcherProvider,
) {
    val isSupported = deviceFlipDetector.isSupported

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _pendingInfo = MutableStateFlow(false)
    val pendingInfo: StateFlow<Boolean> = _pendingInfo.asStateFlow()

    private val handlingOwners = MutableStateFlow<Set<Any>>(emptySet())
    private var flipEventsJob: Job? = null

    private val scope = CoroutineScope(dispatcherProvider.main)

    init {
        val enabled = localStorage.balanceHideOnFlipEnabled && isSupported
        if (!isSupported) {
            localStorage.balanceHideOnFlipEnabled = false
        }
        _enabled.value = enabled
        updateFlipEventCollection()

        scope.launch {
            combine(
                backgroundManager.stateFlow.map { it == BackgroundManagerState.EnterForeground },
                _enabled,
                pinComponent.isLockedFlow,
                handlingOwners,
            ) { foreground, enabled, locked, owners ->
                foreground && enabled && !locked && owners.isNotEmpty()
            }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) deviceFlipDetector.start() else deviceFlipDetector.stop()
                }
        }

        scope.launch {
            balanceHiddenManager.flipHiddenResult.collect { nowHidden ->
                HudHelper.vibrateDouble(App.instance)
                _pendingInfo.value = nowHidden && !localStorage.balanceHideOnFlipInfoSuppressed
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val newEnabled = enabled && isSupported
        localStorage.balanceHideOnFlipEnabled = newEnabled
        _enabled.value = newEnabled
        if (!newEnabled) _pendingInfo.value = false
    }

    fun setHandlingAllowed(owner: Any, allowed: Boolean) {
        val currentOwners = handlingOwners.value
        val updatedOwners = if (allowed) currentOwners + owner else currentOwners - owner
        if (updatedOwners == currentOwners) return

        handlingOwners.value = updatedOwners
        if (currentOwners.isEmpty() != updatedOwners.isEmpty()) {
            updateFlipEventCollection()
        }
    }

    fun consumeInfo() {
        _pendingInfo.value = false
    }

    fun suppressInfo() {
        localStorage.balanceHideOnFlipInfoSuppressed = true
        _pendingInfo.value = false
    }

    private fun updateFlipEventCollection() {
        flipEventsJob?.cancel()
        flipEventsJob = if (handlingOwners.value.isNotEmpty()) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                deviceFlipDetector.flipEvents.collect {
                    if (_enabled.value &&
                        handlingOwners.value.isNotEmpty() &&
                        !pinComponent.isLockedFlow.value
                    ) {
                        balanceHiddenManager.toggleBalanceHiddenOnFlip()
                    }
                }
            }
        } else {
            null
        }
    }
}
