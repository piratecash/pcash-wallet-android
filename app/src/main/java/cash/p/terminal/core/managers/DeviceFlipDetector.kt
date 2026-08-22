package cash.p.terminal.core.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import cash.p.terminal.core.App
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Accelerometer-only mechanism that emits a [flipEvents] tick when the user performs a
 * face-down-then-up flip. It owns no lifecycle or feature state: [start]/[stop] are driven by
 * [BalanceHideOnFlipManager], so the listener is registered only while the feature is active.
 *
 * The Z axis is smoothed before requiring a stable face-down position followed by a sufficiently
 * face-up position. The complete gesture must fit within three seconds; there is no minimum dwell
 * or cooldown. Sensor callbacks run on a dedicated thread, while input processing is capped
 * because Android treats the requested sampling period as a hint and may deliver events faster.
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

    private var listener: FlipSensorListener? = null
    private var sensorThread: HandlerThread? = null

    fun start() {
        if (listener != null) return
        val sensor = accelerometerSensor ?: return
        val newListener = FlipSensorListener()
        val newSensorThread = HandlerThread(SENSOR_THREAD_NAME).apply { start() }
        val registered = sensorManager?.registerListener(
            newListener,
            sensor,
            SensorManager.SENSOR_DELAY_UI,
            Handler(newSensorThread.looper),
        ) == true

        if (registered) {
            listener = newListener
            sensorThread = newSensorThread
        } else {
            newSensorThread.quitSafely()
        }
    }

    fun stop() {
        val activeListener = listener ?: return
        sensorManager?.unregisterListener(activeListener)
        listener = null
        sensorThread?.quitSafely()
        sensorThread = null
    }

    private inner class FlipSensorListener : SensorEventListener {
        private val gestureRecognizer = FlipGestureRecognizer()
        private var lastProcessedEventAtNs = 0L

        override fun onSensorChanged(event: SensorEvent) {
            if (!shouldProcess(event.timestamp)) {
                return
            }

            val elapsedMs = event.timestamp / NANOS_PER_MILLISECOND
            if (gestureRecognizer.onSensorChanged(event.values[2], elapsedMs)) {
                _flipEvents.tryEmit(Unit)
            }
        }

        private fun shouldProcess(eventTimestampNs: Long): Boolean {
            val lastProcessedAtNs = lastProcessedEventAtNs
            if (lastProcessedAtNs != 0L &&
                eventTimestampNs - lastProcessedAtNs < MIN_PROCESSING_INTERVAL_NS
            ) {
                return false
            }
            lastProcessedEventAtNs = eventTimestampNs
            return true
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private companion object {
        const val SENSOR_THREAD_NAME = "DeviceFlipDetector"
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val MAX_PROCESSING_RATE_HZ = 50
        const val MIN_PROCESSING_INTERVAL_NS = NANOS_PER_SECOND / MAX_PROCESSING_RATE_HZ
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
        val FACE_UP_Z = 6f..10.5f
        const val FILTER_PREVIOUS_WEIGHT = 0.25f
        const val FLIP_WINDOW_MS = 3000L
    }
}
