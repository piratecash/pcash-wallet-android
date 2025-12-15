package cash.p.terminal.modules.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.composablePopup
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.modules.releasenotes.ReleaseNotesScreen
import cash.p.terminal.modules.releasenotes.ReleaseNotesViewModel
import cash.p.terminal.modules.settings.appcache.AppCacheScreen
import cash.p.terminal.modules.settings.appstatus.AppStatusScreen
import cash.p.terminal.modules.settings.main.HsSettingCell
import cash.p.terminal.modules.settings.privacy.PrivacyScreen
import cash.p.terminal.modules.settings.privacy.PrivacyViewModel
import cash.p.terminal.modules.settings.terms.TermsScreen
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.ScreenWithoutConnectionPanel
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_jacob
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.compose.viewmodel.koinViewModel

class AboutFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        AboutNavHost(navController)
    }

}

private const val AboutPage = "about"
private const val ReleaseNotesPage = "release_notes"
private const val AppStatusPage = "app_status"
private const val AppCachePage = "app_cache"
private const val PrivacyPage = "privacy"
private const val TermsPage = "terms"

@Composable
private fun AboutNavHost(fragmentNavController: NavController) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AboutPage,
    ) {
        composable(AboutPage) {
            AboutScreen(
                navController,
                { fragmentNavController.slideFromBottom(R.id.contactOptionsDialog) },
                { fragmentNavController.popBackStack() }
            )
        }
        composablePage(ReleaseNotesPage) {
            ScreenWithoutConnectionPanel {
                val viewModel = koinViewModel<ReleaseNotesViewModel>()
                ReleaseNotesScreen(
                    closeablePopup = false,
                    uiState = viewModel.uiState,
                    onCloseClick = navController::popBackStack,
                    onRetryClick = { viewModel.retry() },
                    onWhatsNewShown = { viewModel.whatsNewShown() },
                    onShowChangelogToggle = viewModel::setShowChangeLogAfterUpdate
                )
            }
        }
        composablePage(AppStatusPage) { AppStatusScreen(navController) }
        composablePage(AppCachePage) { AppCacheScreen(navController) }
        composablePage(PrivacyPage) {
            val viewModel = koinViewModel<PrivacyViewModel>()
            PrivacyScreen(
                navController = navController,
                uiState = viewModel.uiState,
                toggleCrashData = viewModel::toggleCrashData
            )
        }
        composablePopup(TermsPage) { TermsScreen(navController) }
    }
}

@Composable
private fun AboutScreen(
    navController: NavController,
    showContactOptions: () -> Unit,
    onBackPress: () -> Unit,
    aboutViewModel: AboutViewModel = viewModel(factory = AboutModule.Factory()),
) {
    Surface(color = ComposeAppTheme.colors.tyler) {
        Column {
            AppBar(
                title = stringResource(R.string.SettingsAboutApp_Title),
                navigationIcon = {
                    HsBackButton(onClick = onBackPress)
                }
            )

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                SettingSections(aboutViewModel, navController)
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun SettingSections(
    viewModel: AboutViewModel,
    navController: NavController
) {

    val context = LocalContext.current
    val termsShowAlert = viewModel.termsShowAlert

    CellUniversalLawrenceSection(
        listOf {
            HsSettingCell(
                title = R.string.SettingsAboutApp_AppVersion,
                icon = R.drawable.ic_info_20,
                value = viewModel.appVersion,
                onClick = {
                    navController.navigate(ReleaseNotesPage)
                }
            )
        }
    )

    Spacer(Modifier.height(32.dp))

    CellUniversalLawrenceSection(
        listOf({
            HsSettingCell(
                R.string.Settings_AppStatus,
                R.drawable.ic_app_status,
                onClick = {
                    navController.navigate(AppStatusPage)
                }
            )
        }, {
            HsSettingCell(
                R.string.Settings_Terms,
                R.drawable.ic_terms_20,
                showAlert = termsShowAlert,
                onClick = {
                    navController.navigate(TermsPage)
                }
            )
        }, {
            HsSettingCell(
                R.string.Settings_Privacy,
                R.drawable.ic_user_20,
                onClick = {
                    navController.navigate(PrivacyPage)
                }
            )
        })
    )

    Spacer(Modifier.height(32.dp))

    CellUniversalLawrenceSection(
        listOf({
            HsSettingCell(
                R.string.SettingsAboutApp_Github,
                R.drawable.ic_github_20,
                onClick = {
                    LinkHelper.openLinkInAppBrowser(context, viewModel.githubLink)
                }
            )
        }, {
            HsSettingCell(
                R.string.SettingsAboutApp_Site,
                R.drawable.ic_globe,
                onClick = {
                    LinkHelper.openLinkInAppBrowser(context, viewModel.appWebPageLink)
                }
            )
        })
    )

    Spacer(Modifier.height(32.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        subhead1_jacob(text = stringResource(id = R.string.Settings_JoinUs).uppercase())
    }
    CellUniversalLawrenceSection(
        listOf({
            HsSettingCell(
                R.string.Settings_Telegram,
                R.drawable.ic_telegram_filled_24,
                onClick = {
                    LinkHelper.openLinkInAppBrowser(context, AppConfigProvider.appTelegramLink)
                }
            )
        }, {
            HsSettingCell(
                R.string.Settings_Twitter,
                R.drawable.ic_twitter_filled_24,
                onClick = {
                    LinkHelper.openLinkInAppBrowser(context, AppConfigProvider.appTwitterLink)
                }
            )
        })
    )
    InfoText(
        text = stringResource(R.string.Settings_JoinUs_Description),
    )

    VSpacer(32.dp)
}
