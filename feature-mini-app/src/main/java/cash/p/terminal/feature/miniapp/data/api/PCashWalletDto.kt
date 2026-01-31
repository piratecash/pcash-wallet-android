package cash.p.terminal.feature.miniapp.data.api

import cash.p.terminal.feature.miniapp.domain.model.Vector3D
import kotlinx.serialization.Serializable

@Serializable
data class PCashWalletRequestDto(
    val walletAddress: String,
    val premiumAddress: String,
    val pirate: String,
    val cosa: String,
    val uniqueCode: String?,
    // Sensor averages
    val gyro: Vector3DDto,
    val accelerometer: Vector3DDto,
    // Sensor variance (low variance = emulator indicator)
    val gyroVariance: Vector3DDto,
    val accelerometerVariance: Vector3DDto,
    // Battery & charging
    val batteryPercent: Int,
    val isCharging: Boolean,
    val chargingType: String,
    val isUsbConnected: Boolean,
    // Device info
    val deviceModel: String,
    val osVersion: String,
    val sdkVersion: Int,
    // Sensor availability (missing = emulator indicator)
    val hasGyroscope: Boolean,
    val hasAccelerometer: Boolean,
    // Behavioral signals
    val emulator: Boolean,
    val isMoving: Boolean,
    val isHandHeld: Boolean,
    // Security signals
    val isDev: Boolean,
    val isAdb: Boolean,
    val isRooted: Boolean,
    // Collection metadata
    val collectionDurationMs: Long,
    val sampleCount: Int,
    val apiVersion: Int = MiniAppApi.API_VERSION
)

@Serializable
data class PCashWalletResponseDto(
    val uniqueCode: String? = null
)

@Serializable
data class Vector3DDto(
    val x: Float,
    val y: Float,
    val z: Float
) {
    companion object {
        val ZERO = Vector3DDto(0f, 0f, 0f)
    }
}

fun Vector3D.toDto() = Vector3DDto(x, y, z)
