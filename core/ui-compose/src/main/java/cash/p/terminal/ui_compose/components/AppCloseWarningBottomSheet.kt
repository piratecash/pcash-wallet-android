package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCloseWarningBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.ic_attention_24),
            title = stringResource(R.string.Alert_TitleWarning),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            onCloseClick = onDismiss,
        ) {
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                text = stringResource(R.string.Appearance_Warning_CloseApplication),
            )
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp),
                title = stringResource(R.string.Button_Change),
                onClick = onConfirm,
            )
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                title = stringResource(R.string.Button_Cancel),
                onClick = onDismiss,
            )
            VSpacer(20.dp)
        }
    }
}
