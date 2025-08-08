package cash.p.terminal.modules.premium

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.markdown.MarkdownFragment
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.BaseComposeFragment
import org.koin.compose.viewmodel.koinViewModel

class AboutPremiumFragment : BaseComposeFragment() {
    @Composable
    override fun GetContent(navController: NavController) {
        val viewModel: AboutPremiumViewModel = koinViewModel()

        AboutPremiumScreen(
            uiState = viewModel.uiState,
            onRetryClick = viewModel::retry,
            onCloseClick = navController::popBackStack,
            onUrlClick = { url ->
                navController.slideFromRight(
                    R.id.markdownFragment, MarkdownFragment.Input(url)
                )
            }
        )
    }
}
