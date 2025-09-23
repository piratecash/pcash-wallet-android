package cash.p.terminal.modules.releasenotes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.ReleaseNotesManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.domain.usecase.GetLocalizedAssetUseCase
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.markdown.MarkdownBlock
import cash.p.terminal.modules.markdown.MarkdownVisitorBlock
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.commonmark.parser.Parser

class ReleaseNotesViewModel(
    private val getLocalizedAssetUseCase: GetLocalizedAssetUseCase,
    private val localStorage: ILocalStorage
) : ViewModel() {

    private val connectivityManager: ConnectivityManager = App.connectivityManager
    private val releaseNotesManager: ReleaseNotesManager = App.releaseNotesManager

    var uiState by mutableStateOf(
        ReleaseNotesUiState(
            twitterUrl = AppConfigProvider.appTwitterLink,
            telegramUrl = AppConfigProvider.appTelegramLink,
            redditUrl = AppConfigProvider.appRedditLink,
            showChangelogAfterUpdate = localStorage.showChangelogAfterUpdate,
        )
    )
        private set

    init {
        loadContent()

        connectivityManager.networkAvailabilityFlow
            .onEach {
                if (connectivityManager.isConnected.value && uiState.viewState is ViewState.Error) {
                    retry()
                }
            }
            .launchIn(viewModelScope)
    }

    fun retry() {
        uiState = uiState.copy(viewState = ViewState.Loading)
        loadContent()
    }

    fun whatsNewShown() {
        releaseNotesManager.updateShownAppVersion()
    }

    private fun loadContent() {
        viewModelScope.launch {
            try {
                val content = getLocalizedAssetUseCase(GetLocalizedAssetUseCase.CHANGELOG_PREFIX)
                val markdownBlocks = getMarkdownBlocks(content)
                uiState = uiState.copy(
                    viewState = ViewState.Success,
                    markdownBlocks = markdownBlocks
                )
            } catch (e: Exception) {
                uiState = uiState.copy(viewState = ViewState.Error(e))
            }
        }
    }

    private fun getMarkdownBlocks(content: String): List<MarkdownBlock> {
        val parser = Parser.builder().build()
        val document = parser.parse(content)

        val markdownVisitor = MarkdownVisitorBlock()

        document.accept(markdownVisitor)

        return markdownVisitor.blocks + MarkdownBlock.Footer()
    }

    fun setShowChangeLogAfterUpdate() {
        localStorage.showChangelogAfterUpdate = !localStorage.showChangelogAfterUpdate
        uiState = uiState.copy(showChangelogAfterUpdate = localStorage.showChangelogAfterUpdate)
    }
}

data class ReleaseNotesUiState(
    val viewState: ViewState = ViewState.Loading,
    val markdownBlocks: List<MarkdownBlock> = emptyList(),
    val twitterUrl: String,
    val telegramUrl: String,
    val redditUrl: String,
    val showChangelogAfterUpdate: Boolean
)
