package cash.p.terminal.ui_compose.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun HsSettingCell(
    @StringRes title: Int,
    @DrawableRes icon: Int? = null,
    iconTint: Color? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val primaryColor = if (enabled) ComposeAppTheme.colors.leah else ComposeAppTheme.colors.grey50
    val secondaryColor = if (enabled) ComposeAppTheme.colors.grey else ComposeAppTheme.colors.grey50

    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick,
        enabled = enabled,
    ) {
        icon?.let {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = if (enabled) iconTint ?: secondaryColor else secondaryColor,
            )
        }
        Text(
            text = stringResource(title),
            color = primaryColor,
            style = ComposeAppTheme.typography.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (icon != null) 16.dp else 0.dp, end = 16.dp),
        )
        Spacer(Modifier.weight(1f))

        if (value != null) {
            Text(
                text = value,
                color = secondaryColor,
                style = ComposeAppTheme.typography.subhead1,
                maxLines = 1,
                modifier = Modifier.padding(
                    horizontal = if (onClick != null) 8.dp else 0.dp
                ),
            )
        }

        if (onClick != null) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = secondaryColor,
            )
        }
    }
}
