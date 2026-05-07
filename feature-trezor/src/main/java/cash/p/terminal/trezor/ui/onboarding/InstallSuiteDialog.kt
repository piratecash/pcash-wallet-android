package cash.p.terminal.trezor.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.strings.R
import io.horizontalsystems.core.ui.dialogs.ConfirmationDialogBottomSheet

@Composable
fun InstallSuiteDialog(
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialogBottomSheet(
        title = stringResource(R.string.trezor_suite_required_title),
        icon = null,
        warningTitle = null,
        warningText = stringResource(R.string.trezor_suite_required_description),
        actionButtonTitle = stringResource(R.string.install_from_play_store),
        transparentButtonTitle = stringResource(R.string.Alert_Cancel),
        onCloseClick = onDismiss,
        onActionButtonClick = onInstall,
        onTransparentButtonClick = onDismiss
    )
}
