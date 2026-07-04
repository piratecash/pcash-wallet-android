package cash.p.terminal.modules.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.settings.main.HsSettingCell
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class ContactUsFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        ContactUsScreen(
            onPcashSupport = {
                navController.slideFromBottom(
                    R.id.contactOptionsDialog,
                    ContactOptionsDialog.Input(
                        titleRes = R.string.settings_contact_us_pcash_support,
                        email = AppConfigProvider.reportEmail,
                        telegramUrl = Translator.getString(R.string.telegram_link)
                    )
                )
            },
            onPaycoreSupport = {
                navController.slideFromBottom(
                    R.id.contactOptionsDialog,
                    ContactOptionsDialog.Input(
                        titleRes = R.string.settings_contact_us_paycore_support,
                        email = AppConfigProvider.payCoreSupportEmail,
                        telegramUrl = AppConfigProvider.payCoreSupportUrl
                    )
                )
            },
            onBackPress = navController::navigateUpSafely
        )
    }
}

@Composable
private fun ContactUsScreen(
    onPcashSupport: () -> Unit,
    onPaycoreSupport: () -> Unit,
    onBackPress: () -> Unit
) {
    Surface(color = ComposeAppTheme.colors.tyler) {
        Column {
            AppBar(
                title = stringResource(R.string.SettingsContact_Title),
                navigationIcon = { HsBackButton(onClick = onBackPress) }
            )

            Spacer(Modifier.height(12.dp))

            CellUniversalLawrenceSection(
                listOf({
                    HsSettingCell(
                        title = R.string.settings_contact_us_pcash_support,
                        icon = R.drawable.ic_pcash_logo_24,
                        onClick = onPcashSupport
                    )
                }, {
                    HsSettingCell(
                        title = R.string.settings_contact_us_paycore_support,
                        icon = R.drawable.ic_paycore,
                        iconTint = Color.Unspecified,
                        onClick = onPaycoreSupport
                    )
                })
            )
        }
    }
}
