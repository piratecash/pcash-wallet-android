package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun ButtonsGroupWithShade(
    modifier: Modifier = Modifier,
    buttonsContent: @Composable (() -> Unit),
) {
    Column(
        modifier = modifier.offset(y = -(24.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(ComposeAppTheme.colors.transparent, ComposeAppTheme.colors.tyler),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(bottom = 8.dp),
        ) {
            buttonsContent()
        }
    }
}
