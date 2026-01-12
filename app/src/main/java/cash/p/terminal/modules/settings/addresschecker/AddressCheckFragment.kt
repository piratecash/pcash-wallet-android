package cash.p.terminal.modules.settings.addresschecker

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import cash.p.terminal.R
import cash.p.terminal.modules.settings.addresschecker.ui.UnifiedAddressCheckScreen
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.ui_compose.BaseComposeFragment
import kotlinx.parcelize.Parcelize

class AddressCheckFragment : BaseComposeFragment() {

    private val args: AddressCheckFragmentArgs by navArgs()

    @Composable
    override fun GetContent(navController: NavController) {
        UnifiedAddressCheckScreen(
            initialAddress = args.input?.initialAddress,
            onClose = navController::popBackStack,
            onPremiumClick = {
                navController.slideFromBottom(R.id.aboutPremiumFragment)
            }
        )
    }

    @Parcelize
    data class Input(val initialAddress: String? = null) : Parcelable
}
