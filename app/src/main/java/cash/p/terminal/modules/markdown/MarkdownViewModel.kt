package cash.p.terminal.modules.markdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.INetworkManager
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.ui_compose.entities.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.net.URL

class MarkdownViewModel(
    private val networkManager: INetworkManager,
    private val contentUrl: String,
    private val connectivityManager: ConnectivityManager,
) : ViewModel() {

    var markdownContent by mutableStateOf<String?>(null)
        private set

    var viewState by mutableStateOf<ViewState>(ViewState.Loading)
        private set

    init {
        loadContent()

        connectivityManager.networkAvailabilityFlow
            .onEach {
                if (connectivityManager.isConnected.value && viewState is ViewState.Error) {
                    retry()
                }
            }
            .launchIn(viewModelScope)
    }

    fun retry() {
        viewState = ViewState.Loading
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            try {
                markdownContent = getContent()
                viewState = ViewState.Success
            } catch (e: Exception) {
                viewState = ViewState.Error(e)
            }
        }
    }

    private suspend fun getContent(): String {
        val url = URL(contentUrl)
        return networkManager.getMarkdown("${url.protocol}://${url.host}", contentUrl)
    }

}
