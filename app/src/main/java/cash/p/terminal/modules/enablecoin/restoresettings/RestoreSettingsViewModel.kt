package cash.p.terminal.modules.enablecoin.restoresettings

import android.os.Parcelable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.managers.AccountCleaner
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.Token
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.parcelize.Parcelize
import org.koin.java.KoinJavaComponent.inject

class RestoreSettingsViewModel(
    private val service: RestoreSettingsService,
    private val clearables: List<Clearable>,
) : ViewModel(), IRestoreSettingsUi {

    override var openTokenConfigure by mutableStateOf<Token?>(null)
        private set

    private var currentRequest: RestoreSettingsService.Request? = null
    private var currentRequestConfig: TokenConfig? = null

    private val accountCleaner: AccountCleaner by inject(AccountCleaner::class.java)

    init {
        viewModelScope.launch {
            service.requestObservable.asFlow().collect {
                handleRequest(it)
            }
        }
    }

    private fun handleRequest(request: RestoreSettingsService.Request) {
        currentRequest = request
        currentRequestConfig = request.initialConfig

        when (request.requestType) {
            RestoreSettingsService.RequestType.BirthdayHeight -> {
                openTokenConfigure = request.token
            }
        }
    }

    override fun onEnter(tokenConfig: TokenConfig) {
        val request = currentRequest ?: return

        viewModelScope.launch {
            when (request.requestType) {
                RestoreSettingsService.RequestType.BirthdayHeight -> {
                    val changed =
                        request.initialConfig?.birthdayHeight != tokenConfig.birthdayHeight
                    if (changed) {
                        // Clear wallet DB
                        accountCleaner.clearWalletForCurrentAccount(request.token.blockchainType)
                    }
                    service.enter(tokenConfig, request.token)
                }
            }
        }
    }

    override fun onCancelEnterBirthdayHeight() {
        val request = currentRequest ?: return

        service.cancel(request.token)
    }

    override fun onCleared() {
        clearables.forEach(Clearable::clear)
    }

    override fun tokenConfigureOpened() {
        openTokenConfigure = null
    }

    override fun consumeInitialConfig(): TokenConfig? {
        return currentRequestConfig.also {
            currentRequestConfig = null
        }
    }
}

@Parcelize
data class TokenConfig(val birthdayHeight: String?, val restoreAsNew: Boolean) : Parcelable

interface IRestoreSettingsUi {
    val openTokenConfigure: Token?

    fun tokenConfigureOpened()
    fun consumeInitialConfig(): TokenConfig?
    fun onEnter(tokenConfig: TokenConfig)
    fun onCancelEnterBirthdayHeight()
}
