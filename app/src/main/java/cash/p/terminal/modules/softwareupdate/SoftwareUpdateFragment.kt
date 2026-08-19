package cash.p.terminal.modules.softwareupdate

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.releasenotes.ReleaseNotesScreen
import cash.p.terminal.modules.softwareupdate.changelog.VersionChangelogViewModel
import cash.p.terminal.modules.softwareupdate.domain.InstallSourceProvider
import cash.p.terminal.modules.softwareupdate.history.VersionHistoryScreen
import cash.p.terminal.modules.softwareupdate.history.VersionHistoryViewModel
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.network.github.domain.entity.AppRelease
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.ScreenWithoutConnectionPanel
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class SoftwareUpdateFragment : BaseComposeFragment() {

    private val installSourceProvider: InstallSourceProvider by inject()

    @Composable
    override fun GetContent(navController: NavController) {
        SoftwareUpdateNavHost(navController, onUpdateNow = ::onUpdateNow)
    }

    private fun onUpdateNow(release: AppRelease) {
        val destinationUrl = installSourceProvider.updateDestinationUrl(release)
        openUrl(requireContext(), destinationUrl)
    }
}

private sealed class SoftwareUpdateRoute {
    @Serializable
    data object Update : SoftwareUpdateRoute()

    @Serializable
    data object History : SoftwareUpdateRoute()

    @Serializable
    data class Changelog(val minor: String, val isActiveBranch: Boolean) : SoftwareUpdateRoute()
}

@Composable
private fun SoftwareUpdateNavHost(
    fragmentNavController: NavController,
    onUpdateNow: (AppRelease) -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = SoftwareUpdateRoute.Update,
    ) {
        composable<SoftwareUpdateRoute.Update> {
            val viewModel: SoftwareUpdateViewModel = koinViewModel()
            SoftwareUpdateScreen(
                uiState = viewModel.uiState,
                onBack = fragmentNavController::popBackStackSafely,
                onIntervalChange = viewModel::onIntervalChange,
                onRetry = viewModel::retry,
                onHistoryClick = { navController.navigate(SoftwareUpdateRoute.History) },
                onDetailsClick = { minor ->
                    navController.navigate(SoftwareUpdateRoute.Changelog(minor, isActiveBranch = true))
                },
                onUpdateNowClick = onUpdateNow,
            )
        }
        composablePage<SoftwareUpdateRoute.History> {
            val viewModel: VersionHistoryViewModel = koinViewModel()
            VersionHistoryScreen(
                uiState = viewModel.uiState,
                onBack = navController::popBackStackSafely,
                onRetry = viewModel::retry,
                onVersionClick = { minor, isActiveBranch ->
                    navController.navigate(SoftwareUpdateRoute.Changelog(minor, isActiveBranch))
                },
            )
        }
        composablePage<SoftwareUpdateRoute.Changelog> { backStackEntry ->
            val context = LocalContext.current
            val route = backStackEntry.toRoute<SoftwareUpdateRoute.Changelog>()
            val viewModel: VersionChangelogViewModel =
                koinViewModel(parameters = { parametersOf(route.minor, route.isActiveBranch) })
            ScreenWithoutConnectionPanel {
                ReleaseNotesScreen(
                    closeablePopup = false,
                    uiState = viewModel.uiState,
                    onCloseClick = navController::popBackStackSafely,
                    onRetryClick = viewModel::retry,
                    onWhatsNewShown = {},
                    onShowChangelogToggle = viewModel::onShowChangelogToggle,
                    onUrlClick = { url -> LinkHelper.openLinkInAppBrowser(context, url) },
                )
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    tryOrNull { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
