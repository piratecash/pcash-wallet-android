package cash.p.terminal.modules.fee

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun FeeCell(
    title: String,
    info: String,
    value: FeeItem?,
    viewState: ViewState?,
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    RowUniversal(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.clickable(
                onClick = { showInfoDialog = true },
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            subhead2_grey(text = title)

            Image(
                modifier = Modifier.padding(horizontal = 8.dp),
                painter = painterResource(id = R.drawable.ic_info_20),
                contentDescription = ""
            )
        }

        Spacer(Modifier.weight(1f))

        Box(contentAlignment = Alignment.CenterEnd) {
            if (viewState == ViewState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = ComposeAppTheme.colors.grey,
                    strokeWidth = 1.5.dp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val color = if (viewState is ViewState.Error) {
                    ComposeAppTheme.colors.lucian
                } else if (value == null) {
                    ComposeAppTheme.colors.grey50
                } else {
                    ComposeAppTheme.colors.leah
                }
                Text(
                    modifier = Modifier.alpha(if (viewState == ViewState.Loading) 0f else 1f),
                    text = value?.primary ?: stringResource(id = R.string.NotAvailable),
                    maxLines = 1,
                    style = ComposeAppTheme.typography.subhead1,
                    color = color
                )
                Text(
                    modifier = Modifier.alpha(if (viewState == ViewState.Loading) 0f else 1f),
                    text = value?.secondary ?: stringResource(id = R.string.NotAvailable),
                    maxLines = 1,
                    style = ComposeAppTheme.typography.caption,
                    color = ComposeAppTheme.colors.grey
                )
            }
        }
    }

    if (showInfoDialog) {
        InfoBottomSheet(
            title = title,
            text = info,
            onDismiss = { showInfoDialog = false }
        )
    }
}
