package cash.p.terminal.modules.softwareupdate.changelog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.releasenotes.ReleaseNotesUiState
import cash.p.terminal.modules.softwareupdate.domain.GetReleaseChangelogUseCase
import cash.p.terminal.ui_compose.entities.ViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Backs the reused [cash.p.terminal.modules.releasenotes.ReleaseNotesScreen] with the changelog of a
 * specific minor version fetched from GitHub, so version history reuses the app's existing markdown
 * renderer and footer instead of a bespoke screen.
 */
class VersionChangelogViewModel(
    private val minor: String,
    private val isActiveBranch: Boolean,
    private val getReleaseChangelogUseCase: GetReleaseChangelogUseCase,
    private val localStorage: ILocalStorage,
) : ViewModel() {

    var uiState by mutableStateOf(
        ReleaseNotesUiState(
            viewState = ViewState.Loading,
            markdownContent = null,
            twitterUrl = AppConfigProvider.appTwitterLink,
            telegramUrl = AppConfigProvider.appTelegramLink,
            redditUrl = AppConfigProvider.appRedditLink,
            showChangelogAfterUpdate = localStorage.showChangelogAfterUpdate,
        )
    )
        private set

    init {
        load()
    }

    fun retry() = load()

    fun onShowChangelogToggle() {
        localStorage.showChangelogAfterUpdate = !localStorage.showChangelogAfterUpdate
        uiState = uiState.copy(showChangelogAfterUpdate = localStorage.showChangelogAfterUpdate)
    }

    private fun load() {
        uiState = uiState.copy(viewState = ViewState.Loading)
        viewModelScope.launch {
            val markdown = try {
                getReleaseChangelogUseCase(minor, isActiveBranch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            uiState = if (markdown.isNullOrBlank()) {
                uiState.copy(viewState = ViewState.Error(ChangelogNotFoundException()))
            } else {
                uiState.copy(viewState = ViewState.Success, markdownContent = markdown)
            }
        }
    }
}

class ChangelogNotFoundException : Exception()
