package cash.p.terminal.core.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import cash.p.terminal.core.App
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Accelerometer-only mechanism that emits a [flipEvents] tick when the user performs a
 * face-down-then-up flip. It owns no lifecycle or feature state: [start]/[stop] are driven by
 * [BalanceHideOnFlipManager], so the listener is registered only while the feature is active.
 *
 * The Z axis is smoothed before requiring face-down and face-up values close to Earth's gravity.
 * The complete gesture must fit within three seconds; there is no minimum dwell or cooldown.
 */
class DeviceFlipDetector {

    private val _flipEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flipEvents: SharedFlow<Unit> = _flipEvents.asSharedFlow()

    private val sensorManager: SensorManager? by lazy {
        App.instance.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val accelerometerSensor: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    val isSupported: Boolean
        get() = accelerometerSensor != null

    private var registered = false
    private val listener = FlipSensorListener()

    fun start() {
        if (registered) return
        val sensor = accelerometerSensor ?: return
        registered = sensorManager?.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_UI
        ) == true
    }

    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(listener)
        registered = false
        listener.reset()
    }

    private inner class FlipSensorListener : SensorEventListener {
        private val gestureRecognizer = FlipGestureRecognizer()

        fun reset() {
            gestureRecognizer.reset()
        }

        override fun onSensorChanged(event: SensorEvent) {
            val elapsedMs = event.timestamp / NANOS_PER_MILLISECOND
            if (gestureRecognizer.onSensorChanged(event.values[2], elapsedMs)) {
                _flipEvents.tryEmit(Unit)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

internal class FlipGestureRecognizer {
    private var filteredZ: Float? = null
    private var faceDownAtMs: Long? = null

    fun onSensorChanged(z: Float, elapsedMs: Long): Boolean {
        val smoothedZ = smooth(z)
        val startedAtMs = faceDownAtMs
        val isFaceDown = isStableOrientation(z, smoothedZ, FACE_DOWN_Z)
        val isFaceUp = isStableOrientation(z, smoothedZ, FACE_UP_Z)

        return when {
            isFaceDown && startedAtMs == null -> {
                faceDownAtMs = elapsedMs
                false
            }

            isFaceUp && startedAtMs != null -> {
                faceDownAtMs = null
                elapsedMs - startedAtMs <= FLIP_WINDOW_MS
            }

            else -> false
        }
    }

    fun reset() {
        filteredZ = null
        faceDownAtMs = null
    }

    private fun smooth(z: Float): Float {
        val smoothed = filteredZ?.let { previous ->
            z + FILTER_PREVIOUS_WEIGHT * (previous - z)
        } ?: z
        filteredZ = smoothed
        return smoothed
    }

    private fun isStableOrientation(
        rawZ: Float,
        smoothedZ: Float,
        range: ClosedFloatingPointRange<Float>,
    ) = rawZ in range && smoothedZ in range

    private companion object {
        val FACE_DOWN_Z = -10.5f..-8f
        val FACE_UP_Z = 8f..10.5f
        const val FILTER_PREVIOUS_WEIGHT = 0.5f
        const val FLIP_WINDOW_MS = 3000L
    }
}
