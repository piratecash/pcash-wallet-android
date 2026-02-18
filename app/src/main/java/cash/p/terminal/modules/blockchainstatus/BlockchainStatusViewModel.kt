package cash.p.terminal.modules.blockchainstatus

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.BuildConfig
import cash.p.terminal.core.App
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.core.logger.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class BlockchainStatusViewModel(
    private val provider: BlockchainStatusProvider,
    dispatcherProvider: DispatcherProvider
) : ViewModel() {

    @Volatile
    private var shareFile: File? = null

    private val _uiState = MutableStateFlow(
        BlockchainStatusUiState(
            blockchainName = provider.blockchainName,
            kitVersion = provider.kitVersion,
            statusSections = emptyList(),
            sharedSection = null,
            logBlocks = emptyList(),
            statusAsText = null,
            statusLoading = true,
            logsLoading = true
        )
    )
    val uiState: StateFlow<BlockchainStatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            val appLogs = AppLog.getRecentLog(provider.logFilterTag)
            val logBlocks = buildLogBlocks(appLogs)
            _uiState.update { it.copy(logBlocks = logBlocks, logsLoading = false) }
        }

        viewModelScope.launch(dispatcherProvider.io) {
            val status = provider.getStatus()
            _uiState.update {
                it.copy(
                    statusSections = status.sections,
                    sharedSection = status.sharedSection,
                )
            }
            rebuildShareFile()
            _uiState.update { it.copy(statusLoading = false) }
        }
    }

    fun getShareFileUri(context: Context): Uri? {
        val file = shareFile ?: return null
        return FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
    }

    private fun rebuildShareFile() {
        val state = _uiState.value
        val allLogBlocks = buildLogBlocks(AppLog.getLog(provider.logFilterTag))
        val statusAsText = buildStatusText(state.statusSections, state.sharedSection, allLogBlocks)
        _uiState.update { it.copy(statusAsText = statusAsText) }

        try {
            val file = File(App.instance.cacheDir, "${provider.logFilterTag.lowercase()}_blockchain_status_report.txt")
            file.writeText(statusAsText)
            shareFile = file
        } catch (_: Exception) {
            // File write failed, share will be unavailable
        }
    }

    private fun buildLogBlocks(appLogs: Map<String, Any>): List<LogBlock> {
        return appLogs.map { (key, value) ->
            val content = formatMapValue(value)
            LogBlock(title = key.replaceFirstChar(Char::uppercase), content = content)
        }
    }

    private fun buildStatusText(
        sections: List<StatusSection>,
        sharedSection: StatusSection?,
        logBlocks: List<LogBlock>
    ): String = buildString {
        appendLine("${provider.blockchainName} Status")
        appendLine("Kit Version: ${provider.kitVersion}")
        appendLine()

        sections.forEach { appendSection(it) }
        sharedSection?.let { appendSection(it) }

        if (logBlocks.isNotEmpty()) {
            appendLine("App Log")
            logBlocks.forEach { block ->
                appendLine(block.title)
                appendLine(block.content)
            }
        }
    }

    private fun StringBuilder.appendSection(section: StatusSection) {
        appendLine(section.title)
        section.items.forEach { item ->
            when (item) {
                is StatusItem.KeyValue -> appendLine("  ${item.key}: ${item.value}")
                is StatusItem.Nested -> {
                    appendLine("  ${item.title}:")
                    item.items.forEach { kv ->
                        appendLine("    ${kv.key}: ${kv.value}")
                    }
                }
            }
        }
        appendLine()
    }

    @Suppress("UNCHECKED_CAST")
    private fun formatMapValue(value: Any, indent: String = "  "): String = buildString {
        when (value) {
            is Map<*, *> -> {
                (value as Map<String, Any>).forEach { (k, v) ->
                    when (v) {
                        is Map<*, *> -> {
                            appendLine("$indent$k:")
                            append(formatMapValue(v, "$indent  "))
                        }
                        is Date -> {
                            val date = DateHelper.formatDate(v, "MMM d, yyyy, HH:mm")
                            appendLine("$indent$k: $date")
                        }
                        else -> appendLine("$indent$k: $v")
                    }
                }
            }
            else -> appendLine("$indent$value")
        }
    }
}

data class LogBlock(
    val title: String,
    val content: String
)

@Immutable
data class BlockchainStatusUiState(
    val blockchainName: String,
    val kitVersion: String,
    val statusSections: List<StatusSection>,
    val sharedSection: StatusSection?,
    val logBlocks: List<LogBlock>,
    val statusAsText: String?,
    val statusLoading: Boolean,
    val logsLoading: Boolean
) {
    val loading: Boolean get() = statusLoading || logsLoading
}
