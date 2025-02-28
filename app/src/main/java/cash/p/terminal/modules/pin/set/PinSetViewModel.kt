package cash.p.terminal.modules.pin.set

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.modules.pin.PinModule
import cash.p.terminal.modules.pin.set.PinSetModule.PinSetViewState
import cash.p.terminal.modules.pin.set.PinSetModule.SetStage.Confirm
import cash.p.terminal.modules.pin.set.PinSetModule.SetStage.Enter
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PinSetViewModel(
    private val pinComponent: IPinComponent,
    private val forDuress: Boolean,
) : ViewModelUiState<PinSetViewState>() {

    private var enteredPin = ""
    private var submittedPin = ""

    private var stage = Enter
    private var finished = false
    private var reverseSlideAnimation = false
    private var error: String? = null

    override fun createState() = PinSetViewState(
        stage = stage,
        enteredCount = enteredPin.length,
        finished = finished,
        reverseSlideAnimation = reverseSlideAnimation,
        error = error,
    )

    fun onDelete() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            emitState()
        }
    }

    fun finished() {
        finished = false
        emitState()
    }

    fun onKeyClick(number: Int) {
        if (enteredPin.length >= PinModule.PIN_COUNT) return

        error = null
        reverseSlideAnimation = false
        enteredPin += number.toString()
        emitState()

        if (enteredPin.length != PinModule.PIN_COUNT) return

        when {
            stage == Enter -> {
                if (pinComponent.isUnique(enteredPin, forDuress)) {
                    submittedPin = enteredPin

                    enteredPin = ""

                    stage = Confirm
                    viewModelScope.launch {
                        delay(500)
                        emitState()
                    }
                } else {
                    enteredPin = ""

                    error = cash.p.terminal.strings.helpers.Translator.getString(R.string.PinSet_ErrorPinInUse)
                    emitState()
                }
            }
            submittedPin.isNotEmpty() -> {
                if (submittedPin == enteredPin) {
                    try {
                        if (forDuress) {
                            pinComponent.setDuressPin(submittedPin)
                        } else {
                            pinComponent.setPin(submittedPin)
                        }

                        finished = true
                        emitState()
                    } catch (ex: Exception) {
                        resetWithError(R.string.PinSet_ErrorFailedToSavePin)
                    }
                } else {
                    resetWithError(R.string.PinSet_ErrorPinsDontMatch)
                }
            }
        }
    }

    private fun resetWithError(errorMessage: Int) {
        submittedPin = ""
        enteredPin = ""

        stage = Enter
        reverseSlideAnimation = true
        error = cash.p.terminal.strings.helpers.Translator.getString(errorMessage)
        viewModelScope.launch {
            delay(500)
            emitState()
        }
    }

}
