package cash.p.terminal.modules.send.offline

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.HudHelper
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel

class OfflineBroadcastFragment : BaseComposeFragment() {

    private val args: OfflineBroadcastFragmentArgs by navArgs()

    @Composable
    override fun GetContent(navController: NavController) {
        val viewModel: OfflineBroadcastViewModel = koinViewModel()
        val view = LocalView.current
        val initialInput = args.input?.initialInput

        LaunchedEffect(Unit) {
            initialInput?.let(viewModel::prefillAndAdvance)
        }

        // Unrecoverable inputs (bad payload, no broadcasting wallet) surface a toast and close the
        // screen instead of stranding the user on an empty confirmation.
        viewModel.uiState.dismissError?.let { message ->
            LaunchedEffect(message) {
                HudHelper.showErrorMessage(view, message)
                viewModel.onDismissErrorShown()
                navController.navigateUp()
            }
        }

        // Recoverable errors (e.g. enabling the network failed) keep the user on the screen so they
        // can retry, so they only surface a toast without navigating away.
        viewModel.uiState.errorMessage?.let { message ->
            LaunchedEffect(message) {
                HudHelper.showErrorMessage(view, message)
                viewModel.onErrorMessageShown()
            }
        }

        OfflineBroadcastScreen(
            uiState = viewModel.uiState,
            onPickNetwork = viewModel::onPickNetwork,
            onSelectBlockchain = viewModel::onSelectBlockchain,
            onPrimaryAction = viewModel::onPrimaryAction,
            onRetry = viewModel::onRetry,
            onBack = { if (!viewModel.onBack()) navController.navigateUpSafely() },
            onClose = navController::navigateUpSafely,
        )
    }

    @Parcelize
    data class Input(val initialInput: String? = null) : Parcelable
}
