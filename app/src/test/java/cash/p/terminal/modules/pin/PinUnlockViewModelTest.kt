package cash.p.terminal.modules.pin

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.modules.pin.core.ILockoutManager
import cash.p.terminal.modules.pin.core.LockoutState
import cash.p.terminal.modules.pin.core.OneTimeTimer
import cash.p.terminal.modules.pin.unlock.AttemptPinUnlockUseCase
import cash.p.terminal.modules.pin.unlock.PinUnlockModule
import cash.p.terminal.modules.pin.unlock.PinUnlockViewModel
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.ISystemInfoManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinUnlockViewModelTest {

    private val pinComponent: IPinComponent = mockk(relaxed = true)
    private val lockoutManager: ILockoutManager = mockk(relaxed = true)
    private val systemInfoManager: ISystemInfoManager = mockk(relaxed = true)
    private val timer: OneTimeTimer = mockk(relaxed = true)
    private val localStorage: ILocalStorage = mockk(relaxed = true)
    private val attemptPinUnlock: AttemptPinUnlockUseCase = mockk(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { systemInfoManager.biometricAuthSupported } returns false
        every { pinComponent.isBiometricAuthEnabled } returns false
        every { localStorage.pinRandomized } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(initialState: LockoutState = LockoutState.Unlocked(null)): PinUnlockViewModel {
        every { lockoutManager.currentState } returns initialState
        return PinUnlockViewModel(
            pinComponent,
            lockoutManager,
            systemInfoManager,
            timer,
            localStorage,
            attemptPinUnlock,
        )
    }

    @Test
    fun unlocked_afterFailedThenSuccessfulPin_resetsAttemptsLeft() = runTest(dispatcher) {
        val viewModel = createViewModel(LockoutState.Unlocked(attemptsLeft = 4))

        coEvery { attemptPinUnlock("123456") } returns true
        every { lockoutManager.currentState } returns LockoutState.Unlocked(null)

        for (digit in "123456".map { it.digitToInt() }) {
            viewModel.onKeyClick(digit)
        }

        viewModel.unlocked()

        val inputState = viewModel.uiState.inputState
        assert(inputState is PinUnlockModule.InputState.Enabled) {
            "Expected Enabled state, got $inputState"
        }
        assertNull(
            "attemptsLeft should be null after successful unlock and re-lock",
            (inputState as PinUnlockModule.InputState.Enabled).attemptsLeft
        )
    }

    @Test
    fun onKeyClick_failedPin_reflectsRemainingAttemptsFromLockoutManager() = runTest(dispatcher) {
        val viewModel = createViewModel(LockoutState.Unlocked(attemptsLeft = 4))

        coEvery { attemptPinUnlock("654321") } returns false
        every { lockoutManager.currentState } returns LockoutState.Unlocked(attemptsLeft = 3)

        for (digit in "654321".map { it.digitToInt() }) {
            viewModel.onKeyClick(digit)
        }

        coVerify(exactly = 1) { attemptPinUnlock("654321") }
        val inputState = viewModel.uiState.inputState as PinUnlockModule.InputState.Enabled
        assertEquals(3, inputState.attemptsLeft)
    }
}
