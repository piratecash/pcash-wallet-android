package cash.p.terminal.modules.premium.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.premium.domain.premiumAction
import cash.p.terminal.strings.R
import cash.p.terminal.ui.compose.components.HsSwitch
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.cell.CellUniversal
import io.horizontalsystems.chartview.cell.SectionUniversalLawrence
import org.koin.androidx.viewmodel.ext.android.viewModel

class PremiumSettingsFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val viewModel by viewModel<PremiumSettingsViewModel>()

        PremiumSettingsScreen(
            uiState = viewModel.uiState,
            onCheckAddressContractClick = {
                navController.premiumAction {
                    viewModel.setAddressContractChecking(it)
                }
            },
            onClose = navController::popBackStack
        )
    }

}

@Composable
internal fun PremiumSettingsScreen(
    uiState: PremiumSettingsUiState,
    onCheckAddressContractClick: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.premium_settings),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            SectionUniversalLawrence {
                CellUniversal {
                    body_leah(text = stringResource(R.string.settings_smart_contract_check))
                    HFillSpacer(minWidth = 8.dp)
                    HsSwitch(
                        checked = uiState.checkEnabled,
                        onCheckedChange = onCheckAddressContractClick
                    )
                }
            }
            InfoText(
                text = stringResource(R.string.settings_smart_contract_check_description),
            )
        }
    }
}

@Preview
@Composable
private fun PremiumSettingsScreenPreview() {
    ComposeAppTheme {
        PremiumSettingsScreen(
            uiState = PremiumSettingsUiState(
                checkEnabled = true
            ),
            onCheckAddressContractClick = {},
            onClose = {}
        )
    }
}