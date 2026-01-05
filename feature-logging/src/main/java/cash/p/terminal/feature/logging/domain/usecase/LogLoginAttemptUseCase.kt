package cash.p.terminal.feature.logging.domain.usecase

import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ILoggingSettings
import io.horizontalsystems.core.ILoginRecordRepository
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.ISilentPhotoCapture
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Use case for logging login attempts with optional photo capture.
 * Handles both successful and unsuccessful login attempts across all PIN levels.
 */
class LogLoginAttemptUseCase(
    private val loggingSettings: ILoggingSettings,
    private val loginRecordRepository: ILoginRecordRepository,
    private val pinComponent: IPinComponent,
    private val accountManager: IAccountManager,
    private val silentPhotoCapture: ISilentPhotoCapture,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val userManager: UserManager,
    private val dispatcherProvider: DispatcherProvider
) {

    /**
     * To show badge on settings tab
     */
    suspend fun selfieEnabledAndHasProblem(): Boolean {
        val currentUserLevel = userManager.getUserLevel()
        if (!checkPremiumUseCase.isPremiumWithParentInCache(currentUserLevel)) return false
        val duressLevel = maxOf(currentUserLevel - 1, 0)

        val successEnabled = loggingSettings.getLogSuccessfulLoginsEnabled(currentUserLevel) &&
                loggingSettings.getSelfieOnSuccessfulLoginEnabled(currentUserLevel)

        val duressEnabled = loggingSettings.getLogIntoDuressModeEnabled(duressLevel) &&
                loggingSettings.getSelfieOnDuressLoginEnabled(duressLevel)

        val unsuccessfulEnabled = loggingSettings.getLogUnsuccessfulLoginsEnabled(currentUserLevel) &&
                loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(currentUserLevel)

        return (successEnabled || duressEnabled || unsuccessfulEnabled) && !silentPhotoCapture.hasCameraPermission()
    }

    /**
     * Determines if the logging settings alert should be shown.
     * Alert shows if:
     * - Selfie is enabled but has problems (no camera permission), OR
     * - User is NOT premium but has logging settings enabled
     */
    suspend fun shouldShowLoggingAlert(): Boolean {
        return selfieEnabledAndHasProblem() ||
            (!checkPremiumUseCase.getPremiumType().isPremium() &&
                loggingSettings.hasEnabledAtLeastOneSettingsEnabled(userManager.currentUserLevelFlow.value))
    }

    /**
     * Captures a selfie photo for login logging if enabled for the given PIN level.
     *
     * @param pinLevel The detected PIN level, or null for unsuccessful login
     * @return The file path of the captured photo, or null if not needed/failed
     */
    suspend fun captureLoginPhoto(pinLevel: Int?): String? {
        val shouldTakeSelfie = when (pinLevel) {
            null -> {
                // Check ALL levels - take selfie if ANY level has BOTH logging AND selfie enabled
                pinComponent.getAllPinLevels().any { level ->
                    loggingSettings.getLogUnsuccessfulLoginsEnabled(level) &&
                        loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(level)
                }
            }

            0 -> loggingSettings.getLogSuccessfulLoginsEnabled(0) &&
                loggingSettings.getSelfieOnSuccessfulLoginEnabled(0)

            else -> {
                val duressLevel = maxOf(pinLevel - 1, 0)
                loggingSettings.getLogIntoDuressModeEnabled(duressLevel) &&
                    loggingSettings.getSelfieOnDuressLoginEnabled(duressLevel)
            }
        }
        val hasPremium = when (pinLevel) {
            null -> {
                // Check ALL levels - has premium if ANY level has it
                pinComponent.getAllPinLevels().any { level ->
                    checkPremiumUseCase.isPremiumWithParentInCache(level)
                }
            }

            else -> checkPremiumUseCase.isPremiumWithParentInCache(pinLevel)
        }

        return if (shouldTakeSelfie && hasPremium && silentPhotoCapture.hasCameraPermission()) {
            val result = silentPhotoCapture.capturePhoto()
            result.getOrNull()
        } else {
            null
        }
    }

    /**
     * Logs a login attempt with the captured photo.
     *
     * @param userLevel The user level for successful logins
     * @param photoPath The path to the captured photo, or null
     */
    suspend fun logLoginAttempt(
        userLevel: Int?,
        photoPath: String?
    ) {
        val hasPremium = if (userLevel == null) {
            // Check ALL levels - has premium if ANY level has it
            pinComponent.getAllPinLevels().any { level ->
                checkPremiumUseCase.isPremiumWithParentInCache(level)
            }
        } else {
            checkPremiumUseCase.isPremiumWithParentInCache(userLevel)
        }
        if (!hasPremium) {
            tryDeleteFile(photoPath)
            return
        }

        if (userLevel == null) {
            // Log for EACH level that has logging enabled
            logUnsuccessfulLoginForAllLevels(photoPath)
            return
        }

        // Successful login - use explicit level
        val shouldLog = when (userLevel) {
            0 -> loggingSettings.getLogSuccessfulLoginsEnabled(0)
            else -> loggingSettings.getLogIntoDuressModeEnabled(maxOf(userLevel - 1, 0))
        }
        val shouldTakeSelfie = when (userLevel) {
            0 -> loggingSettings.getSelfieOnSuccessfulLoginEnabled(0)
            else -> loggingSettings.getSelfieOnDuressLoginEnabled(maxOf(userLevel - 1, 0))
        }


        if (!shouldLog) {
            tryDeleteFile(photoPath)
            return
        }

        if (!shouldTakeSelfie && photoPath != null) {
            tryDeleteFile(photoPath)
        }

        val activeAccount = accountManager.activeAccount
        val accountId = activeAccount?.id ?: ""

        try {
            loginRecordRepository.insert(
                timestamp = System.currentTimeMillis(),
                isSuccessful = true,
                userLevel = userLevel,
                accountId = accountId,
                photoPath = if (shouldTakeSelfie) photoPath else null
            )
        } catch (_: Exception) {
        }
    }

    private suspend fun logUnsuccessfulLoginForAllLevels(photoPath: String?) {
        val levels = pinComponent.getAllPinLevels()

        val levelsNeedingPhoto = levels.filter { level ->
            loggingSettings.getLogUnsuccessfulLoginsEnabled(level) &&
                    loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(level)
        }
        val levelsNeedingLogOnly = levels.filter { level ->
            loggingSettings.getLogUnsuccessfulLoginsEnabled(level) &&
                    !loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(level)
        }

        // Copy photo for each level that needs it
        val photoPaths = mutableMapOf<Int, String?>()
        if (photoPath != null && levelsNeedingPhoto.isNotEmpty()) {
            for ((index, level) in levelsNeedingPhoto.withIndex()) {
                if (index == 0) {
                    // First level keeps original
                    photoPaths[level] = photoPath
                } else {
                    // Other levels get a copy
                    val copyPath = copyPhotoForLevel(photoPath, level)
                    photoPaths[level] = copyPath
                }
            }
        }

        // Create log records for levels needing photo
        for (level in levelsNeedingPhoto) {
            try {
                loginRecordRepository.insert(
                    timestamp = System.currentTimeMillis(),
                    isSuccessful = false,
                    userLevel = level,
                    accountId = "",
                    photoPath = photoPaths[level]
                )
            } catch (_: Exception) {
            }
        }

        // Create log records for levels needing log only (no photo)
        for (level in levelsNeedingLogOnly) {
            try {
                loginRecordRepository.insert(
                    timestamp = System.currentTimeMillis(),
                    isSuccessful = false,
                    userLevel = level,
                    accountId = "",
                    photoPath = null
                )
            } catch (_: Exception) {
            }
        }

        // Delete original photo if no level used it
        if (levelsNeedingPhoto.isEmpty() && photoPath != null) {
            tryDeleteFile(photoPath)
        }
    }

    private suspend fun copyPhotoForLevel(originalPath: String, level: Int): String? {
        return withContext(dispatcherProvider.io) {
            try {
                val original = File(originalPath)
                val levelDir = File(original.parentFile?.parentFile, level.toString())
                levelDir.mkdirs()
                val copy = File(levelDir, original.name)
                original.copyTo(copy, overwrite = true)
                copy.absolutePath
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun tryDeleteFile(path: String?) {
        withContext(dispatcherProvider.io) {
            try {
                path?.let { File(it).delete() }
            } catch (_: Exception) {
            }
        }
    }
}
