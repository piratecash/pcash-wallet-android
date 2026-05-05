package cash.p.terminal.modules.multiswap.providersettings

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.ui_compose.BaseComposeFragment
import org.koin.compose.viewmodel.koinViewModel

class SwapProvidersSettingsFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val viewModel: SwapProvidersSettingsViewModel = koinViewModel()
        SwapProvidersSettingsScreen(
            uiState = viewModel.uiState,
            onToggle = viewModel::setProviderEnabled,
            onClose = navController::navigateUp,
        )
    }
}
