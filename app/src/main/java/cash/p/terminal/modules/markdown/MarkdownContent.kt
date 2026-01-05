package cash.p.terminal.modules.markdown

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.theme.Grey50
import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.AstBlockNodeComposer
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.markdown.node.AstBlockNodeType
import com.halilibo.richtext.markdown.node.AstHeading
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.ui.Heading
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText

@Composable
fun MarkdownContent(
    modifier: Modifier = Modifier,
    viewState: ViewState? = null,
    markdownContent: String?,
    scrollableContent: Boolean = true,
    addFooter: Boolean,
    onRetryClick: () -> Unit,
    onUrlClick: (String) -> Unit,
) {
    // Parse markdown to AstNode locally using remember (not saveable) to avoid Parcel issues
    val markdownBlocks = remember(markdownContent) {
        markdownContent?.let {
            val parser = CommonmarkAstNodeParser(CommonMarkdownParseOptions.Default)
            parser.parse(it)
        }
    }
    val colors = ComposeAppTheme.colors
    val richTextStyle by remember {
        mutableStateOf(
            RichTextStyle(
                headingStyle = defaultHeadingStyle(colors)
            )
        )
    }
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Crossfade(viewState) { viewState ->
            when (viewState) {
                is ViewState.Error -> {
                    ListErrorView(stringResource(id = R.string.Markdown_Error_NotFound)) {
                        onRetryClick()
                    }
                }

                ViewState.Loading -> {
                    Loading()
                }

                ViewState.Success -> {
                    markdownBlocks?.let {
                        Column(
                            if (scrollableContent) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        ) {
                            ProvideToastUriHandler(onOpenUri = onUrlClick) {
                                RichText(
                                    style = richTextStyle,
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    BasicMarkdown(markdownBlocks, CustomAstBlockNodeComposer)
                                }
                            }
                            if (addFooter) {
                                MarkdownFooter()
                            }
                        }
                    }

                }

                null -> {}
            }
        }
    }
}

val CustomAstBlockNodeComposer = object : AstBlockNodeComposer {
    override fun predicate(astBlockNodeType: AstBlockNodeType): Boolean {
        return astBlockNodeType is AstHeading
    }

    @Composable
    override fun RichTextScope.Compose(
        astNode: AstNode,
        visitChildren: @Composable (AstNode) -> Unit
    ) {
        when (astNode.type) {
            is AstHeading -> {
                when ((astNode.type as AstHeading).level) {
                    1 -> {
                        Column {
                            Heading(level = (astNode.type as AstHeading).level) {
                                visitChildren(astNode)
                            }
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .height(1.dp)
                                    .background(Grey50)
                            )
                        }
                    }

                    2 -> {
                        Column(Modifier.padding(top = 12.dp)) {
                            Heading(level = (astNode.type as AstHeading).level) {
                                visitChildren(astNode)
                            }
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .height(1.dp)
                                    .background(Grey50)
                            )
                        }
                    }

                    3 -> {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Heading(level = (astNode.type as AstHeading).level) {
                                visitChildren(astNode)
                            }
                        }
                    }
                    else -> return
                }
            }

            else -> return
        }
    }
}

@Composable
fun ProvideToastUriHandler(onOpenUri: ((String) -> Unit), content: @Composable () -> Unit) {
    val uriHandler = remember {
        object : UriHandler {
            override fun openUri(uri: String) {
                onOpenUri(uri)
            }
        }
    }

    CompositionLocalProvider(LocalUriHandler provides uriHandler, content)
}
