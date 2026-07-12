package cash.p.terminal.modules.blockchainstatus

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.BaseComposeFragment
import io.horizontalsystems.core.entities.Blockchain
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class BlockchainStatusFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        withInput<Blockchain>(navController) { blockchain ->
            val provider = rememberBlockchainStatusProvider(blockchain)
            val viewModel = koinViewModel<BlockchainStatusViewModel> {
                parametersOf(provider)
            }
            BlockchainStatusScreen(
                viewModel = viewModel,
                onBack = navController::navigateUpSafely
            )
        }
    }
}
