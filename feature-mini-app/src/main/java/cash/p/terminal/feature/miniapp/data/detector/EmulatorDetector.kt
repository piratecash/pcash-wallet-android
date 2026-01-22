package cash.p.terminal.feature.miniapp.data.detector

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import cash.p.terminal.feature.miniapp.domain.model.DeviceEnvironmentData
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.NetworkInterface

data class EmulatorIndicator(
    val type: String,
    val detail: String
)

class EmulatorDetector(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun detectAll(): List<EmulatorIndicator> = withContext(dispatcherProvider.io) {
        val indicators = mutableListOf<EmulatorIndicator>()
        runCatching { indicators.addAll(checkBuildProperties()) }
        runCatching { indicators.addAll(checkSystemProperties()) }
        runCatching { indicators.addAll(checkFileSystem()) }
        runCatching { indicators.addAll(checkCpuInfo()) }
        runCatching { indicators.addAll(checkQemuDrivers()) }
        runCatching { indicators.addAll(checkNetworkConfig()) }
        runCatching { indicators.addAll(checkInstalledPackages()) }
        runCatching { indicators.addAll(checkMounts()) }
        runCatching { indicators.addAll(checkCpuArchitecture()) }
        runCatching { indicators.addAll(checkArmTranslation()) }
        runCatching { indicators.addAll(checkNoxPropertyFiles()) }
        indicators
    }

    private fun checkBuildProperties(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        if (EmulatorSignatures.FINGERPRINT_PREFIXES.any { Build.FINGERPRINT.startsWith(it) }) {
            indicators.add(EmulatorIndicator("BUILD", "FINGERPRINT: ${Build.FINGERPRINT}"))
        }

        if (EmulatorSignatures.MODEL_INDICATORS.any { Build.MODEL.contains(it, ignoreCase = true) }) {
            indicators.add(EmulatorIndicator("BUILD", "MODEL: ${Build.MODEL}"))
        }

        if (EmulatorSignatures.MANUFACTURERS.any { Build.MANUFACTURER.equals(it, ignoreCase = true) }) {
            indicators.add(EmulatorIndicator("BUILD", "MANUFACTURER: ${Build.MANUFACTURER}"))
        }

        if (EmulatorSignatures.HARDWARE_NAMES.any { Build.HARDWARE.equals(it, ignoreCase = true) }) {
            indicators.add(EmulatorIndicator("BUILD", "HARDWARE: ${Build.HARDWARE}"))
        }

        if (EmulatorSignatures.PRODUCTS.any { Build.PRODUCT.equals(it, ignoreCase = true) }) {
            indicators.add(EmulatorIndicator("BUILD", "PRODUCT: ${Build.PRODUCT}"))
        }

        if (EmulatorSignatures.BOARDS.any { Build.BOARD.equals(it, ignoreCase = true) }) {
            indicators.add(EmulatorIndicator("BUILD", "BOARD: ${Build.BOARD}"))
        }

        if (EmulatorSignatures.DEVICES.any { Build.DEVICE.equals(it, ignoreCase = true) }) {
            indicators.add(EmulatorIndicator("BUILD", "DEVICE: ${Build.DEVICE}"))
        }

        if (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) {
            indicators.add(EmulatorIndicator("BUILD", "BRAND+DEVICE: ${Build.BRAND}/${Build.DEVICE}"))
        }

        // Nox-specific checks (contains, not equals)
        if (Build.BOARD.contains(EmulatorSignatures.NOX_INDICATOR, ignoreCase = true)) {
            indicators.add(EmulatorIndicator("BUILD", "BOARD contains nox: ${Build.BOARD}"))
        }
        if (Build.BOOTLOADER.contains(EmulatorSignatures.NOX_INDICATOR, ignoreCase = true)) {
            indicators.add(EmulatorIndicator("BUILD", "BOOTLOADER contains nox: ${Build.BOOTLOADER}"))
        }
        @Suppress("DEPRECATION")
        if (Build.SERIAL.contains(EmulatorSignatures.NOX_INDICATOR, ignoreCase = true)) {
            indicators.add(EmulatorIndicator("BUILD", "SERIAL contains nox: ${Build.SERIAL}"))
        }

        return indicators
    }

    private fun checkSystemProperties(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        // Check for exact value matches
        for ((key, expectedValue) in EmulatorSignatures.QEMU_PROPERTY_EXACT) {
            val value = getSystemProperty(key)
            if (value == expectedValue) {
                indicators.add(EmulatorIndicator("SYSTEM_PROPERTY", "$key=$value"))
            }
        }

        // Check for value containing expected substrings
        for ((key, expectedValues) in EmulatorSignatures.QEMU_PROPERTY_CONTAINS) {
            val value = getSystemProperty(key)
            if (!value.isNullOrEmpty()) {
                for (expected in expectedValues) {
                    if (value.contains(expected, ignoreCase = true)) {
                        indicators.add(EmulatorIndicator("SYSTEM_PROPERTY", "$key contains $expected"))
                        break
                    }
                }
            }
        }

        // Check for property existence (any non-empty value)
        for (key in EmulatorSignatures.QEMU_PROPERTY_EXISTENCE) {
            val value = getSystemProperty(key)
            if (!value.isNullOrEmpty()) {
                indicators.add(EmulatorIndicator("SYSTEM_PROPERTY", "$key exists"))
            }
        }

        return indicators
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val classLoader = context.classLoader
            val systemProperties = classLoader.loadClass("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java)
            getMethod.invoke(systemProperties, key) as? String
        } catch (e: Exception) {
            Timber.d("Failed to get system property: $key")
            null
        }
    }

    private fun checkFileSystem(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        for (filePath in EmulatorSignatures.EMULATOR_FILES) {
            val file = File(filePath)
            if (file.exists()) {
                indicators.add(EmulatorIndicator("FILE", filePath))
            }
        }

        return indicators
    }

    private fun checkCpuInfo(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        try {
            val cpuInfoFile = File("/proc/cpuinfo")
            if (cpuInfoFile.exists() && cpuInfoFile.canRead()) {
                val content = cpuInfoFile.readText()

                // Check for emulator indicators (hypervisor, goldfish)
                for (indicator in EmulatorSignatures.CPU_INFO_EMULATOR_INDICATORS) {
                    if (content.contains(indicator, ignoreCase = true)) {
                        indicators.add(EmulatorIndicator("CPU", "cpuinfo contains: $indicator"))
                    }
                }

                // Check for host CPU leak (amd/intel visible means emulator)
                for (hostCpu in EmulatorSignatures.HOST_CPU_INDICATORS) {
                    if (content.contains(hostCpu, ignoreCase = true)) {
                        indicators.add(EmulatorIndicator("CPU", "Host CPU detected: $hostCpu"))
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("Failed to read cpuinfo")
        }

        return indicators
    }

    private fun checkQemuDrivers(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        val driverFile = File("/proc/tty/drivers")
        if (driverFile.exists() && driverFile.canRead()) {
            try {
                val content = driverFile.readText()
                for (qemuDriver in EmulatorSignatures.QEMU_DRIVERS) {
                    if (content.contains(qemuDriver)) {
                        indicators.add(EmulatorIndicator("DRIVER", "QEMU driver: $qemuDriver"))
                        break
                    }
                }
            } catch (e: Exception) {
                Timber.d("Failed to check QEMU drivers")
            }
        }

        return indicators
    }

    private fun checkNetworkConfig(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces?.hasMoreElements() == true) {
                val networkInterface = networkInterfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    val hostAddress = address.hostAddress
                    if (hostAddress == EmulatorSignatures.EMULATOR_IP) {
                        indicators.add(
                            EmulatorIndicator(
                                "NETWORK",
                                "Emulator IP detected: $hostAddress on ${networkInterface.name}"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("Failed to check network config")
        }

        return indicators
    }

    private fun checkInstalledPackages(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        val packageManager = context.packageManager

        for (packageName in EmulatorSignatures.EMULATOR_PACKAGES) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    val resolveInfos = packageManager.queryIntentActivities(
                        intent,
                        PackageManager.MATCH_DEFAULT_ONLY
                    )
                    if (resolveInfos.isNotEmpty()) {
                        indicators.add(EmulatorIndicator("PACKAGE", packageName))
                    }
                }
            } catch (e: Exception) {
                Timber.d("Failed to check package: $packageName")
            }
        }

        return indicators
    }

    private fun checkMounts(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        try {
            val mountsFile = File("/proc/mounts")
            if (mountsFile.exists() && mountsFile.canRead()) {
                val content = mountsFile.readText()
                if (content.contains(EmulatorSignatures.VBOXSF_INDICATOR, ignoreCase = true)) {
                    indicators.add(EmulatorIndicator("MOUNT", "VirtualBox shared folder detected in /proc/mounts"))
                }
            }
        } catch (e: Exception) {
            Timber.d("Failed to read /proc/mounts")
        }

        return indicators
    }

    private fun checkCpuArchitecture(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        val abi = getSystemProperty(EmulatorSignatures.CPU_ABI_PROPERTY)
        if (!abi.isNullOrEmpty() && abi.contains(EmulatorSignatures.X86_INDICATOR, ignoreCase = true)) {
            indicators.add(EmulatorIndicator("ARCHITECTURE", "x86 architecture detected: $abi"))
        }

        return indicators
    }

    private fun checkArmTranslation(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        // Check native bridge property
        val nativeBridge = getSystemProperty(EmulatorSignatures.NATIVE_BRIDGE_PROPERTY)
        if (!nativeBridge.isNullOrEmpty() && nativeBridge != "0") {
            indicators.add(EmulatorIndicator("ARM_TRANSLATION", "Native bridge enabled: $nativeBridge"))
        }

        // Check ARM translation properties
        for ((key, expectedValue) in EmulatorSignatures.ARM_TRANSLATION_PROPERTIES) {
            val value = getSystemProperty(key)
            if (value == expectedValue) {
                indicators.add(EmulatorIndicator("ARM_TRANSLATION", "$key=$value"))
            }
        }

        // Check ARM translation library files
        for (filePath in EmulatorSignatures.ARM_TRANSLATION_FILES) {
            val file = File(filePath)
            if (file.exists()) {
                indicators.add(EmulatorIndicator("ARM_TRANSLATION", "libhoudini.so found: $filePath"))
            }
        }

        return indicators
    }

    private fun checkNoxPropertyFiles(): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        try {
            val propertyDir = File("/data/property/")
            if (propertyDir.exists() && propertyDir.isDirectory) {
                val files = propertyDir.listFiles()
                files?.forEach { file ->
                    if (file.name.contains(EmulatorSignatures.NOX_INDICATOR, ignoreCase = true)) {
                        indicators.add(EmulatorIndicator("FILE", "Nox property file: ${file.absolutePath}"))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("Failed to scan /data/property/")
        }

        return indicators
    }

    /**
     * Check sensor data for emulator indicators.
     * Real devices have sensors with natural variation; emulators often lack sensors
     * or return constant/zero values.
     */
    fun checkSensorData(data: DeviceEnvironmentData): List<EmulatorIndicator> {
        val indicators = mutableListOf<EmulatorIndicator>()

        // Missing sensors - most real devices have both
        if (!data.hasGyroscope) {
            indicators.add(EmulatorIndicator("SENSOR", "No gyroscope sensor"))
        }
        if (!data.hasAccelerometer) {
            indicators.add(EmulatorIndicator("SENSOR", "No accelerometer sensor"))
        }

        // Zero/constant accelerometer values (should have gravity at minimum)
        data.accelerometerVariance?.let { variance ->
            if (variance.x < SENSOR_VARIANCE_THRESHOLD &&
                variance.y < SENSOR_VARIANCE_THRESHOLD &&
                variance.z < SENSOR_VARIANCE_THRESHOLD
            ) {
                indicators.add(EmulatorIndicator("SENSOR", "Accelerometer variance too low: ${variance.x}, ${variance.y}, ${variance.z}"))
            }
        }

        // Zero/constant gyroscope values
        data.gyroscopeVariance?.let { variance ->
            if (variance.x < SENSOR_VARIANCE_THRESHOLD &&
                variance.y < SENSOR_VARIANCE_THRESHOLD &&
                variance.z < SENSOR_VARIANCE_THRESHOLD
            ) {
                indicators.add(EmulatorIndicator("SENSOR", "Gyroscope variance too low: ${variance.x}, ${variance.y}, ${variance.z}"))
            }
        }

        // No movement at all during collection is suspicious for an actively used device
        if (!data.wasDeviceMoved && data.sampleCount > MIN_SENSOR_SAMPLES) {
            indicators.add(EmulatorIndicator("BEHAVIOR", "No device movement detected during ${data.collectionDurationMs}ms"))
        }

        return indicators
    }

    companion object {
        // Minimum variance threshold - values below this suggest synthetic/emulated data
        private const val SENSOR_VARIANCE_THRESHOLD = 0.0001f

        // Minimum samples needed before movement detection is reliable
        private const val MIN_SENSOR_SAMPLES = 10
    }
}
