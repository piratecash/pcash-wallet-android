package cash.p.terminal.modules.walletconnect.request

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui_compose.entities.DataState

@Composable
fun WcRequestPreScreen(navController: NavController) {
    val viewModelPre = viewModel<WCRequestPreViewModel>(
        factory = WCRequestPreViewModel.Factory()
    )

    val uiState = viewModelPre.uiState

    if (uiState is DataState.Success) {
        WcRequestScreen(navController, uiState.data.sessionRequest, uiState.data.wcAction)
    } else if (uiState is DataState.Error) {
        ListErrorView(uiState.error.message ?: "Error") { }
    }
}
