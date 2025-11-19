package cash.p.terminal.modules.multiswap.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.modules.multiswap.SwapConfirmViewModel

@Composable
fun SwapTransactionSettingsScreen(navController: NavController) {
    val previousBackStackEntry = navController.previousBackStackEntry ?: return
    val viewModel = viewModel<SwapConfirmViewModel>(
        viewModelStoreOwner = previousBackStackEntry
    )

    val sendTransactionService = viewModel.sendTransactionService

    sendTransactionService.GetSettingsContent(navController)
}
