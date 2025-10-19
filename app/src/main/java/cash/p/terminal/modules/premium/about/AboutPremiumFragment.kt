package cash.p.terminal.modules.premium.about

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import cash.p.terminal.modules.markdown.openMarkdownOrWeblink
import cash.p.terminal.navigation.setNavigationResultX
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.getInput
import kotlinx.parcelize.Parcelize
import org.koin.androidx.viewmodel.ext.android.viewModel

class AboutPremiumFragment : BaseComposeFragment() {
    private val viewModel: AboutPremiumViewModel by viewModel()

    @Composable
    override fun GetContent(navController: NavController) {
        val closeOnPremium = navController.getInput<CloseOnPremiumInput>()

        LaunchedEffect(viewModel.uiState.hasPremium) {
            if (viewModel.uiState.hasPremium && closeOnPremium != null) {
                navController.setNavigationResultX(Result())
                navController.popBackStack()
            }
        }
        AboutPremiumScreen(
            uiState = viewModel.uiState,
            uiEvents = viewModel.uiEvents,
            onRetryClick = viewModel::retry,
            onCloseClick = navController::popBackStack,
            onUrlClick = { url ->
                navController.openMarkdownOrWeblink(url)
            },
            onTryForFreeClick = viewModel::activateDemoPremium
        )
    }

    @Parcelize
    class CloseOnPremiumInput : Parcelable

    @Parcelize
    class Result : Parcelable
}
