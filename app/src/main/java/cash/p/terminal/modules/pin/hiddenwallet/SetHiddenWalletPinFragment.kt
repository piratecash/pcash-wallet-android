package cash.p.terminal.modules.pin.hiddenwallet

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.main.MainActivity
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.ui.PinSet
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.HudHelper

class SetHiddenWalletPinFragment : BaseComposeFragment(screenshotEnabled = false) {
    @Composable
    override fun GetContent(navController: NavController) {
        val view = LocalView.current

        PinSet(
            title = stringResource(id = R.string.create_hidden_wallet_pin),
            description = stringResource(id = R.string.create_hidden_wallet_pin_description),
            pinType = PinType.HIDDEN_WALLET,
            dismissWithSuccess = {
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Created)
                (requireActivity() as MainActivity).openCreateNewWallet()
            },
            onBackPress = {
                navController.popBackStack(R.id.mainFragment, inclusive = false)
            },
        )
    }
}
