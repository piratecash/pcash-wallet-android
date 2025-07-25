package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R

@Composable
fun HsCheckbox(
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange?.invoke(!checked) }
            )
            .size(24.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.checkbox_inactive_24),
            contentDescription = null
        )
        if (checked) {
            Image(
                painter = painterResource(id = R.drawable.checkbox_active_24),
                contentDescription = null
            )
        }
    }
}
