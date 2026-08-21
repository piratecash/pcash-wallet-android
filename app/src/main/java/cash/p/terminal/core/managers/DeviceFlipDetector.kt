package cash.p.terminal.core.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import cash.p.terminal.core.App
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Pure gravity-sensor mechanism that emits a [flipEvents] tick when the user performs a
 * face-down-then-up flip. It owns no lifecycle or feature state: [start]/[stop] are driven by
 * [BalanceHideOnFlipManager], so the listener is registered only while the feature is active.
 *
 * Gesture (ported from Tangem): the screen goes face-down (gravity z < [Z_FACE_DOWN]) and returns
 * up (z > [Z_RETURNED_UP]) within [FLIP_WINDOW_MS]. Requiring the return within a short window means
 * a phone resting face-down on a table does not toggle the balance when later picked up. Only
 * [Sensor.TYPE_GRAVITY] is used: the raw accelerometer would false-trigger on shakes.
 */
class DeviceFlipDetector {

    private val _flipEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flipEvents: SharedFlow<Unit> = _flipEvents.asSharedFlow()

    private val sensorManager: SensorManager? by lazy {
        App.instance.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val gravitySensor: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    }

    val isSupported: Boolean
        get() = gravitySensor != null

    private var registered = false
    private val listener = FlipSensorListener()

    fun start() {
        if (registered) return
        val sensor = gravitySensor ?: return
        registered = sensorManager?.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        ) == true
    }

    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(listener)
        registered = false
        listener.reset()
    }

    private inner class FlipSensorListener : SensorEventListener {
        private var faceDownAt = 0L

        fun reset() {
            faceDownAt = 0L
        }

        override fun onSensorChanged(event: SensorEvent) {
            val z = event.values[2]
            when {
                z < Z_FACE_DOWN && faceDownAt == 0L -> {
                    faceDownAt = SystemClock.elapsedRealtime()
                }

                z > Z_RETURNED_UP && faceDownAt != 0L -> {
                    val now = SystemClock.elapsedRealtime()
                    val completedInWindow = now - faceDownAt <= FLIP_WINDOW_MS
                    faceDownAt = 0L
                    if (completedInWindow) {
                        _flipEvents.tryEmit(Unit)
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private companion object {
        const val Z_FACE_DOWN = -6f
        const val Z_RETURNED_UP = -3f
        const val FLIP_WINDOW_MS = 3000L
    }
}
