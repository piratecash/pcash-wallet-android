package cash.p.terminal.feature.logging.domain.usecase

import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ILoginRecordRepository
import io.horizontalsystems.core.ILoggingSettings
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteLoggingOnDuressUseCaseTest {

    @MockK
    private lateinit var loggingSettings: ILoggingSettings

    @MockK
    private lateinit var loginRecordRepository: ILoginRecordRepository

    @MockK
    private lateinit var dispatcherProvider: DispatcherProvider

    private lateinit var testScope: TestScope
    private lateinit var useCase: DeleteLoggingOnDuressUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        testScope = TestScope()
        every { dispatcherProvider.applicationScope } returns testScope
        useCase = DeleteLoggingOnDuressUseCase(
            loggingSettings = loggingSettings,
            loginRecordRepository = loginRecordRepository,
            dispatcherProvider = dispatcherProvider
        )
    }

    // ==================== deleteLoggingForLowerLevelsIfEnabled Tests ====================

    @Test
    fun deleteLoggingForLowerLevelsIfEnabled_userLevel0_doesNothing() {
        useCase.deleteLoggingForLowerLevelsIfEnabled(userLevel = 0)
        testScope.advanceUntilIdle()

        coVerify(exactly = 0) { loginRecordRepository.deleteAll(any()) }
    }

    @Test
    fun deleteLoggingForLowerLevelsIfEnabled_negativeUserLevel_doesNothing() {
        useCase.deleteLoggingForLowerLevelsIfEnabled(userLevel = -1)
        testScope.advanceUntilIdle()

        coVerify(exactly = 0) { loginRecordRepository.deleteAll(any()) }
    }

    @Test
    fun deleteLoggingForLowerLevelsIfEnabled_userLevel1SettingEnabled_deletesFromLevel0() {
        every { loggingSettings.getDeleteAllAuthDataOnDuressEnabled(0) } returns true

        useCase.deleteLoggingForLowerLevelsIfEnabled(userLevel = 1)
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { loginRecordRepository.deleteAll(0) }
    }

    @Test
    fun deleteLoggingForLowerLevelsIfEnabled_userLevel1SettingDisabled_doesNothing() {
        every { loggingSettings.getDeleteAllAuthDataOnDuressEnabled(0) } returns false

        useCase.deleteLoggingForLowerLevelsIfEnabled(userLevel = 1)
        testScope.advanceUntilIdle()

        coVerify(exactly = 0) { loginRecordRepository.deleteAll(any()) }
    }

    @Test
    fun deleteLoggingForLowerLevelsIfEnabled_userLevel2SettingEnabled_deletesFromLevel1() {
        every { loggingSettings.getDeleteAllAuthDataOnDuressEnabled(1) } returns true

        useCase.deleteLoggingForLowerLevelsIfEnabled(userLevel = 2)
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { loginRecordRepository.deleteAll(1) }
    }

    @Test
    fun deleteLoggingForLowerLevelsIfEnabled_userLevel2SettingDisabled_doesNothing() {
        every { loggingSettings.getDeleteAllAuthDataOnDuressEnabled(1) } returns false

        useCase.deleteLoggingForLowerLevelsIfEnabled(userLevel = 2)
        testScope.advanceUntilIdle()

        coVerify(exactly = 0) { loginRecordRepository.deleteAll(any()) }
    }
}
