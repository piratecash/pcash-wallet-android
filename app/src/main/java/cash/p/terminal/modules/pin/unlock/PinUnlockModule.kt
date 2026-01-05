package cash.p.terminal.modules.pin.unlock

object PinUnlockModule {

    data class PinUnlockViewState(
        val enteredCount: Int,
        val fingerScannerEnabled: Boolean,
        val unlocked: Boolean,
        val showShakeAnimation: Boolean,
        val inputState: InputState
    )

    sealed class InputState {
        class Enabled(val attemptsLeft: Int? = null) : InputState()
        class Locked(val until: String) : InputState()
    }

}