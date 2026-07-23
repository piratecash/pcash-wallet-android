package cash.p.terminal.modules.softwareupdate.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.softwareupdate.domain.GetVersionHistoryUseCase
import cash.p.terminal.network.github.domain.entity.AppRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class VersionHistoryViewModel(
    private val getVersionHistoryUseCase: GetVersionHistoryUseCase,
) : ViewModel() {

    var uiState by mutableStateOf(VersionHistoryUiState())
        private set

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        uiState = uiState.copy(loading = true, error = false)
        viewModelScope.launch {
            uiState = try {
                val history = getVersionHistoryUseCase()
                VersionHistoryUiState(
                    current = history.current,
                    currentIsActiveBranch = history.currentIsActiveBranch,
                    oldMinors = history.oldMinors,
                    loading = false,
                    error = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState.copy(loading = false, error = true)
            }
        }
    }
}

data class VersionHistoryUiState(
    val current: AppRelease? = null,
    val currentIsActiveBranch: Boolean = false,
    val oldMinors: List<String> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
)
