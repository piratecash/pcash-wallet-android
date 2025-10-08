package cash.p.terminal.modules.zcashconfigure

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import cash.p.terminal.strings.helpers.Translator
import kotlinx.coroutines.launch
import java.time.LocalDate

class ZcashConfigureViewModel(
    private val getZcashHeightUseCase: GetZcashHeightUseCase
) : ViewModel() {

    var uiState by mutableStateOf(
        ZCashConfigView(
            birthdayHeight = null,
            restoreAsNew = true,
            restoreAsOld = false,
            doneButtonEnabled = true,
        )
    )
        private set

    fun restoreAsNew() {
        uiState = ZCashConfigView(
            birthdayHeight = null,
            restoreAsNew = true,
            restoreAsOld = false,
            doneButtonEnabled = true,
        )
    }

    fun restoreAsOld() {
        uiState = ZCashConfigView(
            birthdayHeight = null,
            restoreAsNew = false,
            restoreAsOld = true,
            doneButtonEnabled = true
        )
    }

    fun setBirthdayHeight(height: String) {
        uiState = ZCashConfigView(
            birthdayHeight = height,
            restoreAsNew = false,
            restoreAsOld = false,
            doneButtonEnabled = height.isNotBlank(),
            errorHeight = null
        )
    }

    fun onDoneClick() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true)

            val birthdayHeight = uiState.birthdayHeight?.trim()
            val heightDetected = if (uiState.restoreAsNew || birthdayHeight == null) {
                null
            } else {
                getLocalDate(birthdayHeight)?.let {
                    getZcashHeightUseCase(it)?.toString()
                } ?: birthdayHeight.toLongOrNull()?.toString()
            }
            val heightCorrect = uiState.restoreAsNew || (heightDetected != null)
            val closeWithResult = if (heightCorrect) {
                TokenConfig(heightDetected, uiState.restoreAsNew)
            } else {
                null
            }
            uiState = uiState.copy(
                closeWithResult = closeWithResult,
                errorHeight = if (heightCorrect) {
                    null
                } else {
                    Translator.getString(R.string.invalid_height)
                },
                loading = false
            )
        }
    }

    /**
     * Check yyyy-MM-dd format
     */
    private fun getLocalDate(height: String): LocalDate? {
        return try {
            LocalDate.parse(height)
        } catch (e: Exception) {
            null
        }
    }

    fun onClosed() {
        uiState = ZCashConfigView(
            birthdayHeight = uiState.birthdayHeight,
            restoreAsNew = uiState.restoreAsNew,
            restoreAsOld = uiState.restoreAsOld,
            doneButtonEnabled = uiState.doneButtonEnabled,
            closeWithResult = null,
        )
    }
}

data class ZCashConfigView(
    val birthdayHeight: String?,
    val restoreAsNew: Boolean,
    val restoreAsOld: Boolean,
    val doneButtonEnabled: Boolean,
    val closeWithResult: TokenConfig? = null,
    val errorHeight: String? = null,
    val loading: Boolean = false
)
