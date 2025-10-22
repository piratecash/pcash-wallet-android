package cash.p.terminal.modules.settings.advancedsecurity

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.premiumAction
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsScreen
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsViewModel
import cash.p.terminal.ui_compose.BaseComposeFragment
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class AdvancedSecurityFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        AdvancedSecurityNavHost(navController)
    }
}

@Composable
private fun AdvancedSecurityNavHost(fragmentNavController: NavController) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AdvancedSecurityRoutes.ADVANCED_SECURITY_PAGE
    ) {
        composable(AdvancedSecurityRoutes.ADVANCED_SECURITY_PAGE) {
            AdvancedSecurityScreen(
                onCreateHiddenWalletClick = {
                    fragmentNavController.premiumAction {
                        navController.navigate(AdvancedSecurityRoutes.HIDDEN_WALLET_TERM_SPAGE)
                    }
                },
                onClose = fragmentNavController::navigateUp
            )
        }
        composablePage(AdvancedSecurityRoutes.HIDDEN_WALLET_TERM_SPAGE) {
            val context = LocalContext.current
            val termTitles = context.resources.getStringArray(R.array.AdvancedSecurity_Terms_Checkboxes)

            val viewModel: HiddenWalletTermsViewModel = koinViewModel {
                parametersOf(termTitles)
            }
            val uiState = viewModel.uiState

            HiddenWalletTermsScreen(
                uiState = uiState,
                onCheckboxToggle = viewModel::toggleCheckbox,
                onAgreeClick = {
                    viewModel.onAgreeClick()
                    navController.popBackStack()
                },
                onNavigateBack = navController::navigateUp
            )
        }
    }
}
