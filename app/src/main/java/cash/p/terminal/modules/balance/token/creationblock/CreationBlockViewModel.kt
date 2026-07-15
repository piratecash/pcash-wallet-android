package cash.p.terminal.modules.balance.token.creationblock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.usecase.GetRestoreHeightForWalletUseCase
import cash.p.terminal.core.usecase.RescanMoneroUseCase
import cash.p.terminal.core.usecase.RescanZcashUseCase
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.network.zcash.domain.usecase.GetZcashHeightUseCase
import cash.p.terminal.network.zcash.domain.usecase.ZcashHeightResult
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class CreationBlockViewModel(
    private val wallet: Wallet,
    private val getRestoreHeightForWalletUseCase: GetRestoreHeightForWalletUseCase,
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase,
    private val getZcashHeightUseCase: GetZcashHeightUseCase,
    private val rescanMoneroUseCase: RescanMoneroUseCase,
    private val rescanZcashUseCase: RescanZcashUseCase,
) : ViewModel() {

    private val blockchainType = wallet.token.blockchainType
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    private var initialHeight: String = ""

    var uiState by mutableStateOf(CreationBlockUiState(blockchainType = blockchainType))
        private set

    init {
        uiState = uiState.copy(blockchainType = blockchainType)
        viewModelScope.launch {
            val height = getRestoreHeightForWalletUseCase(wallet)
            initialHeight = height?.toString().orEmpty()
            // Publish the locally-known height first; the Zcash date lookup is a network call and
            // must not gate the field. A later copy only touches blockDateText, so it can't clobber
            // a height the user typed while the request was in flight.
            uiState = uiState.copy(heightText = initialHeight)
            val blockDate = height?.let { blockDateForHeight(it)?.format(dateFormatter) }
            uiState = uiState.copy(blockDateText = blockDate)
        }
    }

    fun onHeightChange(text: String) {
        uiState = uiState.copy(
            heightText = text,
            changed = isChanged(text),
            error = null
        )
        // Monero height→date is an offline, non-suspending lookup, so it can refresh live as the
        // user types. Zcash reverse lookup needs the network, so it is only refreshed on date pick.
        if (blockchainType == BlockchainType.Monero) {
            val date = text.toLongOrNull()?.let { validateMoneroHeightUseCase.getDateForHeight(it) }
            uiState = uiState.copy(blockDateText = date?.format(dateFormatter))
        }
    }

    fun onDatePicked(date: LocalDate) {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            val height = heightForDate(date)
            uiState = if (height != null) {
                val heightText = height.toString()
                uiState.copy(
                    heightText = heightText,
                    blockDateText = date.format(dateFormatter),
                    changed = isChanged(heightText),
                    loading = false
                )
            } else {
                uiState.copy(loading = false)
            }
        }
    }

    fun onRescanConfirmed() {
        val newHeight = uiState.heightText.trim().toLongOrNull()
        if (newHeight == null || newHeight <= 0) {
            uiState = uiState.copy(error = Translator.getString(R.string.invalid_height_format))
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                when (blockchainType) {
                    BlockchainType.Monero -> rescanMoneroUseCase(wallet.account, newHeight)
                    BlockchainType.Zcash -> rescanZcashUseCase(wallet.account, newHeight)
                    else -> Unit
                }
                initialHeight = uiState.heightText
                uiState = uiState.copy(loading = false, changed = false, rescanStarted = true)
            } catch (e: Throwable) {
                uiState = uiState.copy(
                    loading = false,
                    error = e.message ?: Translator.getString(R.string.Error)
                )
            }
        }
    }

    private fun isChanged(text: String): Boolean {
        val trimmed = text.trim()
        val height = trimmed.toLongOrNull()
        return trimmed != initialHeight.trim() && height != null && height > 0
    }

    private suspend fun heightForDate(date: LocalDate): Long? = when (blockchainType) {
        BlockchainType.Monero -> validateMoneroHeightUseCase.getHeight(date).takeIf { it != -1L }
            ?: run {
                uiState = uiState.copy(error = Translator.getString(R.string.invalid_height_format))
                null
            }

        BlockchainType.Zcash -> when (val result = getZcashHeightUseCase(date)) {
            is ZcashHeightResult.Success -> result.height
            ZcashHeightResult.NotFound -> {
                uiState = uiState.copy(error = Translator.getString(R.string.invalid_height))
                null
            }

            ZcashHeightResult.NetworkError -> {
                uiState = uiState.copy(
                    error = Translator.getString(R.string.blockchair_height_by_date_connection_error)
                )
                null
            }
        }

        else -> null
    }

    private suspend fun blockDateForHeight(height: Long): LocalDate? = when (blockchainType) {
        BlockchainType.Monero -> validateMoneroHeightUseCase.getDateForHeight(height)
        BlockchainType.Zcash -> getZcashHeightUseCase.getDateForHeight(height)
        else -> null
    }
}

data class CreationBlockUiState(
    val blockchainType: BlockchainType,
    val heightText: String = "",
    val blockDateText: String? = null,
    val changed: Boolean = false,
    val error: String? = null,
    val loading: Boolean = false,
    val rescanStarted: Boolean = false,
)
