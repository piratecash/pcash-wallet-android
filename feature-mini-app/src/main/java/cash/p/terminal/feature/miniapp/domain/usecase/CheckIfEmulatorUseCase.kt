package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.feature.miniapp.data.detector.EmulatorDetector
import cash.p.terminal.feature.miniapp.data.detector.EmulatorIndicator

data class EmulatorCheckResult(
    val isEmulator: Boolean,
    val confidence: Float,
    val indicators: List<EmulatorIndicator>
)

class CheckIfEmulatorUseCase(
    private val emulatorDetector: EmulatorDetector
) {
    suspend operator fun invoke(): EmulatorCheckResult {
        val indicators = emulatorDetector.detectAll()
        val confidence = calculateConfidence(indicators)
        return EmulatorCheckResult(
            isEmulator = confidence >= EMULATOR_THRESHOLD,
            confidence = confidence,
            indicators = indicators
        )
    }

    private fun calculateConfidence(indicators: List<EmulatorIndicator>): Float {
        if (indicators.isEmpty()) return 0f

        var score = 0f

        for (indicator in indicators) {
            score += when (indicator.type) {
                "BUILD" -> WEIGHT_BUILD
                "SYSTEM_PROPERTY" -> WEIGHT_SYSTEM_PROPERTY
                "FILE" -> WEIGHT_FILE
                "CPU" -> WEIGHT_CPU
                "DRIVER" -> WEIGHT_DRIVER
                "NETWORK" -> WEIGHT_NETWORK
                "PACKAGE" -> WEIGHT_PACKAGE
                else -> WEIGHT_DEFAULT
            }
        }

        return minOf(1.0f, score)
    }

    companion object {
        private const val EMULATOR_THRESHOLD = 0.3f

        private const val WEIGHT_BUILD = 0.15f
        private const val WEIGHT_SYSTEM_PROPERTY = 0.1f
        private const val WEIGHT_FILE = 0.2f
        private const val WEIGHT_CPU = 0.15f
        private const val WEIGHT_DRIVER = 0.2f
        private const val WEIGHT_NETWORK = 0.15f
        private const val WEIGHT_PACKAGE = 0.25f
        private const val WEIGHT_DEFAULT = 0.1f
    }
}
