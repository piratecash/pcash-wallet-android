package cash.p.terminal.modules.eip20approve

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.rememberViewModelFromGraph
import cash.p.terminal.ui_compose.BaseComposeFragment

class Eip20ApproveTransactionSettingsFragment : BaseComposeFragment() {
    @Composable
    override fun GetContent(navController: NavController) {
        Eip20ApproveTransactionSettingsScreen(navController)
    }
}

@Composable
fun Eip20ApproveTransactionSettingsScreen(navController: NavController) {
    val viewModel =
        rememberViewModelFromGraph<Eip20ApproveViewModel>(navController, R.id.eip20ApproveFragment)
            ?: return

    val sendTransactionService = viewModel.sendTransactionService

    sendTransactionService.GetSettingsContent(navController)
}
