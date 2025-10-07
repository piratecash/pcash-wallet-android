package cash.p.terminal.modules.eip20revoke

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.rememberViewModelFromGraph
import cash.p.terminal.ui_compose.BaseComposeFragment

class Eip20RevokeTransactionSettingsFragment : BaseComposeFragment() {
    @Composable
    override fun GetContent(navController: NavController) {
        Eip20RevokeTransactionSettingsScreen(navController)
    }
}

@Composable
fun Eip20RevokeTransactionSettingsScreen(navController: NavController) {
    val viewModel = rememberViewModelFromGraph<Eip20RevokeConfirmViewModel>(
        navController,
        R.id.eip20RevokeConfirmFragment
    ) ?: return

    val sendTransactionService = viewModel.sendTransactionService

    sendTransactionService.GetSettingsContent(navController)
}
