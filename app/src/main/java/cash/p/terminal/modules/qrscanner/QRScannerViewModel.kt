package cash.p.terminal.modules.qrscanner

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QRScannerViewModel(
    private val qrCodeImageDecoder: QrCodeImageDecoder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QRScannerUiState())
    val uiState = _uiState.asStateFlow()

    private val _scanResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scanResult = _scanResult.asSharedFlow()

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDecodingFromImage = true, errorMessageRes = null) }

            qrCodeImageDecoder.decode(uri).onSuccess { decoded ->
                _uiState.update { state -> state.copy(isDecodingFromImage = false) }
                _scanResult.emit(decoded)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isDecodingFromImage = false,
                        errorMessageRes = R.string.image_decode_failed
                    )
                }
            }
        }
    }

    fun onErrorMessageConsumed() {
        _uiState.update { it.copy(errorMessageRes = null) }
    }
}

data class QRScannerUiState(
    val isDecodingFromImage: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)
