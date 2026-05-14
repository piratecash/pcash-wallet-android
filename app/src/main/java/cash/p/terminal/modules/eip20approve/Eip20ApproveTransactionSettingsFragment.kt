package cash.p.terminal.modules.eip20approve

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.rememberViewModelFromGraph
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.ui_compose.BaseComposeFragment

class Eip20ApproveTransactionSettingsFragment : BaseComposeFragment() {
    @Composable
    override fun GetContent(navController: NavController) {
        withInput<Eip20ApproveFragment.Input>(navController) { input ->
            Eip20ApproveTransactionSettingsScreen(navController, input)
        }
    }
}

@Composable
fun Eip20ApproveTransactionSettingsScreen(
    navController: NavController,
    input: Eip20ApproveFragment.Input
) {
    val viewModel =
        rememberViewModelFromGraph<Eip20ApproveViewModel>(
            navController,
            R.id.eip20ApproveFragment,
            Eip20ApproveViewModel.Factory(input)
        )
            ?: return

    val sendTransactionService = viewModel.sendTransactionService
    if (sendTransactionService == null) {
        LaunchedEffect(navController) {
            navController.popBackStackSafely()
        }
        return
    }

    sendTransactionService.GetSettingsContent(navController)
}
