package cash.p.terminal.modules.send.evm.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.rememberViewModelFromGraph
import cash.p.terminal.modules.send.evm.confirmation.SendEvmConfirmationViewModel
import cash.p.terminal.ui_compose.BaseComposeFragment

class SendEvmSettingsFragment : BaseComposeFragment() {
    @Composable
    override fun GetContent(navController: NavController) {
        SendEvmSettingsScreen(navController)
    }
}

@Composable
fun SendEvmSettingsScreen(navController: NavController) {
    val viewModel = rememberViewModelFromGraph<SendEvmConfirmationViewModel>(
        navController,
        R.id.sendEvmConfirmationFragment
    ) ?: return

    val sendTransactionService = viewModel.sendTransactionService

    sendTransactionService.GetSettingsContent(navController)
}
