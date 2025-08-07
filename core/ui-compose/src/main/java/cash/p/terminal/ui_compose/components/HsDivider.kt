package cash.p.terminal.ui_compose.components

import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun HsDivider(
    modifier: Modifier = Modifier,
    color: Color = ComposeAppTheme.colors.blade
) {
    Divider(
        thickness = 0.5.dp,
        color = color,
        modifier = modifier
    )
}
