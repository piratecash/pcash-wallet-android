package cash.p.terminal.modules.settings.about

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ITermsManager
import cash.p.terminal.core.providers.AppConfigProvider
import io.horizontalsystems.core.ISystemInfoManager
import kotlinx.coroutines.launch

class AboutViewModel(
    private val termsManager: ITermsManager,
    private val systemInfoManager: ISystemInfoManager,
) : ViewModel() {

    val githubLink = AppConfigProvider.appGithubLink
    val appWebPageLink = AppConfigProvider.appWebPageLink
    val appVersion: String get() = systemInfoManager.appVersionDisplay

    var termsShowAlert by mutableStateOf(!termsManager.allTermsAccepted)
        private set

    init {
        viewModelScope.launch {
            termsManager.termsAcceptedSignalFlow.collect {
                termsShowAlert = !it
            }
        }
    }

}
