package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

enum class RestoreHeightMode {
    NewWallet,
    ExistingWallet,
}

val RestoreHeightMode?.isNewWallet: Boolean
    get() = this == RestoreHeightMode.NewWallet

val RestoreHeightMode?.isSelected: Boolean
    get() = this != null

fun Boolean.toRestoreHeightMode(): RestoreHeightMode =
    if (this) RestoreHeightMode.NewWallet else RestoreHeightMode.ExistingWallet

@Composable
fun RestoreHeightScreen(
    mode: RestoreHeightMode?,
    onModeSelect: (RestoreHeightMode) -> Unit,
    doneEnabled: Boolean,
    onDoneClick: () -> Unit,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    contentWindowInsets: WindowInsets = WindowInsets(0),
    existingWalletContent: @Composable ColumnScope.() -> Unit = {},
    additionalContent: @Composable ColumnScope.() -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = topBar,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .windowInsetsPadding(contentWindowInsets)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(12.dp))
                RestoreHeightOptions(
                    mode = mode,
                    onModeSelect = onModeSelect,
                )
                if (mode == RestoreHeightMode.ExistingWallet) {
                    existingWalletContent()
                }
                additionalContent()
            }
            ButtonsGroupWithShade {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    title = stringResource(if (loading) R.string.Alert_Loading else R.string.Button_Done),
                    onClick = onDoneClick,
                    enabled = doneEnabled && !loading,
                )
            }
        }
    }
}

@Composable
private fun RestoreHeightOptions(
    mode: RestoreHeightMode?,
    onModeSelect: (RestoreHeightMode) -> Unit,
) {
    CellMultilineLawrenceSection(
        listOf(
            {
                RestoreHeightModeCell(
                    title = stringResource(R.string.Restore_ZCash_NewWallet),
                    subtitle = stringResource(
                        R.string.Restore_ZCash_NewWallet_Description,
                    ),
                    checked = mode == RestoreHeightMode.NewWallet,
                    onClick = { onModeSelect(RestoreHeightMode.NewWallet) },
                )
            },
            {
                RestoreHeightModeCell(
                    title = stringResource(R.string.Restore_ZCash_OldWallet),
                    subtitle = stringResource(
                        R.string.Restore_ZCash_OldWallet_Description,
                    ),
                    checked = mode == RestoreHeightMode.ExistingWallet,
                    onClick = { onModeSelect(RestoreHeightMode.ExistingWallet) },
                )
            },
        ),
    )
}

@Composable
private fun RestoreHeightModeCell(
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            body_leah(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            subhead2_grey(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.ic_checkmark_20),
                    tint = ComposeAppTheme.colors.jacob,
                    contentDescription = null,
                )
            }
        }
    }
}

@Preview(name = "Choose wallet type")
@Composable
private fun RestoreHeightScreenPreview() {
    ComposeAppTheme {
        RestoreHeightScreen(
            mode = null,
            onModeSelect = {},
            doneEnabled = false,
            onDoneClick = {},
            topBar = {
                AppBar(title = stringResource(R.string.restore_monero))
            },
        )
    }
}

@Preview(name = "Existing wallet")
@Composable
private fun RestoreHeightExistingWalletPreview() {
    ComposeAppTheme {
        RestoreHeightScreen(
            mode = RestoreHeightMode.ExistingWallet,
            onModeSelect = {},
            doneEnabled = true,
            onDoneClick = {},
            topBar = {
                AppBar(title = stringResource(R.string.restore_monero))
            },
            existingWalletContent = {
                Spacer(Modifier.height(16.dp))
                HeaderText(stringResource(R.string.restoreheight_title))
                InputField(
                    value = "2024-01-01",
                    onValueChange = {},
                    placeholderText = stringResource(R.string.restoreheight_hint),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    keyboardType = KeyboardType.Ascii,
                )
            },
        )
    }
}
