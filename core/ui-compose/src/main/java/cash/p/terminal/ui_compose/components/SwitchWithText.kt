package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun SwitchWithText(
    text: String,
    checkEnabled: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?
) {
    CellUniversal {
        body_leah(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        HsSwitch(
            checked = checkEnabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
@Preview(showBackground = true)
private fun SwitchWithTextPreview() {
    ComposeAppTheme {
        SwitchWithText(
            text = LoremIpsum(20).values.joinToString(),
            checkEnabled = true,
            onCheckedChange = {}
        )
    }
}