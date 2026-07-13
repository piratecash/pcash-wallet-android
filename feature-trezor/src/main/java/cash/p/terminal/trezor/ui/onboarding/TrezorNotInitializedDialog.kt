package cash.p.terminal.trezor.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.trezor.R

@Composable
fun TrezorNotInitializedDialog(
    onOpenSetupGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    TrezorSetupActionDialog(
        title = stringResource(R.string.trezor_not_initialized_title),
        description = stringResource(R.string.trezor_not_initialized_description),
        actionButtonTitle = stringResource(R.string.trezor_not_initialized_cta),
        onAction = onOpenSetupGuide,
        onDismiss = onDismiss
    )
}
