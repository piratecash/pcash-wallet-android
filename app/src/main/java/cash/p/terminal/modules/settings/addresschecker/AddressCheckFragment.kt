package cash.p.terminal.modules.settings.addresschecker

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.settings.addresschecker.ui.UnifiedAddressCheckScreen
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.navigation.slideFromBottom

class AddressCheckFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        UnifiedAddressCheckScreen(
            onClose = navController::popBackStack,
            onPremiumClick = {
                navController.slideFromBottom(R.id.aboutPremiumFragment)
            }
        )
    }
}
