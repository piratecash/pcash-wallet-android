package cash.p.terminal.feature.logging.domain.usecase

import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ILoginRecordRepository
import io.horizontalsystems.core.ILoggingSettings
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.ISilentPhotoCapture
import cash.p.terminal.wallet.managers.UserManager
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogLoginAttemptUseCaseTest {

    @MockK
    private lateinit var loggingSettings: ILoggingSettings

    @MockK
    private lateinit var loginRecordRepository: ILoginRecordRepository

    @MockK
    private lateinit var pinComponent: IPinComponent

    @MockK
    private lateinit var accountManager: IAccountManager

    @MockK
    private lateinit var silentPhotoCapture: ISilentPhotoCapture

    @MockK
    private lateinit var checkPremiumUseCase: CheckPremiumUseCase

    @MockK
    private lateinit var userManager: UserManager

    @MockK
    private lateinit var dispatcherProvider: DispatcherProvider

    private lateinit var useCase: LogLoginAttemptUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        every { dispatcherProvider.io } returns Dispatchers.Unconfined
        useCase = LogLoginAttemptUseCase(
            loggingSettings = loggingSettings,
            loginRecordRepository = loginRecordRepository,
            pinComponent = pinComponent,
            accountManager = accountManager,
            silentPhotoCapture = silentPhotoCapture,
            checkPremiumUseCase = checkPremiumUseCase,
            userManager = userManager,
            dispatcherProvider = dispatcherProvider
        )
    }

    // ==================== captureLoginPhoto Tests ====================

    @Test
    fun captureLoginPhoto_unsuccessfulLoginNoLevelsExist_returnsNull() = runTest {
        every { pinComponent.getAllPinLevels() } returns emptyList()

        val result = useCase.captureLoginPhoto(null)

        assertNull(result)
        coVerify(exactly = 0) { silentPhotoCapture.capturePhoto() }
    }

    @Test
    fun captureLoginPhoto_unsuccessfulLoginAnyLevelHasSelfieEnabled_capturesPhoto() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1)
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(0) } returns false
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(0) } returns false
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(1) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(1) } returns true
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns true
        every { silentPhotoCapture.hasCameraPermission() } returns true
        coEvery { silentPhotoCapture.capturePhoto() } returns Result.success("/path/photo.jpg")

        val result = useCase.captureLoginPhoto(null)

        assertEquals("/path/photo.jpg", result)
        coVerify { silentPhotoCapture.capturePhoto() }
    }

    @Test
    fun captureLoginPhoto_unsuccessfulLoginNoLevelHasSelfieEnabled_returnsNull() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1)
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(0) } returns false
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(1) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(1) } returns false
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns true

        val result = useCase.captureLoginPhoto(null)

        assertNull(result)
        coVerify(exactly = 0) { silentPhotoCapture.capturePhoto() }
    }

    @Test
    fun captureLoginPhoto_unsuccessfulLoginAnyLevelHasPremium_checksAllLevels() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1, 2)
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(any()) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(any()) } returns true
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns false
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(1) } returns false
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(2) } returns true
        every { silentPhotoCapture.hasCameraPermission() } returns true
        coEvery { silentPhotoCapture.capturePhoto() } returns Result.success("/path/photo.jpg")

        val result = useCase.captureLoginPhoto(null)

        assertEquals("/path/photo.jpg", result)
        coVerify { checkPremiumUseCase.isPremiumWithParentInCache(0) }
        coVerify { checkPremiumUseCase.isPremiumWithParentInCache(1) }
        coVerify { checkPremiumUseCase.isPremiumWithParentInCache(2) }
    }

    @Test
    fun captureLoginPhoto_unsuccessfulLoginNoLevelHasPremium_returnsNull() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1)
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(any()) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(any()) } returns true
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns false

        val result = useCase.captureLoginPhoto(null)

        assertNull(result)
        coVerify(exactly = 0) { silentPhotoCapture.capturePhoto() }
    }

    @Test
    fun captureLoginPhoto_successfulStandardLoginSelfieEnabled_capturesPhoto() = runTest {
        every { loggingSettings.getLogSuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnSuccessfulLoginEnabled(0) } returns true
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns true
        every { silentPhotoCapture.hasCameraPermission() } returns true
        coEvery { silentPhotoCapture.capturePhoto() } returns Result.success("/path/photo.jpg")

        val result = useCase.captureLoginPhoto(0)

        assertEquals("/path/photo.jpg", result)
    }

    @Test
    fun captureLoginPhoto_successfulStandardLoginSelfieDisabled_returnsNull() = runTest {
        every { loggingSettings.getLogSuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnSuccessfulLoginEnabled(0) } returns false
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns true

        val result = useCase.captureLoginPhoto(0)

        assertNull(result)
    }

    @Test
    fun captureLoginPhoto_successfulStandardLoginNoPremium_returnsNull() = runTest {
        every { loggingSettings.getLogSuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnSuccessfulLoginEnabled(0) } returns true
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns false

        val result = useCase.captureLoginPhoto(0)

        assertNull(result)
    }

    @Test
    fun captureLoginPhoto_successfulDuressLogin_usesCorrectSettings() = runTest {
        val duressLevel = 2
        val checkLevel = duressLevel - 1
        every { loggingSettings.getLogIntoDuressModeEnabled(checkLevel) } returns true
        every { loggingSettings.getSelfieOnDuressLoginEnabled(checkLevel) } returns true
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(duressLevel) } returns true
        every { silentPhotoCapture.hasCameraPermission() } returns true
        coEvery { silentPhotoCapture.capturePhoto() } returns Result.success("/path/photo.jpg")

        val result = useCase.captureLoginPhoto(duressLevel)

        assertEquals("/path/photo.jpg", result)
        verify { loggingSettings.getSelfieOnDuressLoginEnabled(checkLevel) }
        coVerify { checkPremiumUseCase.isPremiumWithParentInCache(duressLevel) }
    }

    @Test
    fun captureLoginPhoto_photoCaptureFails_returnsNull() = runTest {
        every { loggingSettings.getLogSuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnSuccessfulLoginEnabled(0) } returns true
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns true
        every { silentPhotoCapture.hasCameraPermission() } returns true
        coEvery { silentPhotoCapture.capturePhoto() } returns Result.failure(Exception("Camera error"))

        val result = useCase.captureLoginPhoto(0)

        assertNull(result)
    }

    // ==================== logLoginAttempt Tests ====================

    @Test
    fun logLoginAttempt_unsuccessfulLoginNoLevelsExist_doesNothing() = runTest {
        every { pinComponent.getAllPinLevels() } returns emptyList()

        useCase.logLoginAttempt(userLevel = null, photoPath = "/path/photo.jpg")

        coVerify(exactly = 0) { loginRecordRepository.insert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun logLoginAttempt_unsuccessfulLoginNoPremiumAnywhere_deletesPhoto() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1)
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns false

        useCase.logLoginAttempt(userLevel = null, photoPath = "/path/photo.jpg")

        coVerify(exactly = 0) { loginRecordRepository.insert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun logLoginAttempt_unsuccessfulLoginHasPremiumLoggingEnabled_insertsRecordsForAllLevels() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1)
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns true
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(1) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(any()) } returns false
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = null, photoPath = null)

        coVerify(exactly = 2) { loginRecordRepository.insert(any(), false, any(), "", null) }
    }

    @Test
    fun logLoginAttempt_unsuccessfulLoginMixedSettings_correctRecordsPerLevel() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1, 2)
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns true
        // Level 0: logging enabled, selfie enabled
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(0) } returns true
        // Level 1: logging enabled, selfie disabled
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(1) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(1) } returns false
        // Level 2: logging disabled
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(2) } returns false
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(2) } returns false
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = null, photoPath = "/path/photo.jpg")

        // Level 0 with photo, Level 1 without photo, Level 2 not logged
        coVerify { loginRecordRepository.insert(any(), false, 0, "", "/path/photo.jpg") }
        coVerify { loginRecordRepository.insert(any(), false, 1, "", null) }
        coVerify(exactly = 0) { loginRecordRepository.insert(any(), false, 2, any(), any()) }
    }

    @Test
    fun logLoginAttempt_successfulStandardLoginNoPremium_deletesPhoto() = runTest {
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns false

        useCase.logLoginAttempt(userLevel = 0, photoPath = "/path/photo.jpg")

        coVerify(exactly = 0) { loginRecordRepository.insert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun logLoginAttempt_successfulStandardLoginLoggingDisabled_deletesPhoto() = runTest {
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns true
        every { loggingSettings.getLogSuccessfulLoginsEnabled(0) } returns false
        every { loggingSettings.getSelfieOnSuccessfulLoginEnabled(0) } returns true

        useCase.logLoginAttempt(userLevel = 0, photoPath = "/path/photo.jpg")

        coVerify(exactly = 0) { loginRecordRepository.insert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun logLoginAttempt_successfulStandardLoginSelfieDisabledWithPhoto_insertsRecordWithoutPhoto() = runTest {
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns true
        every { loggingSettings.getLogSuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnSuccessfulLoginEnabled(0) } returns false
        every { accountManager.activeAccount } returns null
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = 0, photoPath = "/path/photo.jpg")

        coVerify { loginRecordRepository.insert(any(), true, 0, "", null) }
    }

    @Test
    fun logLoginAttempt_successfulStandardLoginAllEnabled_insertsRecordWithPhoto() = runTest {
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns true
        every { loggingSettings.getLogSuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnSuccessfulLoginEnabled(0) } returns true
        every { accountManager.activeAccount } returns createMockAccount("account-123")
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = 0, photoPath = "/path/photo.jpg")

        coVerify { loginRecordRepository.insert(any(), true, 0, "account-123", "/path/photo.jpg") }
    }

    @Test
    fun logLoginAttempt_successfulStandardLoginNullPhotoPath_insertsRecordWithNull() = runTest {
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns true
        every { loggingSettings.getLogSuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnSuccessfulLoginEnabled(0) } returns true
        every { accountManager.activeAccount } returns null
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = 0, photoPath = null)

        coVerify { loginRecordRepository.insert(any(), true, 0, "", null) }
    }

    @Test
    fun logLoginAttempt_successfulDuressLogin_usesCorrectSettings() = runTest {
        val pinLevel = 2
        val checkLevel = pinLevel - 1  // duress settings are checked on previous level
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(pinLevel) } returns true
        every { loggingSettings.getLogIntoDuressModeEnabled(checkLevel) } returns true
        every { loggingSettings.getSelfieOnDuressLoginEnabled(checkLevel) } returns true
        every { accountManager.activeAccount } returns createMockAccount("duress-account")
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = pinLevel, photoPath = "/path/photo.jpg")

        verify { loggingSettings.getLogIntoDuressModeEnabled(checkLevel) }
        verify { loggingSettings.getSelfieOnDuressLoginEnabled(checkLevel) }
        coVerify { loginRecordRepository.insert(any(), true, pinLevel, "duress-account", "/path/photo.jpg") }
    }

    @Test
    fun logLoginAttempt_repositoryThrowsException_catchesAndContinues() = runTest {
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns true
        every { loggingSettings.getLogSuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnSuccessfulLoginEnabled(0) } returns true
        every { accountManager.activeAccount } returns null
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } throws RuntimeException("DB error")

        // Should not throw
        useCase.logLoginAttempt(userLevel = 0, photoPath = "/path/photo.jpg")
    }

    @Test
    fun logLoginAttempt_successfulLogin_includesCorrectAccountId() = runTest {
        val accountId = "test-account-id-123"
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(0) } returns true
        every { loggingSettings.getLogSuccessfulLoginsEnabled(0) } returns true
        every { loggingSettings.getSelfieOnSuccessfulLoginEnabled(0) } returns false
        every { accountManager.activeAccount } returns createMockAccount(accountId)
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = 0, photoPath = null)

        coVerify { loginRecordRepository.insert(any(), true, 0, accountId, null) }
    }

    // ==================== logUnsuccessfulLoginForAllLevels Tests ====================

    @Test
    fun logUnsuccessfulForAllLevels_noLevelsWithLoggingEnabled_deletesPhoto() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1)
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns true
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(any()) } returns false
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(any()) } returns false

        useCase.logLoginAttempt(userLevel = null, photoPath = "/path/photo.jpg")

        coVerify(exactly = 0) { loginRecordRepository.insert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun logUnsuccessfulForAllLevels_multipleLevelsNeedPhoto_firstKeepsOriginal() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1)
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns true
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(any()) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(any()) } returns true
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = null, photoPath = "/path/photo.jpg")

        // First level (0) should get the original path
        coVerify { loginRecordRepository.insert(any(), false, 0, "", "/path/photo.jpg") }
    }

    @Test
    fun logUnsuccessfulForAllLevels_nullPhotoPath_insertsWithNullForAll() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1)
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns true
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(any()) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(any()) } returns true
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = null, photoPath = null)

        coVerify { loginRecordRepository.insert(any(), false, 0, "", null) }
        coVerify { loginRecordRepository.insert(any(), false, 1, "", null) }
    }

    @Test
    fun logUnsuccessfulForAllLevels_onlyLogOnlyLevels_noPhotoInRecords() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 1)
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns true
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(any()) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(any()) } returns false
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = null, photoPath = "/path/photo.jpg")

        coVerify { loginRecordRepository.insert(any(), false, 0, "", null) }
        coVerify { loginRecordRepository.insert(any(), false, 1, "", null) }
    }

    @Test
    fun logUnsuccessfulForAllLevels_insertsCorrectUserLevelForEachRecord() = runTest {
        every { pinComponent.getAllPinLevels() } returns listOf(0, 2, 5)
        coEvery { checkPremiumUseCase.isPremiumWithParentInCache(any()) } returns true
        every { loggingSettings.getLogUnsuccessfulLoginsEnabled(any()) } returns true
        every { loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(any()) } returns false
        coEvery { loginRecordRepository.insert(any(), any(), any(), any(), any()) } returns 1L

        useCase.logLoginAttempt(userLevel = null, photoPath = null)

        coVerify { loginRecordRepository.insert(any(), false, 0, "", null) }
        coVerify { loginRecordRepository.insert(any(), false, 2, "", null) }
        coVerify { loginRecordRepository.insert(any(), false, 5, "", null) }
    }

    // ==================== Helper Functions ====================

    private fun createMockAccount(id: String): Account {
        return Account(
            id = id,
            name = "Test Account",
            type = io.mockk.mockk(relaxed = true),
            origin = io.mockk.mockk(relaxed = true),
            level = 0,
            isBackedUp = true,
            isFileBackedUp = true
        )
    }
}
