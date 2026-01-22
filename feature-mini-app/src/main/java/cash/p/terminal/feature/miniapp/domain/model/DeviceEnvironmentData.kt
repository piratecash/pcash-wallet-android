package cash.p.terminal.feature.miniapp.domain.model

/**
 * Device environment data collected from sensors and system state.
 * Used to enhance emulator detection by analyzing real device behavior.
 */
data class DeviceEnvironmentData(
    // Sensor averages (nullable if sensor unavailable)
    val gyroscopeAverage: Vector3D?,
    val accelerometerAverage: Vector3D?,

    // Sensor variance (low variance = suspicious for emulator)
    val gyroscopeVariance: Vector3D?,
    val accelerometerVariance: Vector3D?,

    // Device state
    val batteryLevel: Int,              // 0-100%
    val isCharging: Boolean,
    val chargingType: ChargingType,
    val isUsbConnected: Boolean,

    // Behavioral flags
    val isHandHeld: Boolean,            // Based on accelerometer variance (hand tremor)
    val wasDeviceMoved: Boolean,        // Significant movement detected during collection

    // Device info
    val deviceModel: String,
    val manufacturer: String,
    val osVersion: String,
    val sdkVersion: Int,

    // Sensor availability (missing sensors = emulator indicator)
    val hasGyroscope: Boolean,
    val hasAccelerometer: Boolean,

    // Security indicators
    val isDeveloperOptionsEnabled: Boolean,
    val isAdbEnabled: Boolean,
    val isRooted: Boolean,

    // Collection metadata
    val collectionDurationMs: Long,
    val sampleCount: Int
)

/**
 * 3D vector for sensor data (x, y, z axes)
 */
data class Vector3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    companion object {
        val ZERO = Vector3D(0f, 0f, 0f)
    }
}

/**
 * Type of charging connection
 */
enum class ChargingType {
    USB,
    AC,
    WIRELESS,
    NONE
}
