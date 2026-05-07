package cash.p.terminal.modules.importwallet

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.managers.toSeedQrErrorStringRes
import cash.p.terminal.core.openInputStreamSafe
import cash.p.terminal.core.validateAndSaveBackup
import cash.p.terminal.strings.helpers.Translator
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.hdwalletkit.Language
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ImportWalletViewModel(
    private val seedPhraseQrCrypto: SeedPhraseQrCrypto,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var backupFileError by mutableStateOf(false)
        private set

    fun processBackupFile(contentResolver: ContentResolver, uri: Uri, fileName: String?) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                contentResolver.openInputStreamSafe(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val backupFilePath = validateAndSaveBackup(bytes)
                    _navigationEvents.send(
                        NavigationEvent.OpenRestoreLocal(backupFilePath, fileName)
                    )
                } ?: throw IllegalStateException("Cannot open input stream")
            } catch (e: Throwable) {
                backupFileError = true
            }
        }
    }

    fun onBackupFileErrorShown() {
        backupFileError = false
    }

    fun handleScannedData(scannedText: String) {
        if (scannedText.startsWith(SeedPhraseQrCrypto.QR_PREFIX)) {
            seedPhraseQrCrypto.decrypt(scannedText)
                .onSuccess { decrypted ->
                    viewModelScope.launch {
                        _navigationEvents.send(
                            NavigationEvent.OpenRestoreFromQr(
                                words = decrypted.words,
                                passphrase = decrypted.passphrase,
                                moneroHeight = decrypted.height,
                                language = decrypted.language
                            )
                        )
                    }
                }
                .onFailure { error ->
                    errorMessage = Translator.getString(error.toSeedQrErrorStringRes())
                }
        } else {
            errorMessage = Translator.getString(R.string.seed_qr_invalid_format)
        }
    }

    fun onErrorShown() {
        errorMessage = null
    }

    sealed class NavigationEvent {
        data class OpenRestoreFromQr(
            val words: List<String>,
            val passphrase: String,
            val moneroHeight: Long?,
            val language: Language?
        ) : NavigationEvent()

        data class OpenRestoreLocal(
            val backupFilePath: String,
            val fileName: String?
        ) : NavigationEvent()
    }
}
