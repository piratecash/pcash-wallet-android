package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.AnnotatedResourceString
import cash.p.terminal.ui_compose.entities.TermItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun TermsList(
    terms: List<TermItem>,
    onItemClicked: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(12.dp))

        CellUniversalLawrenceSection(
            items = terms,
            showFrame = true
        ) { item ->
            RowUniversal(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Top,
                onClick = { onItemClicked(item.id) }
            ) {
                HsCheckbox(
                    checked = item.checked,
                    enabled = true,
                    modifier = Modifier.padding(top = 8.dp),
                    onCheckedChange = { checked ->
                        onItemClicked(item.id)
                    },
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    val annotatedTitle = remember(item.title) {
                        AnnotatedResourceString.htmlToAnnotatedString(item.title)
                    }
                    subhead2_leah(
                        text = annotatedTitle
                    )
                    item.description?.let { description ->
                        val annotatedDescription = remember(description) {
                            AnnotatedResourceString.htmlToAnnotatedString(description)
                        }
                        subhead2_grey(
                            text = annotatedDescription,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(60.dp))
    }
}

@Preview
@Composable
fun TermsListPreview() {
    val terms = listOf(
        TermItem(
            0,
            "First term description goes here.",
            "Additional details about the first term.",
            false
        ),
        TermItem(
            1,
            "Second term description goes here.",
            "Additional details about the second term.",
            true
        ),
        TermItem(
            2,
            "Third term description goes here.",
            null,
            false
        )
    )
    ComposeAppTheme {
        TermsList(
            terms = terms,
            onItemClicked = {}
        )
    }
}