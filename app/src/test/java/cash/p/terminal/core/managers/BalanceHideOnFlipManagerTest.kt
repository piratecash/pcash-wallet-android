package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.IPinComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Guards that the flip-to-hide feature stays inert behind the PIN/calculator lock screen: while
 * [IPinComponent.isLockedFlow] is true the gravity detector must not run and a flip must not toggle
 * the balance, so a lock-screen flip can neither vibrate a tell, surface the info sheet over the
 * disguise, nor expose an auto-hidden balance after unlock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BalanceHideOnFlipManagerTest {

    private val flipEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Test
    fun flip_whileLocked_doesNotToggleBalanceNorStartDetector() = runTest(UnconfinedTestDispatcher()) {
        val detector = flipDetector()
        val balanceHiddenManager = mockk<BalanceHiddenManager>(relaxed = true)
        createManager(detector, balanceHiddenManager, MutableStateFlow(true))

        flipEvents.emit(Unit)
        advanceUntilIdle()

        verify(exactly = 0) { balanceHiddenManager.toggleBalanceHiddenOnFlip() }
        verify(exactly = 0) { detector.start() }
        verify { detector.stop() }
    }

    @Test
    fun flip_whileUnlocked_togglesBalanceAndStartsDetector() = runTest(UnconfinedTestDispatcher()) {
        val detector = flipDetector()
        val balanceHiddenManager = mockk<BalanceHiddenManager>(relaxed = true)
        createManager(detector, balanceHiddenManager, MutableStateFlow(false))

        flipEvents.emit(Unit)
        advanceUntilIdle()

        verify(exactly = 1) { balanceHiddenManager.toggleBalanceHiddenOnFlip() }
        verify { detector.start() }
    }

    @Test
    fun lockEngaged_stopsDetector() = runTest(UnconfinedTestDispatcher()) {
        val detector = flipDetector()
        val lockedFlow = MutableStateFlow(false)
        createManager(detector, mockk(relaxed = true), lockedFlow)
        verify { detector.start() }

        lockedFlow.value = true
        advanceUntilIdle()

        verify { detector.stop() }
    }

    private fun flipDetector(): DeviceFlipDetector = mockk(relaxed = true) {
        every { flipEvents } returns this@BalanceHideOnFlipManagerTest.flipEvents
    }

    private fun TestScope.createManager(
        detector: DeviceFlipDetector,
        balanceHiddenManager: BalanceHiddenManager,
        lockedFlow: MutableStateFlow<Boolean>,
    ): BalanceHideOnFlipManager {
        every { balanceHiddenManager.flipHiddenResult } returns MutableSharedFlow()
        val backgroundManager = mockk<BackgroundManager> {
            every { stateFlow } returns MutableStateFlow(BackgroundManagerState.EnterForeground)
        }
        val localStorage = mockk<ILocalStorage>(relaxed = true) {
            every { balanceHideOnFlipEnabled } returns true
        }
        val pinComponent = mockk<IPinComponent> {
            every { isLockedFlow } returns lockedFlow
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return BalanceHideOnFlipManager(
            deviceFlipDetector = detector,
            balanceHiddenManager = balanceHiddenManager,
            backgroundManager = backgroundManager,
            localStorage = localStorage,
            pinComponent = pinComponent,
            dispatcherProvider = TestDispatcherProvider(dispatcher, backgroundScope),
        )
    }
}
