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
import cash.p.terminal.ui_compose.components.RestoreHeightMode
import cash.p.terminal.ui_compose.components.isNewWallet
import cash.p.terminal.ui_compose.components.isSelected
import cash.p.terminal.ui_compose.components.toRestoreHeightMode
import kotlinx.coroutines.launch
import java.time.LocalDate

class ZcashConfigureViewModel(
    private val getZcashHeightUseCase: GetZcashHeightUseCase
) : ViewModel() {

    var uiState by mutableStateOf(
        ZCashConfigView(
            birthdayHeight = null,
            mode = null,
        )
    )
        private set

    fun onModeSelect(mode: RestoreHeightMode) {
        uiState = ZCashConfigView(
            birthdayHeight = null,
            mode = mode,
        )
    }

    fun setBirthdayHeight(height: String) {
        uiState = uiState.copy(
            birthdayHeight = height,
            errorHeight = null
        )
    }

    fun setInitialConfig(config: TokenConfig?) {
        if (config == null) return

        uiState = uiState.copy(
            birthdayHeight = config.birthdayHeight,
            mode = config.restoreAsNew.toRestoreHeightMode(),
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
                        val (height, error) = resolveHeightForDate(detectedDate)
                        errorMessage = error
                        height
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

    fun onDatePicked(date: LocalDate) {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true)

            val (height, error) = resolveHeightForDate(date)
            uiState = uiState.copy(
                birthdayHeight = height ?: uiState.birthdayHeight,
                errorHeight = error,
                loading = false
            )
        }
    }

    private suspend fun resolveHeightForDate(date: LocalDate): Pair<String?, String?> {
        return when (val result = getZcashHeightUseCase(date)) {
            is ZcashHeightResult.Success -> result.height.toString() to null
            ZcashHeightResult.NotFound -> null to Translator.getString(R.string.invalid_height)
            ZcashHeightResult.NetworkError -> null to Translator.getString(
                R.string.blockchair_height_by_date_connection_error
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
        uiState = uiState.copy(closeWithResult = null)
    }
}

data class ZCashConfigView(
    val birthdayHeight: String?,
    val mode: RestoreHeightMode?,
    val closeWithResult: TokenConfig? = null,
    val errorHeight: String? = null,
    val loading: Boolean = false
) {
    val restoreAsNew: Boolean get() = mode.isNewWallet
    val doneEnabled: Boolean get() = mode.isSelected
}
