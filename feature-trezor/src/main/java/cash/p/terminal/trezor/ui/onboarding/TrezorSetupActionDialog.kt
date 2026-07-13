package cash.p.terminal.trezor.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.trezor.R
import io.horizontalsystems.core.ui.dialogs.ConfirmationDialogBottomSheet

/**
 * Shared bottom-sheet for Trezor onboarding prompts: a title, a description, one action button and a
 * cancel. Keeps the [ConfirmationDialogBottomSheet] wiring in one place for the onboarding dialogs.
 */
@Composable
fun TrezorSetupActionDialog(
    title: String,
    description: String,
    actionButtonTitle: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialogBottomSheet(
        title = title,
        icon = null,
        warningTitle = null,
        warningText = description,
        actionButtonTitle = actionButtonTitle,
        transparentButtonTitle = stringResource(R.string.Alert_Cancel),
        onCloseClick = onDismiss,
        onActionButtonClick = onAction,
        onTransparentButtonClick = onDismiss
    )
}
