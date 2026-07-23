package cash.p.terminal.modules.softwareupdate.domain

import cash.p.terminal.core.ILocalStorage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldAutoCheckUseCaseTest {

    private val localStorage = mockk<ILocalStorage>()
    private val timeProvider = mockk<TimeProvider>()
    private val useCase = ShouldAutoCheckUseCase(localStorage, timeProvider)

    @Test
    fun invoke_intervalElapsed_returnsTrue() {
        every { localStorage.lastUpdateCheckTimestamp } returns 0L
        every { localStorage.updateCheckInterval } returns UpdateCheckInterval.DAY
        every { timeProvider.now() } returns UpdateCheckInterval.DAY.millis + 1

        assertTrue(useCase())
    }

    @Test
    fun invoke_intervalNotElapsed_returnsFalse() {
        every { localStorage.lastUpdateCheckTimestamp } returns 1_000L
        every { localStorage.updateCheckInterval } returns UpdateCheckInterval.WEEK
        every { timeProvider.now() } returns 1_000L + 5_000L

        assertFalse(useCase())
    }
}
