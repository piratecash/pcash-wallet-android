package cash.p.terminal.modules.markdown.localreader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.ui_compose.entities.ViewState
import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.node.AstNode
import kotlinx.coroutines.launch

class MarkdownLocalViewModel : ViewModel() {

    var markdownBlocks by mutableStateOf<AstNode?>(null)
        private set

    var viewState by mutableStateOf<ViewState>(ViewState.Loading)
        private set

    fun parseContent(markdownContent: String) {
        viewModelScope.launch {
            try {
                markdownBlocks = getMarkdownBlocks(markdownContent)
                viewState = ViewState.Success
            } catch (e: Exception) {
                viewState = ViewState.Error(e)
            }
        }
    }

    private fun getMarkdownBlocks(content: String): AstNode {
        val parser = CommonmarkAstNodeParser(CommonMarkdownParseOptions.Default)
        return parser.parse(content)
    }

}
