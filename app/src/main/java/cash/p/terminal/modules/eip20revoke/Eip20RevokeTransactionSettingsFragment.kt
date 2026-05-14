package cash.p.terminal.modules.eip20revoke

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.rememberViewModelFromGraph
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator

class Eip20RevokeTransactionSettingsFragment : BaseComposeFragment() {
    @Composable
    override fun GetContent(navController: NavController) {
        withInput<Eip20RevokeConfirmFragment.Input>(navController) { input ->
            Eip20RevokeTransactionSettingsScreen(navController, input)
        }
    }
}

@Composable
fun Eip20RevokeTransactionSettingsScreen(
    navController: NavController,
    input: Eip20RevokeConfirmFragment.Input
) {
    val viewModel = rememberViewModelFromGraph<Eip20RevokeConfirmViewModel>(
        navController,
        R.id.eip20RevokeConfirmFragment,
        Eip20RevokeConfirmViewModel.Factory(input.token, input.spenderAddress, input.allowance)
    ) ?: return

    val uiState = viewModel.uiState
    val sendTransactionService = viewModel.sendTransactionService
    if (sendTransactionService == null) {
        if (!uiState.preparing) {
            LaunchedEffect(navController) {
                navController.popBackStackSafely()
            }
        }
        Eip20RevokeTransactionSettingsLoading()
        return
    }

    sendTransactionService.GetSettingsContent(navController)
}

@Composable
private fun Eip20RevokeTransactionSettingsLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        HSCircularProgressIndicator(progress = 0.15f, size = 32.dp)
    }
}
