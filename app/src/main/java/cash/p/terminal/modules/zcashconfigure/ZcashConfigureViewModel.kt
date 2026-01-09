package cash.p.terminal.modules.zcashconfigure

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import cash.p.terminal.network.zcash.domain.usecase.ZcashHeightResult
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

    fun setInitialConfig(config: TokenConfig?) {
        if (config == null) return

        val isNew = config.restoreAsNew
        val height = config.birthdayHeight
        uiState = uiState.copy(
            birthdayHeight = height,
            restoreAsNew = isNew,
            restoreAsOld = !isNew,
            doneButtonEnabled = isNew || !height.isNullOrBlank(),
            errorHeight = null,
            closeWithResult = null,
            loading = false
        )
    }

    fun onDoneClick() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true)

            val birthdayHeight = uiState.birthdayHeight?.trim().takeUnless { it.isNullOrBlank() }
            var errorMessage: String? = null
            val heightDetected = if (uiState.restoreAsNew) {
                getZcashHeightUseCase.getCurrentBlockHeight()?.toString()
            } else {
                birthdayHeight?.let { heightInput ->
                    val detectedDate = getLocalDate(heightInput)
                    if (detectedDate != null) {
                        when (val result = getZcashHeightUseCase(detectedDate)) {
                            is ZcashHeightResult.Success -> result.height.toString()
                            ZcashHeightResult.NotFound -> {
                                errorMessage = Translator.getString(R.string.invalid_height)
                                null
                            }
                            ZcashHeightResult.NetworkError -> {
                                errorMessage = Translator.getString(
                                    R.string.blockchair_height_by_date_connection_error
                                )
                                null
                            }
                        }
                    } else {
                        heightInput.toLongOrNull()?.toString().also {
                            if (it == null) {
                                errorMessage = Translator.getString(R.string.invalid_height_format)
                            }
                        }
                    }
                }
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
                    errorMessage ?: Translator.getString(R.string.invalid_height)
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
