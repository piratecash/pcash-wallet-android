package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.material3.RichText

@Composable
fun DescriptionMarkdown(
    text: String,
) {
    val parser = remember { CommonmarkAstNodeParser() }
    val astNode = remember(text) { parser.parse(text) }

    RichText(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        BasicMarkdown(astNode)
    }
}
