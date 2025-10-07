package cash.p.terminal.modules.walletconnect.request

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.rememberViewModelFromGraph
import cash.p.terminal.modules.walletconnect.request.sendtransaction.WCSendEthereumTransactionRequestViewModel
import cash.p.terminal.ui_compose.BaseComposeFragment

class WCEvmTransactionSettingsFragment : BaseComposeFragment() {
    @Composable
    override fun GetContent(navController: NavController) {
        WCEvmTransactionSettingsScreen(navController)
    }
}

@Composable
fun WCEvmTransactionSettingsScreen(navController: NavController) {
    val viewModel = rememberViewModelFromGraph<WCSendEthereumTransactionRequestViewModel>(
        navController,
        R.id.wcRequestFragment
    ) ?: return

    val sendTransactionService = viewModel.sendTransactionService

    sendTransactionService.GetSettingsContent(navController)
}
