package cash.p.terminal.core.managers

import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.ui_compose.components.HudHelper
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Policy owner for the "hide balance on flip" feature. Drives [DeviceFlipDetector] from
 * foreground && enabled && !locked (so the accelerometer listener runs only while the feature is
 * usable and no PIN/calculator lock screen is up), routes each flip to the serialized
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

    private val scope = CoroutineScope(dispatcherProvider.default)

    init {
        val enabled = localStorage.balanceHideOnFlipEnabled && isSupported
        if (!isSupported) {
            localStorage.balanceHideOnFlipEnabled = false
        }
        _enabled.value = enabled

        scope.launch {
            combine(
                backgroundManager.stateFlow.map { it == BackgroundManagerState.EnterForeground },
                _enabled,
                pinComponent.isLockedFlow
            ) { foreground, enabled, locked -> foreground && enabled && !locked }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) deviceFlipDetector.start() else deviceFlipDetector.stop()
                }
        }

        scope.launch {
            deviceFlipDetector.flipEvents.collect {
                if (_enabled.value && !pinComponent.isLockedFlow.value) {
                    balanceHiddenManager.toggleBalanceHiddenOnFlip()
                }
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

    fun consumeInfo() {
        _pendingInfo.value = false
    }

    fun suppressInfo() {
        localStorage.balanceHideOnFlipInfoSuppressed = true
        _pendingInfo.value = false
    }
}
