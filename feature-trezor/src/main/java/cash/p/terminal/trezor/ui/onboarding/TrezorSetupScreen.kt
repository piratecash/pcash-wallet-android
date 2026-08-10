package cash.p.terminal.trezor.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.trezor.R
import cash.p.terminal.trezor.ui.TrezorMoneroRestoreMode
import cash.p.terminal.trezor.ui.TrezorMoneroRestoreUiState
import cash.p.terminal.trezor.ui.TrezorSetupUiState
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InputField
import cash.p.terminal.ui_compose.components.RestoreHeightMode
import cash.p.terminal.ui_compose.components.RestoreHeightScreen
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun TrezorSetupScreen(
    uiState: TrezorSetupUiState,
    onConnect: () -> Unit,
    onOpenSetupGuide: () -> Unit,
    onDismissSetupPrompt: () -> Unit,
    onSelectMoneroRestoreMode: (TrezorMoneroRestoreMode) -> Unit,
    onMoneroRestoreHeightChange: (String) -> Unit,
    onSubmitMoneroRestore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.moneroRestore.visible) {
        RestoreHeightScreen(
            mode = when (uiState.moneroRestore.mode) {
                TrezorMoneroRestoreMode.NewWallet -> RestoreHeightMode.NewWallet
                TrezorMoneroRestoreMode.ExistingWallet -> RestoreHeightMode.ExistingWallet
                null -> null
            },
            onModeSelect = { mode ->
                onSelectMoneroRestoreMode(
                    when (mode) {
                        RestoreHeightMode.NewWallet -> TrezorMoneroRestoreMode.NewWallet
                        RestoreHeightMode.ExistingWallet ->
                            TrezorMoneroRestoreMode.ExistingWallet
                    },
                )
            },
            doneEnabled = uiState.moneroRestore.canSubmit,
            onDoneClick = onSubmitMoneroRestore,
            topBar = {
                AppBar(
                    title = stringResource(R.string.restore_monero),
                    navigationIcon = {
                        HsBackButton(onClick = onBack)
                    },
                )
            },
            modifier = modifier,
            existingWalletContent = {
                Spacer(Modifier.height(16.dp))
                HeaderText(stringResource(R.string.restoreheight_title))
                InputField(
                    value = uiState.moneroRestore.heightOrDate,
                    onValueChange = onMoneroRestoreHeightChange,
                    placeholderText = stringResource(R.string.restoreheight_hint),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    keyboardType = KeyboardType.Ascii,
                )
                if (uiState.moneroRestore.invalidHeight) {
                    Spacer(Modifier.height(8.dp))
                    caption_lucian(
                        text = stringResource(R.string.invalid_height_format),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            },
            additionalContent = {
                if (uiState.moneroRestore.automaticHeightUnavailable) {
                    Spacer(Modifier.height(8.dp))
                    caption_lucian(
                        text = stringResource(R.string.monero_restore_error),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            },
        )
        return
    }

    Column(modifier = modifier.background(color = ComposeAppTheme.colors.tyler)) {
        AppBar(
            title = stringResource(R.string.trezor_wallet),
            navigationIcon = {
                HsBackButton(onClick = onBack)
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            subhead2_grey(
                text = stringResource(R.string.trezor_setup_description),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(32.dp))
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.connect_trezor),
                onClick = onConnect,
                enabled = !uiState.loading,
                loadingIndicator = uiState.loading,
            )
        }
    }

    if (uiState.showNotInitialized) {
        TrezorNotInitializedDialog(
            onOpenSetupGuide = onOpenSetupGuide,
            onDismiss = onDismissSetupPrompt
        )
    }
}

@Preview
@Composable
private fun TrezorSetupScreenPreview() {
    ComposeAppTheme {
        TrezorSetupScreen(
            uiState = TrezorSetupUiState(),
            onConnect = {},
            onOpenSetupGuide = {},
            onDismissSetupPrompt = {},
            onSelectMoneroRestoreMode = {},
            onMoneroRestoreHeightChange = {},
            onSubmitMoneroRestore = {},
            onBack = {}
        )
    }
}

@Preview(name = "Monero restore")
@Composable
private fun TrezorSetupMoneroRestorePreview() {
    ComposeAppTheme {
        TrezorSetupScreen(
            uiState = TrezorSetupUiState(
                moneroRestore = TrezorMoneroRestoreUiState(
                    visible = true,
                    mode = TrezorMoneroRestoreMode.ExistingWallet,
                    heightOrDate = "2024-01-01",
                ),
            ),
            onConnect = {},
            onOpenSetupGuide = {},
            onDismissSetupPrompt = {},
            onSelectMoneroRestoreMode = {},
            onMoneroRestoreHeightChange = {},
            onSubmitMoneroRestore = {},
            onBack = {},
        )
    }
}
