package cash.p.terminal.modules.settings.main

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.IBackupManager
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.settings.main.MainSettingsModule.CounterType
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.WCSessionManager
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.supportsTonConnect
import cash.z.ecc.android.sdk.ext.collectWith
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx2.asFlow
import org.koin.java.KoinJavaComponent.inject

class MainSettingsViewModel(
    private val backupManager: IBackupManager,
    private val systemInfoManager: ISystemInfoManager,
    private val termsManager: ITermsManager,
    private val pinComponent: IPinComponent,
    private val wcSessionManager: WCSessionManager,
    private val wcManager: WCManager,
    private val accountManager: IAccountManager,
    private val languageManager: LanguageManager,
    private val currencyManager: CurrencyManager,
) : ViewModelUiState<MainSettingUiState>() {

    private val localStorage: ILocalStorage by inject(ILocalStorage::class.java)

    val appVersion: String
        get() {
            var appVersion = systemInfoManager.appVersion
            if (cash.p.terminal.strings.helpers.Translator.getString(R.string.is_release) == "false") {
                appVersion += " (${AppConfigProvider.appBuild})"
            }

            return appVersion
        }

    val companyWebPage = AppConfigProvider.companyWebPageLink

    val walletConnectSupportState: WCManager.SupportState
        get() = wcManager.getWalletConnectSupportState()

    private val currentLanguageDisplayName: String
        get() = languageManager.currentLanguageName

    private val baseCurrencyCode: String
        get() = currencyManager.baseCurrency.code

    private val appWebPageLink = AppConfigProvider.appWebPageLink
    private val hasNonStandardAccount: Boolean
        get() = accountManager.hasNonStandardAccount

    private val allBackedUp: Boolean
        get() = backupManager.allBackedUp

    private val walletConnectSessionCount: Int
        get() = wcSessionManager.sessions.count()

    private val isPinSet: Boolean
        get() = pinComponent.isPinSet

    val currentAccountSupportsTonConnect: Boolean
        get() = accountManager.activeAccount?.supportsTonConnect() == true


    private var wcCounterType: CounterType? = null
    private var wcSessionsCount = walletConnectSessionCount
    private var wcPendingRequestCount = 0

    init {
        viewModelScope.launch {
            backupManager.allBackedUpFlow.collect {
                emitState()
            }
        }
        viewModelScope.launch {
            wcSessionManager.sessionsFlow.collect {
                wcSessionsCount = walletConnectSessionCount
                syncCounter()
            }
        }
        viewModelScope.launch {
            pinComponent.pinSetFlowable.asFlow().collect {
                emitState()
            }
        }

        viewModelScope.launch {
            termsManager.termsAcceptedSignalFlow.collect {
                emitState()
            }
        }

        viewModelScope.launch {
            wcSessionManager.pendingRequestCountFlow.collect {
                wcPendingRequestCount = it
                syncCounter()
            }
        }
        viewModelScope.launch {
            currencyManager.baseCurrencyUpdatedSignal.asFlow().collect {
                emitState()
            }
        }

        syncCounter()
    }

    override fun createState(): MainSettingUiState {
        return MainSettingUiState(
            currentLanguage = currentLanguageDisplayName,
            baseCurrencyCode = baseCurrencyCode,
            appWebPageLink = appWebPageLink,
            hasNonStandardAccount = hasNonStandardAccount,
            allBackedUp = allBackedUp,
            pendingRequestCount = wcPendingRequestCount,
            walletConnectSessionCount = wcSessionsCount,
            manageWalletShowAlert = !allBackedUp || hasNonStandardAccount,
            securityCenterShowAlert = !isPinSet || !localStorage.isSystemPinRequired,
            aboutAppShowAlert = !termsManager.allTermsAccepted,
            wcCounterType = wcCounterType
        )
    }

    private fun syncCounter() {
        if (wcPendingRequestCount > 0) {
            wcCounterType = CounterType.PendingRequestCounter(wcPendingRequestCount)
        } else if (wcSessionsCount > 0) {
            wcCounterType = CounterType.SessionCounter(wcSessionsCount)
        } else {
            wcCounterType = null
        }
        emitState()
    }
}

data class MainSettingUiState(
    val currentLanguage: String,
    val baseCurrencyCode: String,
    val appWebPageLink: String,
    val hasNonStandardAccount: Boolean,
    val allBackedUp: Boolean,
    val pendingRequestCount: Int,
    val walletConnectSessionCount: Int,
    val manageWalletShowAlert: Boolean,
    val securityCenterShowAlert: Boolean,
    val aboutAppShowAlert: Boolean,
    val wcCounterType: CounterType?
)
