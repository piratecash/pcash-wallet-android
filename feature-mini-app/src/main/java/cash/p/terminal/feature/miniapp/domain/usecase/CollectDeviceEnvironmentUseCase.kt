package cash.p.terminal.feature.miniapp.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import java.io.File
import cash.p.terminal.feature.miniapp.domain.model.ChargingType
import cash.p.terminal.feature.miniapp.domain.model.DeviceEnvironmentData
import cash.p.terminal.feature.miniapp.domain.model.Vector3D
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Use case for collecting device environment data from sensors and system state.
 * Provides start/stop API for data collection with noise filtering.
 * Automatically pauses when app goes to background and resumes on foreground.
 *
 * Usage:
 * ```
 * // Start collection
 * useCase.startCollection()
 *
 * // ... user navigates through screens ...
 *
 * // Get final data
 * val data = useCase.stopCollection()
 * ```
 */
class CollectDeviceEnvironmentUseCase(
    private val context: Context,
    private val backgroundManager: BackgroundManager
) {
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val gyroSamples = ArrayDeque<Vector3D>(MAX_SAMPLES)
    private val accelSamples = ArrayDeque<Vector3D>(MAX_SAMPLES)

    @Volatile
    private var isCollectingFlag = false

    @Volatile
    private var isPaused = false
    private var collectionStartTime: Long = 0
    private var pausedDuration: Long = 0
    private var pauseStartTime: Long = 0

    private var gyroListener: SensorEventListener? = null
    private var accelListener: SensorEventListener? = null

    // Noise filtering (exponential moving average)
    private val alpha = 0.8f
    private var filteredGyro: Vector3D? = null
    private var filteredAccel: Vector3D? = null

    // Sensor availability
    private val hasGyroscopeSensor: Boolean by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }

    private val hasAccelerometerSensor: Boolean by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    init {
        observeBackgroundState()
    }

    private fun observeBackgroundState() {
        scope.launch {
            backgroundManager.stateFlow.collect { state ->
                when (state) {
                    BackgroundManagerState.EnterBackground -> pauseCollection()
                    BackgroundManagerState.EnterForeground -> resumeCollection()
                    else -> { /* ignore */ }
                }
            }
        }
    }

    /**
     * Pause collection when app goes to background.
     * Data is preserved, listeners are unregistered.
     */
    @Synchronized
    private fun pauseCollection() {
        if (!isCollectingFlag || isPaused) return
        isPaused = true
        pauseStartTime = System.currentTimeMillis()
        unregisterSensorListeners()
    }

    /**
     * Resume collection when app comes back to foreground.
     */
    @Synchronized
    private fun resumeCollection() {
        if (!isCollectingFlag || !isPaused) return
        isPaused = false
        pausedDuration += System.currentTimeMillis() - pauseStartTime
        registerSensorListeners()
    }

    /**
     * Start collecting sensor data.
     * Call [stopCollection] or [getData] to retrieve collected data.
     */
    @Synchronized
    fun startCollection() {
        if (isCollectingFlag) return

        isCollectingFlag = true
        isPaused = false
        collectionStartTime = System.currentTimeMillis()
        pausedDuration = 0
        pauseStartTime = 0
        gyroSamples.clear()
        accelSamples.clear()
        filteredGyro = null
        filteredAccel = null

        registerSensorListeners()
    }

    /**
     * Stop collection and return final data.
     * Collection cannot be resumed after this call.
     */
    @Synchronized
    fun stopCollection(): DeviceEnvironmentData {
        isCollectingFlag = false
        isPaused = false
        unregisterSensorListeners()
        return buildResult()
    }

    /**
     * Get current collected data without stopping collection.
     * Returns null if collection hasn't started or no data collected.
     */
    @Synchronized
    fun getData(): DeviceEnvironmentData? {
        if (gyroSamples.isEmpty() && accelSamples.isEmpty()) {
            return null
        }
        return buildResult()
    }

    /**
     * Check if collection is currently active.
     */
    fun isCollecting(): Boolean = isCollectingFlag

    /**
     * Check if collection is paused (app in background).
     */
    fun isPaused(): Boolean = isPaused

    private fun registerSensorListeners() {
        // Gyroscope listener
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { gyro ->
            gyroListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (!isCollectingFlag) return
                    val raw = Vector3D(event.values[0], event.values[1], event.values[2])
                    val filtered = applyLowPassFilter(raw, filteredGyro)
                    filteredGyro = filtered
                    synchronized(gyroSamples) {
                        if (gyroSamples.size >= MAX_SAMPLES) {
                            gyroSamples.removeFirst()  // O(1)
                        }
                        gyroSamples.addLast(filtered)  // O(1)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(
                gyroListener,
                gyro,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        // Accelerometer listener
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { accel ->
            accelListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (!isCollectingFlag) return
                    val raw = Vector3D(event.values[0], event.values[1], event.values[2])
                    val filtered = applyLowPassFilter(raw, filteredAccel)
                    filteredAccel = filtered
                    synchronized(accelSamples) {
                        if (accelSamples.size >= MAX_SAMPLES) {
                            accelSamples.removeFirst()  // O(1)
                        }
                        accelSamples.addLast(filtered)  // O(1)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(
                accelListener,
                accel,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    private fun unregisterSensorListeners() {
        gyroListener?.let { sensorManager.unregisterListener(it) }
        accelListener?.let { sensorManager.unregisterListener(it) }
        gyroListener = null
        accelListener = null
    }

    /**
     * Apply exponential moving average filter to reduce sensor noise.
     */
    private fun applyLowPassFilter(raw: Vector3D, previous: Vector3D?): Vector3D {
        if (previous == null) return raw
        return Vector3D(
            x = alpha * raw.x + (1 - alpha) * previous.x,
            y = alpha * raw.y + (1 - alpha) * previous.y,
            z = alpha * raw.z + (1 - alpha) * previous.z
        )
    }

    private fun calculateAverage(samples: List<Vector3D>): Vector3D? {
        if (samples.isEmpty()) return null
        val sumX = samples.sumOf { it.x.toDouble() }
        val sumY = samples.sumOf { it.y.toDouble() }
        val sumZ = samples.sumOf { it.z.toDouble() }
        val count = samples.size
        return Vector3D(
            x = (sumX / count).toFloat(),
            y = (sumY / count).toFloat(),
            z = (sumZ / count).toFloat()
        )
    }

    private fun calculateVariance(samples: List<Vector3D>, average: Vector3D): Vector3D {
        if (samples.size < 2) return Vector3D.ZERO
        val varX = samples.sumOf { (it.x - average.x).toDouble().pow(2) } / samples.size
        val varY = samples.sumOf { (it.y - average.y).toDouble().pow(2) } / samples.size
        val varZ = samples.sumOf { (it.z - average.z).toDouble().pow(2) } / samples.size
        return Vector3D(
            x = varX.toFloat(),
            y = varY.toFloat(),
            z = varZ.toFloat()
        )
    }

    /**
     * Detect if device is hand-held based on accelerometer variance.
     * Hand tremor causes higher variance than a fixed device.
     */
    private fun isHandHeld(variance: Vector3D?): Boolean {
        if (variance == null) return false
        val totalVariance = variance.x + variance.y + variance.z
        return totalVariance > HAND_HELD_VARIANCE_THRESHOLD
    }

    /**
     * Detect if device was moved during collection.
     * Looks for significant acceleration changes between samples.
     */
    private fun wasDeviceMoved(samples: List<Vector3D>): Boolean {
        if (samples.size < 2) return false

        for (i in 1 until samples.size) {
            val delta = Vector3D(
                x = abs(samples[i].x - samples[i - 1].x),
                y = abs(samples[i].y - samples[i - 1].y),
                z = abs(samples[i].z - samples[i - 1].z)
            )
            val magnitude = sqrt(delta.x.pow(2) + delta.y.pow(2) + delta.z.pow(2))
            if (magnitude > MOVEMENT_THRESHOLD) {
                return true
            }
        }
        return false
    }

    private fun getBatteryInfo(): Triple<Int, Boolean, ChargingType> {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargingType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_USB -> ChargingType.USB
            BatteryManager.BATTERY_PLUGGED_AC -> ChargingType.AC
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargingType.WIRELESS
            else -> ChargingType.NONE
        }

        return Triple(batteryPct, isCharging, chargingType)
    }

    private fun isUsbConnected(): Boolean {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return chargePlug == BatteryManager.BATTERY_PLUGGED_USB
    }

    private fun isDeveloperOptionsEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun isAdbEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun isDeviceRooted(): Boolean {
        return hasSuBinary() || hasRootManagementApps() || hasDangerousProps()
    }

    private fun hasSuBinary(): Boolean {
        val paths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun hasRootManagementApps(): Boolean {
        val packages = listOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "com.kingroot.kinguser",
            "com.kingo.root"
        )
        val pm = context.packageManager
        return packages.any { pkg ->
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private fun hasDangerousProps(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.debuggable"))
            val result = process.inputStream.bufferedReader().readLine()
            process.waitFor()
            result == "1"
        } catch (e: Exception) {
            false
        }
    }

    private fun buildResult(): DeviceEnvironmentData {
        val gyroSamplesCopy: List<Vector3D>
        val accelSamplesCopy: List<Vector3D>

        synchronized(gyroSamples) {
            gyroSamplesCopy = gyroSamples.toList()
        }
        synchronized(accelSamples) {
            accelSamplesCopy = accelSamples.toList()
        }

        val gyroAvg = calculateAverage(gyroSamplesCopy)
        val accelAvg = calculateAverage(accelSamplesCopy)

        val gyroVar = gyroAvg?.let { calculateVariance(gyroSamplesCopy, it) }
        val accelVar = accelAvg?.let { calculateVariance(accelSamplesCopy, it) }

        val (batteryLevel, isCharging, chargingType) = getBatteryInfo()

        // Calculate actual collection duration (excluding paused time)
        val totalElapsed = System.currentTimeMillis() - collectionStartTime
        val currentPauseDuration = if (isPaused) System.currentTimeMillis() - pauseStartTime else 0
        val collectionDuration = totalElapsed - pausedDuration - currentPauseDuration
        val totalSamples = gyroSamplesCopy.size + accelSamplesCopy.size

        return DeviceEnvironmentData(
            gyroscopeAverage = gyroAvg,
            accelerometerAverage = accelAvg,
            gyroscopeVariance = gyroVar,
            accelerometerVariance = accelVar,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            chargingType = chargingType,
            isUsbConnected = isUsbConnected(),
            isHandHeld = isHandHeld(accelVar),
            wasDeviceMoved = wasDeviceMoved(accelSamplesCopy),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            manufacturer = Build.MANUFACTURER,
            osVersion = "Android ${Build.VERSION.RELEASE}",
            sdkVersion = Build.VERSION.SDK_INT,
            hasGyroscope = hasGyroscopeSensor,
            hasAccelerometer = hasAccelerometerSensor,
            isDeveloperOptionsEnabled = isDeveloperOptionsEnabled(),
            isAdbEnabled = isAdbEnabled(),
            isRooted = isDeviceRooted(),
            collectionDurationMs = collectionDuration,
            sampleCount = totalSamples
        )
    }

    companion object {
        // Hand tremor detection threshold (variance in m/s²)
        private const val HAND_HELD_VARIANCE_THRESHOLD = 0.05f

        // Movement detection threshold (acceleration change in m/s²)
        private const val MOVEMENT_THRESHOLD = 0.4f

        private const val MAX_SAMPLES = 10_000
    }
}
