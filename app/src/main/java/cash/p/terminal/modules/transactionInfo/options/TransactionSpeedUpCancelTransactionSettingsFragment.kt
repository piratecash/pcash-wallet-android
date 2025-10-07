package cash.p.terminal.modules.transactionInfo.options

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.rememberViewModelFromGraph
import cash.p.terminal.ui_compose.BaseComposeFragment

class TransactionSpeedUpCancelTransactionSettingsFragment : BaseComposeFragment() {
    @Composable
    override fun GetContent(navController: NavController) {
        TransactionSpeedUpCancelTransactionSettingsScreen(navController)
    }
}

@Composable
fun TransactionSpeedUpCancelTransactionSettingsScreen(navController: NavController) {
    val viewModel = rememberViewModelFromGraph<TransactionSpeedUpCancelViewModel>(
        navController,
        R.id.transactionSpeedUpCancelFragment
    ) ?: return

    val sendTransactionService = viewModel.sendTransactionService

    sendTransactionService.GetSettingsContent(navController)
}
