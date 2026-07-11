package cash.p.terminal.modules.balance.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigation.NavController
import cash.p.terminal.modules.balance.BalanceAccountsViewModel
import cash.p.terminal.modules.balance.BalanceScreenState
import cash.p.terminal.modules.transactions.TransactionItem

@Composable
fun BalanceScreen(
    navController: NavController,
    paddingValues: PaddingValues,
    onOpenTransactionInfo: (TransactionItem) -> Unit,
) {
    val viewModel = koinViewModel<BalanceAccountsViewModel>()

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