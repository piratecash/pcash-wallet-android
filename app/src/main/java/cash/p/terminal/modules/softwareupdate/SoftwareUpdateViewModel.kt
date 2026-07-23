package cash.p.terminal.modules.softwareupdate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.modules.softwareupdate.domain.InstallSource
import cash.p.terminal.modules.softwareupdate.domain.InstallSourceProvider
import cash.p.terminal.modules.softwareupdate.domain.UpdateCheckInterval
import cash.p.terminal.modules.softwareupdate.domain.UpdateStatus
import io.horizontalsystems.core.ISystemInfoManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SoftwareUpdateViewModel(
    private val appUpdateChecker: AppUpdateChecker,
    installSourceProvider: InstallSourceProvider,
    private val localStorage: ILocalStorage,
    systemInfoManager: ISystemInfoManager,
) : ViewModel() {

    var uiState by mutableStateOf(
        SoftwareUpdateUiState(
            currentVersion = systemInfoManager.appVersion,
            interval = localStorage.updateCheckInterval,
            lastCheckTimestamp = localStorage.lastUpdateCheckTimestamp.takeIf { it > 0 },
            updateStatus = appUpdateChecker.updateState.value,
            installSource = installSourceProvider.installSource,
        )
    )
        private set

    init {
        appUpdateChecker.updateState
            .onEach { status ->
                uiState = uiState.copy(
                    updateStatus = status,
                    lastCheckTimestamp = localStorage.lastUpdateCheckTimestamp.takeIf { it > 0 },
                )
            }
            .launchIn(viewModelScope)

        viewModelScope.launch { appUpdateChecker.checkNow() }
    }

    fun onIntervalChange(interval: UpdateCheckInterval) {
        localStorage.updateCheckInterval = interval
        uiState = uiState.copy(interval = interval)
    }

    fun retry() {
        viewModelScope.launch { appUpdateChecker.checkNow() }
    }
}

data class SoftwareUpdateUiState(
    val currentVersion: String,
    val interval: UpdateCheckInterval,
    val lastCheckTimestamp: Long?,
    val updateStatus: UpdateStatus,
    val installSource: InstallSource,
)
