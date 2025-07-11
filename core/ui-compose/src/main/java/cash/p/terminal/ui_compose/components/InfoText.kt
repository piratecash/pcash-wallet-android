package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun InfoText(
    text: String,
    paddingStart: Dp = 32.dp,
    paddingEnd: Dp = 32.dp,
    paddingTop: Dp = 12.dp,
    paddingBottom: Dp = 12.dp,
) {
    subhead2_grey(
        modifier = Modifier.padding(start = paddingStart, top = paddingTop, end = paddingEnd, bottom = paddingBottom),
        text = text
    )
}

@Composable
fun InfoTextBody(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        text = text,
        style = ComposeAppTheme.typography.body,
        color = ComposeAppTheme.colors.bran
    )
}
