package cash.p.terminal.trezor.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.trezor.domain.usecase.ICreateTrezorWalletUseCase
import cash.p.terminal.trezorkit.TrezorNotInitializedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class TrezorWalletViewModel(
    private val accountName: String,
    private val createTrezorWalletUseCase: ICreateTrezorWalletUseCase
) : ViewModel() {

    var uiState by mutableStateOf(TrezorSetupUiState())
        private set

    private val _sideEffects = Channel<TrezorSideEffect>(capacity = Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    private var connectJob: Job? = null

    fun connectTrezor() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            uiState = uiState.copy(loading = true, showNotInitialized = false)
            try {
                // USB device acquisition (incl. permission) happens inside the USB ITrezorClient's connect().
                createTrezorWalletUseCase(accountName)
                uiState = uiState.copy(success = true, loading = false)
            } catch (e: TrezorNotInitializedException) {
                Timber.w(e, "Trezor is not set up")
                uiState = uiState.copy(showNotInitialized = true, loading = false)
            } catch (e: TrezorCancelledException) {
                // Intentional device/user cancel - not an error, show nothing.
                uiState = uiState.copy(loading = false)
            } catch (e: CancellationException) {
                // Coroutine cancellation (e.g. a second tap cancels this job) must propagate, not HUD.
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect Trezor")
                uiState = uiState.copy(loading = false)
                _sideEffects.trySend(TrezorSideEffect.ShowError(e.message))
            }
        }
    }

    fun openSetupGuide() {
        _sideEffects.trySend(TrezorSideEffect.OpenIntent(Intent(Intent.ACTION_VIEW, Uri.parse(TREZOR_SETUP_URL))))
    }

    fun dismissNotInitialized() {
        uiState = uiState.copy(showNotInitialized = false)
    }

    companion object {
        private const val TREZOR_SETUP_URL = "https://trezor.io/start"
    }
}

data class TrezorSetupUiState(
    val loading: Boolean = false,
    val success: Boolean = false,
    val showNotInitialized: Boolean = false
)

sealed class TrezorSideEffect {
    data class OpenIntent(val intent: Intent) : TrezorSideEffect()
    data class ShowError(val message: String?) : TrezorSideEffect()
}
