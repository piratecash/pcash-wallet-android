package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.IPinComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards that the flip-to-hide feature stays inert behind the PIN/calculator lock screen: while
 * [IPinComponent.isLockedFlow] is true the accelerometer detector must not run and a flip must not toggle
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

    @Test
    fun handlingDisallowed_queuedFlip_doesNotToggleBalanceAndStopsDetector() = runTest {
        val detector = flipDetector()
        val balanceHiddenManager = mockk<BalanceHiddenManager>(relaxed = true)
        val manager = createManagerWithStandardDispatcher(detector, balanceHiddenManager)

        flipEvents.emit(Unit)
        manager.setHandlingAllowed(primaryOwner, false)
        advanceUntilIdle()

        verify { detector.stop() }
        verify(exactly = 0) { balanceHiddenManager.toggleBalanceHiddenOnFlip() }
    }

    @Test
    fun handlingAllowed_afterDisallowed_restartsDetectorAndProcessesFlip() =
        runTest(UnconfinedTestDispatcher()) {
            val detector = flipDetector()
            val balanceHiddenManager = mockk<BalanceHiddenManager>(relaxed = true)
            val (manager, _) = createManager(detector, balanceHiddenManager, MutableStateFlow(false))

            manager.setHandlingAllowed(primaryOwner, false)
            advanceUntilIdle()
            manager.setHandlingAllowed(primaryOwner, true)
            advanceUntilIdle()
            flipEvents.emit(Unit)
            advanceUntilIdle()

            verify(exactly = 2) { detector.start() }
            verify(exactly = 1) { balanceHiddenManager.toggleBalanceHiddenOnFlip() }
        }

    @Test
    fun handlingAllowed_afterFlipWhileDisallowed_doesNotProcessStaleFlip() = runTest {
        val detector = flipDetector()
        val balanceHiddenManager = mockk<BalanceHiddenManager>(relaxed = true)
        val manager = createManagerWithStandardDispatcher(detector, balanceHiddenManager)

        manager.setHandlingAllowed(primaryOwner, false)
        flipEvents.emit(Unit)
        manager.setHandlingAllowed(primaryOwner, true)
        advanceUntilIdle()

        verify(exactly = 0) { balanceHiddenManager.toggleBalanceHiddenOnFlip() }
    }

    @Test
    fun initialization_supportedDevice_keepsEnabledPreference() = runTest(UnconfinedTestDispatcher()) {
        val (manager, _) = createManager(flipDetector(), mockk(relaxed = true), MutableStateFlow(false))

        assertTrue(manager.isSupported)
        assertTrue(manager.enabled.value)
    }

    @Test
    fun initialization_unsupportedDevice_disablesLegacyEnabledPreference() = runTest(UnconfinedTestDispatcher()) {
        val (manager, localStorage) = createManager(
            flipDetector(supported = false),
            mockk(relaxed = true),
            MutableStateFlow(false)
        )

        assertFalse(manager.isSupported)
        assertFalse(manager.enabled.value)
        verify { localStorage.balanceHideOnFlipEnabled = false }
    }

    @Test
    fun setHandlingAllowed_ownerRemovedWhileAnotherActive_keepsProcessingFlips() =
        runTest(UnconfinedTestDispatcher()) {
            val detector = flipDetector()
            val balanceHiddenManager = mockk<BalanceHiddenManager>(relaxed = true)
            val (manager, _) = createManager(
                detector,
                balanceHiddenManager,
                MutableStateFlow(false),
            )
            manager.setHandlingAllowed(secondaryOwner, true)

            manager.setHandlingAllowed(primaryOwner, false)
            flipEvents.emit(Unit)
            advanceUntilIdle()

            verify(exactly = 1) { balanceHiddenManager.toggleBalanceHiddenOnFlip() }
        }

    private fun flipDetector(supported: Boolean = true): DeviceFlipDetector = mockk(relaxed = true) {
        every { flipEvents } returns this@BalanceHideOnFlipManagerTest.flipEvents
        every { isSupported } returns supported
    }

    private fun TestScope.createManagerWithStandardDispatcher(
        detector: DeviceFlipDetector,
        balanceHiddenManager: BalanceHiddenManager,
    ): BalanceHideOnFlipManager {
        val (manager, _) = createManager(
            detector,
            balanceHiddenManager,
            MutableStateFlow(false),
            StandardTestDispatcher(testScheduler),
        )
        runCurrent()
        return manager
    }

    private fun TestScope.createManager(
        detector: DeviceFlipDetector,
        balanceHiddenManager: BalanceHiddenManager,
        lockedFlow: MutableStateFlow<Boolean>,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
    ): Pair<BalanceHideOnFlipManager, ILocalStorage> {
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
        val manager = BalanceHideOnFlipManager(
            deviceFlipDetector = detector,
            balanceHiddenManager = balanceHiddenManager,
            backgroundManager = backgroundManager,
            localStorage = localStorage,
            pinComponent = pinComponent,
            dispatcherProvider = TestDispatcherProvider(dispatcher, backgroundScope),
        )
        manager.setHandlingAllowed(primaryOwner, true)
        return manager to localStorage
    }

    private companion object {
        val primaryOwner = Any()
        val secondaryOwner = Any()
    }
}
