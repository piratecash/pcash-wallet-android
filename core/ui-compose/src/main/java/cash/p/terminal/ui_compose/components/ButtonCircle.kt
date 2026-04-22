package cash.p.terminal.ui_compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun ButtonPrimaryCircle(
    @DrawableRes icon: Int = R.drawable.ic_arrow_down_left_24,
    contentDescription: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconTint: Color? = null,
    background: Color? = null,
) {
    val shape = CircleShape
    val resolvedBackground = if (enabled) {
        background ?: ComposeAppTheme.colors.leah
    } else {
        ComposeAppTheme.colors.steel20
    }
    val resolvedTint = if (enabled) {
        iconTint ?: ComposeAppTheme.colors.claude
    } else {
        ComposeAppTheme.colors.grey50
    }

    HsIconButton(
        onClick = { onClick() },
        modifier = Modifier
            .size(50.dp)
            .clip(shape)
            .background(resolvedBackground),
        enabled = enabled,
        rippleColor = ComposeAppTheme.colors.claude
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = contentDescription,
            tint = resolvedTint
        )
    }
}

@Composable
fun ButtonSecondaryCircle(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int = R.drawable.ic_arrow_down_20,
    contentDescription: String? = null,
    tint: Color = ComposeAppTheme.colors.leah,
    background: Color = ComposeAppTheme.colors.steel20,
    onClick: () -> Unit,
) {
    HsIconButton(
        onClick = onClick,
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(background),
        enabled = enabled,
        rippleColor = tint
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = contentDescription,
            tint = tint
        )
    }
}
