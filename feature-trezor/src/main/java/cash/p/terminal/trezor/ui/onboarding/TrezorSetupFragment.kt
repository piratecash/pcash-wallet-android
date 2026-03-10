package cash.p.terminal.trezor.ui.onboarding

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import cash.p.terminal.navigation.setNavigationResultX
import cash.p.terminal.trezor.ui.TrezorSideEffect
import cash.p.terminal.trezor.ui.TrezorWalletViewModel
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.getInput
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class TrezorSetupFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val input = navController.getInput<Input>()
        if (input == null) {
            navController.navigateUp()
            return
        }

        val viewModel = koinViewModel<TrezorWalletViewModel>(
            parameters = { parametersOf(input.accountName) }
        )
        val context = LocalContext.current

        LaunchedEffect(viewModel.uiState.success) {
            if (viewModel.uiState.success) {
                navController.setNavigationResultX(Result(success = true))
                navController.navigateUp()
            }
        }

        LaunchedEffect(Unit) {
            viewModel.sideEffects.collect { effect ->
                when (effect) {
                    is TrezorSideEffect.OpenIntent -> context.startActivity(effect.intent)
                }
            }
        }

        TrezorSetupScreen(
            uiState = viewModel.uiState,
            onConnect = viewModel::connectTrezor,
            onInstallSuite = viewModel::openPlayStore,
            onDismissInstall = viewModel::dismissInstallPrompt,
            onBack = navController::navigateUp
        )
    }

    @Parcelize
    data class Input(val accountName: String) : Parcelable

    @Parcelize
    data class Result(val success: Boolean) : Parcelable
}
