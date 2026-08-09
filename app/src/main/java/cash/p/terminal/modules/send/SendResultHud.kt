package cash.p.terminal.modules.send

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.CustomSnackbar
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.SnackbarDuration

/**
 * Progress HUD for a send/swap, driven by [sendResult].
 *
 * The snackbar attaches to the activity content view, so it outlives the screen that showed it.
 * Driving it from a composable body therefore spawned a new snackbar on every recomposition and
 * stranded the indefinite "sending" one on screen for good once the screen left composition (for
 * example when the offline blocker replaced it) — only an app restart cleared it. Showing runs in
 * an effect keyed on [sendResult], and [DisposableEffect] ties that HUD to this composable.
 */
@Composable
fun SendResultHud(
    sendResult: SendResult?,
    @StringRes sendingTextRes: Int,
    @StringRes successTextRes: Int,
) {
    val view = LocalView.current

    // Only the indefinite "sending" HUD is tracked for dismissal: the terminal ones expire on
    // their own and are meant to stay visible while the screen auto-closes.
    var sendingSnackbar by remember { mutableStateOf<CustomSnackbar?>(null) }

    // getDescription()/getString() are @Composable, so the failure text is resolved here rather
    // than inside the effect.
    val failureText = (sendResult as? SendResult.Failed)?.let { failed ->
        failed.caution.getDescription() ?: failed.caution.getString()
    }

    LaunchedEffect(sendResult) {
        sendingSnackbar?.dismiss()
        sendingSnackbar = null

        when (sendResult) {
            SendResult.Sending -> sendingSnackbar = HudHelper.showInProcessMessage(
                view,
                sendingTextRes,
                SnackbarDuration.INDEFINITE,
            )

            is SendResult.Sent -> HudHelper.showSuccessMessage(
                view,
                successTextRes,
                SnackbarDuration.MEDIUM,
            )

            is SendResult.SentButQueued -> HudHelper.showWarningMessage(
                view,
                R.string.send_success_queued,
                SnackbarDuration.LONG,
            )

            is SendResult.Failed -> HudHelper.showErrorMessage(view, failureText.orEmpty())

            null -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose { sendingSnackbar?.dismiss() }
    }
}
