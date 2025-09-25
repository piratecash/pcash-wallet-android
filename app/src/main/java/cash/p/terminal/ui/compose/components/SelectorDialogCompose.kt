package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

data class SelectorItem<T>(
    val title: String,
    val selected: Boolean,
    val item: T,
    val subtitle: String? = null,
)

@Composable
fun <T> SelectorDialogCompose(
    title: String? = null,
    items: List<SelectorItem<T>>,
    onDismissRequest: () -> Unit,
    onSelectItem: (T) -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(ComposeAppTheme.colors.lawrence)
        ) {
            title?.let {
                Box(modifier = Modifier.height(40.dp)) {
                    subhead1_grey(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        text = title,
                        textAlign = TextAlign.Center
                    )
                }
                Divider(thickness = 1.dp, color = ComposeAppTheme.colors.steel10)
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier
                            .clickable(
                                enabled = !item.selected,
                                onClick = {
                                    onSelectItem.invoke(item.item)
                                    onDismissRequest.invoke()
                                }
                            )

                    ) {
                        if (index != 0 || title != null) {
                            Divider(thickness = 1.dp, color = ComposeAppTheme.colors.steel10)
                        }
                        val color = if (item.selected) {
                            ComposeAppTheme.colors.jacob
                        } else {
                            ComposeAppTheme.colors.leah
                        }
                        VSpacer(12.dp)
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            text = item.title,
                            style = ComposeAppTheme.typography.body,
                            color = color,
                            textAlign = TextAlign.Center
                        )
                        item.subtitle?.let { subtitle ->
                            VSpacer(1.dp)
                            subhead2_grey(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                text = subtitle,
                                textAlign = TextAlign.Center
                            )
                        }
                        VSpacer(12.dp)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectorDialogPreview() {
    val items = List(30) {
        SelectorItem("Item $it", it == 1, it)
    }
    ComposeAppTheme {
        SelectorDialogCompose(
            title = "Title",
            items = items,
            onDismissRequest = {},
            onSelectItem = {}
        )
    }
}
