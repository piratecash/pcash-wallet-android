package cash.p.terminal.modules.premium.about

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.markdown.MarkdownFragment
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.BaseComposeFragment
import org.koin.androidx.viewmodel.ext.android.viewModel

class AboutPremiumFragment : BaseComposeFragment() {
    private val viewModel: AboutPremiumViewModel by viewModel()

    @Composable
    override fun GetContent(navController: NavController) {
        AboutPremiumScreen(
            uiState = viewModel.uiState,
            onRetryClick = viewModel::retry,
            onCloseClick = navController::popBackStack,
            onUrlClick = { url ->
                navController.slideFromRight(
                    R.id.markdownFragment, MarkdownFragment.Input(url)
                )
            },
            onTryForFreeClick = viewModel::activateDemoPremium
        )
    }
}
