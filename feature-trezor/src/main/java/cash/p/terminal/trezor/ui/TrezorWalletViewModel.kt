package cash.p.terminal.trezor.ui

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.trezor.domain.TrezorSuiteInstallChecker
import cash.p.terminal.trezor.domain.usecase.ICreateTrezorWalletUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class TrezorWalletViewModel(
    private val accountName: String,
    private val createTrezorWalletUseCase: ICreateTrezorWalletUseCase,
    private val installChecker: TrezorSuiteInstallChecker
) : ViewModel() {

    var uiState by mutableStateOf(TrezorSetupUiState())
        private set

    private val _sideEffects = Channel<TrezorSideEffect>(capacity = Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    private var connectJob: Job? = null

    fun connectTrezor() {
        if (!installChecker.isInstalled()) {
            uiState = uiState.copy(showInstallPrompt = true)
            return
        }

        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                createTrezorWalletUseCase(accountName)
                uiState = uiState.copy(success = true, loading = false)
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect Trezor")
                uiState = uiState.copy(error = e.message, loading = false)
            }
        }
    }

    fun openPlayStore() {
        val intent = installChecker.getPlayStoreIntent()
        _sideEffects.trySend(TrezorSideEffect.OpenIntent(intent))
    }

    fun dismissInstallPrompt() {
        uiState = uiState.copy(showInstallPrompt = false)
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }
}

data class TrezorSetupUiState(
    val loading: Boolean = false,
    val success: Boolean = false,
    val showInstallPrompt: Boolean = false,
    val error: String? = null
)

sealed class TrezorSideEffect {
    data class OpenIntent(val intent: Intent) : TrezorSideEffect()
}
