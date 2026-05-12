package cash.p.terminal.modules.pin.unlock

import cash.p.terminal.feature.logging.domain.usecase.DeleteLoggingOnDuressUseCase
import cash.p.terminal.feature.logging.domain.usecase.LogLoginAttemptUseCase
import cash.p.terminal.modules.pin.SendZecOnDuressUseCase
import cash.p.terminal.modules.pin.core.ILockoutManager
import cash.p.terminal.modules.pin.core.PinLevels
import io.horizontalsystems.core.IPinComponent

class AttemptPinUnlockUseCase(
    private val pinComponent: IPinComponent,
    private val lockoutManager: ILockoutManager,
    private val logLoginAttemptUseCase: LogLoginAttemptUseCase,
    private val deleteLoggingOnDuressUseCase: DeleteLoggingOnDuressUseCase,
    private val sendZecOnDuressUseCase: SendZecOnDuressUseCase,
) {
    suspend operator fun invoke(pin: String): Boolean {
        val detectedPinLevel = pinComponent.getPinLevel(pin)
        val userLevel = PinLevels.resolvedUserLevelAfterUnlock(detectedPinLevel)

        val photoPath = logLoginAttemptUseCase.captureLoginPhoto(userLevel)
        val unlocked = pinComponent.unlock(pin, detectedPinLevel)

        logLoginAttemptUseCase.logLoginAttempt(
            userLevel = userLevel.takeIf { unlocked },
            photoPath = photoPath
        )

        if (unlocked && userLevel != null) {
            lockoutManager.dropFailedAttempts()
            deleteLoggingOnDuressUseCase.deleteLoggingForLowerLevelsIfEnabled(userLevel)
            sendZecOnDuressUseCase.sendIfEnabled(userLevel)
            return true
        }
        lockoutManager.didFailUnlock()
        return false
    }
}
