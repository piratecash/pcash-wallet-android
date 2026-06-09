package cash.p.terminal.modules.paycore

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction

class PayCoreSelectBankAction(
    override val inProgress: Boolean = false,
) : ISwapProviderAction {

    @Composable
    override fun getTitle(): String = stringResource(R.string.paycore_select_bank)

    @Composable
    override fun getTitleInProgress(): String = stringResource(R.string.paycore_select_bank)

    override fun execute(navController: NavController, onActionCompleted: () -> Unit) = Unit
}
