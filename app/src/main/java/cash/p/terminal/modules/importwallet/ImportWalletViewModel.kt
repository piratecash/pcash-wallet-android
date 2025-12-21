package cash.p.terminal.modules.importwallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.strings.helpers.Translator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ImportWalletViewModel(
    private val seedPhraseQrCrypto: SeedPhraseQrCrypto
) : ViewModel() {

    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun handleScannedData(scannedText: String) {
        if (scannedText.startsWith(SeedPhraseQrCrypto.QR_PREFIX)) {
            seedPhraseQrCrypto.decrypt(scannedText)
                .onSuccess { decrypted ->
                    viewModelScope.launch {
                        _navigationEvents.send(
                            NavigationEvent.OpenRestoreFromQr(
                                words = decrypted.words,
                                passphrase = decrypted.passphrase,
                                moneroHeight = decrypted.height
                            )
                        )
                    }
                }
                .onFailure {
                    errorMessage = Translator.getString(R.string.seed_qr_decryption_failed)
                }
        } else {
            errorMessage = Translator.getString(R.string.seed_qr_decryption_failed)
        }
    }

    fun onErrorShown() {
        errorMessage = null
    }

    sealed class NavigationEvent {
        data class OpenRestoreFromQr(
            val words: List<String>,
            val passphrase: String,
            val moneroHeight: Long?
        ) : NavigationEvent()
    }
}
