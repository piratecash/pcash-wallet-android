package cash.p.terminal.modules.settings.appearance

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.core.composablePage
import cash.p.terminal.ui_compose.BaseComposeFragment

private object AppearanceRoutes {
    const val MAIN = "main"
    const val APP_ICON = "app_icon"
}

class AppearanceFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        AppearanceNavHost(navController)
    }
}

@Composable
private fun AppearanceNavHost(fragmentNavController: NavController) {
    val navController = rememberNavController()
    val viewModel = viewModel<AppearanceViewModel>(factory = AppearanceModule.Factory())

    NavHost(
        navController = navController,
        startDestination = AppearanceRoutes.MAIN
    ) {
        composable(AppearanceRoutes.MAIN) {
            AppearanceScreen(
                uiState = viewModel.uiState,
                onThemeSelect = viewModel::onEnterTheme,
                onLaunchScreenSelect = viewModel::onEnterLaunchPage,
                onBalanceViewTypeSelect = viewModel::onEnterBalanceViewType,
                onPriceChangeIntervalSelect = viewModel::onSetPriceChangeInterval,
                onMarketTabsHiddenChange = viewModel::onSetMarketTabsHidden,
                onBalanceTabButtonsHiddenChange = viewModel::onSetBalanceTabButtonsHidden,
                onAppIconClick = { navController.navigate(AppearanceRoutes.APP_ICON) },
                onClose = { fragmentNavController.popBackStack() }
            )
        }
        composablePage(AppearanceRoutes.APP_ICON) {
            AppIconScreen(
                appIconOptions = viewModel.uiState.appIconOptions,
                onAppIconSelect = viewModel::onEnterAppIcon,
                onClose = { navController.popBackStack() }
            )
        }
    }
}
