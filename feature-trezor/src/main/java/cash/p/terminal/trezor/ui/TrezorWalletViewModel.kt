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
import cash.p.terminal.trezor.domain.usecase.TrezorMoneroRestoreHeightProvider
import cash.p.terminal.trezor.domain.usecase.TrezorMoneroRestoreHeightResolver
import cash.p.terminal.trezorkit.TrezorNotInitializedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class TrezorWalletViewModel(
    private val accountName: String,
    private val createTrezorWalletUseCase: ICreateTrezorWalletUseCase,
    private val moneroRestoreHeightResolver: TrezorMoneroRestoreHeightResolver,
) : ViewModel() {

    var uiState by mutableStateOf(TrezorSetupUiState())
        private set

    private val _sideEffects = Channel<TrezorSideEffect>(capacity = Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    private var connectJob: Job? = null
    private var moneroRestoreHeightRequest: CompletableDeferred<Long>? = null

    fun connectTrezor() {
        if (connectJob?.isActive == true) return

        connectJob = viewModelScope.launch {
            uiState = uiState.copy(loading = true, showNotInitialized = false)
            try {
                // USB device acquisition (incl. permission) happens inside the USB ITrezorClient's connect().
                createTrezorWalletUseCase(
                    accountName = accountName,
                    moneroRestoreHeightProvider = object : TrezorMoneroRestoreHeightProvider {
                        override suspend fun getRestoreHeight(): Long =
                            requestMoneroRestoreHeight()
                    },
                )
                uiState = uiState.copy(success = true)
            } catch (e: TrezorNotInitializedException) {
                Timber.w(e, "Trezor is not set up")
                uiState = uiState.copy(showNotInitialized = true)
            } catch (e: TrezorCancelledException) {
                // Intentional device/user cancel - not an error, show nothing.
            } catch (e: CancellationException) {
                // Coroutine cancellation must propagate, not HUD.
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect Trezor")
                _sideEffects.trySend(TrezorSideEffect.ShowError(e.message))
            } finally {
                uiState = uiState.copy(loading = false)
            }
        }
    }

    fun selectMoneroRestoreMode(mode: TrezorMoneroRestoreMode) {
        uiState = uiState.copy(
            moneroRestore = uiState.moneroRestore.copy(
                mode = mode,
                heightOrDate = "",
                invalidHeight = false,
                automaticHeightUnavailable = false,
            ),
        )
    }

    fun setMoneroRestoreHeight(value: String) {
        uiState = uiState.copy(
            moneroRestore = uiState.moneroRestore.copy(
                heightOrDate = value,
                invalidHeight = false,
                automaticHeightUnavailable = false,
            ),
        )
    }

    fun submitMoneroRestore() {
        val request = moneroRestoreHeightRequest ?: return
        val height = when (uiState.moneroRestore.mode) {
            TrezorMoneroRestoreMode.NewWallet ->
                moneroRestoreHeightResolver.getTodayHeight()
            TrezorMoneroRestoreMode.ExistingWallet ->
                moneroRestoreHeightResolver.resolve(uiState.moneroRestore.heightOrDate)
            null -> return
        }
        if (height < 0) {
            val automaticHeightUnavailable =
                uiState.moneroRestore.mode == TrezorMoneroRestoreMode.NewWallet
            uiState = uiState.copy(
                moneroRestore = uiState.moneroRestore.copy(
                    invalidHeight = !automaticHeightUnavailable,
                    automaticHeightUnavailable = automaticHeightUnavailable,
                ),
            )
            return
        }

        uiState = uiState.copy(
            loading = true,
            moneroRestore = TrezorMoneroRestoreUiState(),
        )
        request.complete(height)
    }

    private suspend fun requestMoneroRestoreHeight(): Long {
        check(moneroRestoreHeightRequest == null) {
            "Monero restore height is already being requested"
        }
        val request = CompletableDeferred<Long>()
        moneroRestoreHeightRequest = request
        uiState = uiState.copy(
            loading = false,
            moneroRestore = TrezorMoneroRestoreUiState(visible = true),
        )
        return try {
            request.await()
        } finally {
            if (moneroRestoreHeightRequest === request) {
                moneroRestoreHeightRequest = null
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
    val showNotInitialized: Boolean = false,
    val moneroRestore: TrezorMoneroRestoreUiState = TrezorMoneroRestoreUiState(),
)

data class TrezorMoneroRestoreUiState(
    val visible: Boolean = false,
    val mode: TrezorMoneroRestoreMode? = null,
    val heightOrDate: String = "",
    val invalidHeight: Boolean = false,
    val automaticHeightUnavailable: Boolean = false,
) {
    val canSubmit: Boolean
        get() = mode == TrezorMoneroRestoreMode.NewWallet ||
            (mode == TrezorMoneroRestoreMode.ExistingWallet && heightOrDate.isNotBlank())
}

enum class TrezorMoneroRestoreMode {
    NewWallet,
    ExistingWallet,
}

sealed class TrezorSideEffect {
    data class OpenIntent(val intent: Intent) : TrezorSideEffect()
    data class ShowError(val message: String?) : TrezorSideEffect()
}
