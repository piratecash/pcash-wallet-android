package cash.p.terminal.modules.balance.token

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.modules.pin.ConfirmPinFragment
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.transactions.TransactionsModule
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.findNavController
import cash.p.terminal.wallet.isPirateCash

class TokenBalanceFragment : BaseComposeFragment() {
    private var viewModel: TokenBalanceViewModel? = null

    @Composable
    override fun GetContent(navController: NavController) {
        val args: TokenBalanceFragmentArgs by navArgs()
        val wallet = args.wallet

        val viewModel by viewModels<TokenBalanceViewModel> { TokenBalanceModule.Factory(wallet) }
        this.viewModel = viewModel
        val transactionsViewModel by navGraphViewModels<TransactionsViewModel>(R.id.mainFragment) { TransactionsModule.Factory() }

        TokenBalanceScreen(
            viewModel = viewModel,
            transactionsViewModel = transactionsViewModel,
            navController = navController,
            onStackingClicked = {
                navController.slideFromRight(
                    resId = R.id.stacking,
                    input = if (wallet.isPirateCash()) StackingType.PCASH else StackingType.COSANTA
                )
            },
            onShowAllTransactionsClicked = {
                navController.authorizedAction(
                    ConfirmPinFragment.InputConfirm(
                        descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                        pinType = PinType.TRANSACTIONS_HIDE
                    )
                ) {
                    viewModel.showAllTransactions(true)
                }
            },
            onClickSubtitle = {
                viewModel.toggleTotalType()
                HudHelper.vibrate(requireContext())
            },
            onRefresh = viewModel::refresh,
            refreshing = viewModel.refreshing
        )
    }

    override fun onStart() {
        super.onStart()
        viewModel?.startStatusChecker()
    }

    override fun onPause() {
        viewModel?.stopStatusChecker()
        super.onPause()


        if (!skipHideTransactions()) {
            viewModel?.showAllTransactions(false)
        }
    }

    private fun skipHideTransactions(): Boolean {
        val previousBackStackEntry = findNavController().previousBackStackEntry?.destination?.id
        // No need to hide transactions when user goes to next screen
        // But hides when they go to background on back
        return previousBackStackEntry == R.id.tokenBalanceFragment
    }
}
