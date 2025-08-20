package cash.p.terminal.modules.moneroconfigure

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.R
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.strings.helpers.Translator

class MoneroConfigureViewModel(
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase,
) : ViewModel() {

    var uiState by mutableStateOf(
        MoneroConfigUIState(
            birthdayHeight = "",
            restoreAsNew = true,
        )
    )
        private set

    fun onRestoreNew(restoreNew: Boolean) {
        uiState = uiState.copy(
            restoreAsNew = restoreNew,
        )
    }

    fun setBirthdayHeight(height: String) {
        uiState = uiState.copy(
            birthdayHeight = height,
            errorHeight = null
        )
    }

    fun onDoneClick() {
        val heightDetected =  if (uiState.restoreAsNew) {
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
                Translator.getString(R.string.inavlid_height)
            } else {
                null
            }
        )
    }
}

data class MoneroConfigUIState(
    val birthdayHeight: String,
    val restoreAsNew: Boolean,
    val closeWithResult: TokenConfig? = null,
    var errorHeight: String? = null
)
