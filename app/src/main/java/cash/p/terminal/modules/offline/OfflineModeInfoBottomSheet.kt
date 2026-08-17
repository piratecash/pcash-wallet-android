package cash.p.terminal.modules.offline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch

private data class OfflineChecklistItem(val textRes: Int, val positive: Boolean)

private val offlineChecklistItems = listOf(
    OfflineChecklistItem(R.string.offline_mode_info_price, positive = true),
    OfflineChecklistItem(R.string.offline_mode_info_history, positive = true),
    OfflineChecklistItem(R.string.offline_mode_info_no_new_tx, positive = false),
    OfflineChecklistItem(R.string.offline_mode_info_no_send, positive = false),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineModeInfoBottomSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.ic_info_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.grey),
            title = stringResource(R.string.offline_mode_info_title),
            onCloseClick = close,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                subhead2_leah(text = stringResource(R.string.offline_mode_info_description))
                VSpacer(12.dp)
                offlineChecklistItems.forEach { item ->
                    OfflineChecklistRow(item)
                    VSpacer(12.dp)
                }
            }
            VSpacer(8.dp)
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = stringResource(R.string.Button_GotIt),
                onClick = close,
            )
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun OfflineChecklistRow(item: OfflineChecklistItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = if (item.positive) R.drawable.ic_checkmark_20 else R.drawable.ic_close_24),
            contentDescription = null,
            tint = if (item.positive) ComposeAppTheme.colors.remus else ComposeAppTheme.colors.lucian,
            modifier = Modifier.size(20.dp),
        )
        HSpacer(12.dp)
        body_leah(text = stringResource(item.textRes))
    }
}

@Preview
@Composable
private fun OfflineModeInfoBottomSheetPreview() {
    ComposeAppTheme {
        OfflineModeInfoBottomSheet(onDismiss = {})
    }
}
