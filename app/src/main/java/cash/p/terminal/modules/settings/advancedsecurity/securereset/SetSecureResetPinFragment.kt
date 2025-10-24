package cash.p.terminal.modules.settings.advancedsecurity.securereset

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.ui.PinSet
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.HudHelper

class SetSecureResetPinFragment : BaseComposeFragment(screenshotEnabled = false) {
    @Composable
    override fun GetContent(navController: NavController) {
        val view = LocalView.current

        PinSet(
            title = stringResource(R.string.SecureReset_Pin_Title),
            description = stringResource(R.string.SecureReset_Pin_Description),
            pinType = PinType.SECURE_RESET,
            dismissWithSuccess = {
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Created)
                navController.popBackStack(R.id.mainFragment, inclusive = false)
            },
            onBackPress = {
                navController.popBackStack(R.id.mainFragment, inclusive = false)
            }
        )
    }
}
