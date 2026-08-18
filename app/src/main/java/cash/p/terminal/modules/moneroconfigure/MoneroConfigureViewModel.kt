package cash.p.terminal.modules.moneroconfigure

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.R
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.modules.enablecoin.restoresettings.BirthdayHeightConfigUiState
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.components.RestoreHeightMode
import cash.p.terminal.ui_compose.components.toRestoreHeightMode
import java.time.LocalDate

class MoneroConfigureViewModel(
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase,
) : ViewModel() {

    var uiState by mutableStateOf(
        BirthdayHeightConfigUiState(birthdayHeight = "")
    )
        private set

    fun onModeSelect(mode: RestoreHeightMode) {
        uiState = uiState.copy(mode = mode)
    }

    fun setBirthdayHeight(height: String) {
        uiState = uiState.copy(
            birthdayHeight = height,
            errorHeight = null
        )
    }

    fun onDatePicked(date: LocalDate) {
        val height = validateMoneroHeightUseCase.getHeight(date)
        if (height == -1L) {
            uiState = uiState.copy(errorHeight = Translator.getString(R.string.invalid_height_format))
        } else {
            setBirthdayHeight(height.toString())
        }
    }

    fun setInitialConfig(config: TokenConfig?) {
        if (config == null) return

        uiState = uiState.copy(
            birthdayHeight = config.birthdayHeight.orEmpty(),
            mode = config.restoreAsNew.toRestoreHeightMode(),
            errorHeight = null,
            closeWithResult = null
        )
    }

    fun onDoneClick() {
        val heightDetected = if (uiState.restoreAsNew) {
            validateMoneroHeightUseCase.getTodayHeight()
        } else {
            validateMoneroHeightUseCase(uiState.birthdayHeight)
        }
        uiState = uiState.copy(
            closeWithResult = if (heightDetected != -1L) {
                TokenConfig(heightDetected.toString(), uiState.restoreAsNew)
            } else {
                null
            },
            errorHeight = if (heightDetected == -1L) {
                Translator.getString(R.string.invalid_height_format)
            } else {
                null
            }
        )
    }

    fun onClosed() {
        uiState = uiState.copy(closeWithResult = null)
    }
}
