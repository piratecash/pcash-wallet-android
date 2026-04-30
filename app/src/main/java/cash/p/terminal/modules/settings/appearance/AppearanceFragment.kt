package cash.p.terminal.modules.settings.appearance

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.ensurePinSet
import cash.p.terminal.core.fullRestart
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.premiumAction
import cash.p.terminal.modules.calculator.domain.CalculatorModeService
import cash.p.terminal.ui_compose.BaseComposeFragment
import io.horizontalsystems.core.IPinComponent

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
            val context = LocalContext.current
            AppIconScreen(
                appIconOptions = viewModel.uiState.appIconOptions,
                onAppIconSelect = { icon ->
                    val activity = context as? Activity
                    when {
                        icon == AppIcon.Calculator -> {
                            val pinExistedBefore = getKoinInstance<IPinComponent>().isPinSet
                            fragmentNavController.premiumAction {
                                fragmentNavController.ensurePinSet(R.string.PinSet_Title) {
                                    getKoinInstance<CalculatorModeService>().enable(
                                        pinExistedBefore = pinExistedBefore
                                    )
                                    activity?.fullRestart()
                                }
                            }
                        }

                        getKoinInstance<ILocalStorage>().isCalculatorModeEnabled -> {
                            fragmentNavController.authorizedAction {
                                getKoinInstance<CalculatorModeService>().disableAndSwitchTo(icon)
                                activity?.fullRestart()
                            }
                        }

                        else -> {
                            viewModel.onEnterAppIcon(icon)
                            activity?.fullRestart()
                        }
                    }
                },
                onClose = { navController.popBackStack() }
            )
        }
    }
}
