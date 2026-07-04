package cash.p.terminal.modules.balance.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.modules.balance.BalanceAccountsViewModel
import cash.p.terminal.modules.balance.BalanceModule
import cash.p.terminal.modules.balance.BalanceScreenState
import cash.p.terminal.modules.transactions.TransactionItem

@Composable
fun BalanceScreen(
    navController: NavController,
    paddingValues: PaddingValues,
    onOpenTransactionInfo: (TransactionItem) -> Unit,
) {
    val viewModel = viewModel<BalanceAccountsViewModel>(factory = BalanceModule.AccountsFactory())

    when (val tmpAccount = viewModel.balanceScreenState) {
        BalanceScreenState.NoAccount -> BalanceNoAccount(navController, paddingValues)
        is BalanceScreenState.HasAccount -> {
            BalanceForAccount(
                navController = navController,
                accountViewItem = tmpAccount.accountViewItem,
                paddingValuesParent = paddingValues,
                onOpenTransactionInfo = onOpenTransactionInfo,
            )
        }

        else -> {}
    }
}