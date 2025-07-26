package cash.p.terminal.modules.settings.addresschecker

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.modules.settings.addresschecker.ui.UnifiedAddressCheckScreen
import cash.p.terminal.ui_compose.BaseComposeFragment

class AddressCheckFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        UnifiedAddressCheckScreen(
            onClose = { navController.popBackStack() }
        )
    }
}
