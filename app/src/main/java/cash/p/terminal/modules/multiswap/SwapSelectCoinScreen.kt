package cash.p.terminal.modules.multiswap

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.wallet.Token

@Composable
fun SwapSelectCoinScreen(
    navController: NavController,
    token: Token?,
    title: String?,
    onSelect: (Token) -> Unit
) {
    val viewModel = viewModel<SwapSelectCoinViewModel>(
        factory = SwapSelectCoinViewModel.Factory(token)
    )
    val uiState = viewModel.uiState

    SelectSwapCoinDialogScreen(
        title = title ?: "",
        coinBalanceItems = uiState.coinBalanceItems,
        onSearchTextChanged = viewModel::setQuery,
        onClose = navController::popBackStack
    ) {
        onSelect(it.token)
    }
}
