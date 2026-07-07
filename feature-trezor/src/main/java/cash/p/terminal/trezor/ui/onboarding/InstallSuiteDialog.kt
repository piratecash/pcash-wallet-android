package cash.p.terminal.trezor.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.trezor.R

@Composable
fun InstallSuiteDialog(
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    TrezorSetupActionDialog(
        title = stringResource(R.string.trezor_suite_required_title),
        description = stringResource(R.string.trezor_suite_required_description),
        actionButtonTitle = stringResource(R.string.install_from_play_store),
        onAction = onInstall,
        onDismiss = onDismiss
    )
}
