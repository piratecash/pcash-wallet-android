package cash.p.terminal.modules.paycore

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction

class PayCoreVerificationAction(
    override val inProgress: Boolean = false
) : ISwapProviderAction {

    @Composable
    override fun getTitle(): String = stringResource(R.string.paycore_verify_phone)

    @Composable
    override fun getTitleInProgress(): String = stringResource(R.string.paycore_verifying)

    override fun execute(navController: NavController, onActionCompleted: () -> Unit) {
        // Navigation is handled via onOpenVerification in SwapFragment
    }
}
