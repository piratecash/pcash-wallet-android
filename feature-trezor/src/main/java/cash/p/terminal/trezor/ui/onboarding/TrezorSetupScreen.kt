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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.strings.R
import cash.p.terminal.trezor.ui.TrezorSetupUiState
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun TrezorSetupScreen(
    uiState: TrezorSetupUiState,
    onConnect: () -> Unit,
    onInstallSuite: () -> Unit,
    onDismissInstall: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.tyler)) {
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
                onClick = onConnect
            )
        }
    }

    if (uiState.showInstallPrompt) {
        InstallSuiteDialog(
            onInstall = onInstallSuite,
            onDismiss = onDismissInstall
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
            onInstallSuite = {},
            onDismissInstall = {},
            onBack = {}
        )
    }
}
