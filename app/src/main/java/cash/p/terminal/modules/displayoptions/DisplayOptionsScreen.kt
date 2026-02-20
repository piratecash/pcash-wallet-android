package cash.p.terminal.modules.displayoptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.modules.settings.main.HsSettingCell
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui.compose.components.AlertGroup
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun DisplayOptionsScreen(
    navController: NavController,
    uiState: DisplayOptionsUiState,
    onPricePeriodChanged: (DisplayPricePeriod) -> Unit,
    onPercentChangeToggled: (Boolean) -> Unit,
    onPriceChangeToggled: (Boolean) -> Unit,
    onRoundingAmountMainPageToggled: (Boolean) -> Unit,
) {
    var showPeriodSelector by remember { mutableStateOf(false) }

    Surface(color = ComposeAppTheme.colors.tyler) {
        Column {
            AppBar(
                title = stringResource(R.string.display_options),
                navigationIcon = {
                    HsBackButton(onClick = navController::popBackStack)
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                if (uiState.isCoinManagerEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CellUniversalLawrenceSection(
                        listOf {
                            HsSettingCell(
                                title = R.string.ManageCoins_title,
                                onClick = {
                                    navController.slideFromRight(R.id.manageWalletsFragment)
                                }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                PriceParametersSection(
                    pricePeriod = uiState.pricePeriod,
                    displayDiffOptionType = uiState.displayDiffOptionType,
                    isRoundingAmount = uiState.isRoundingAmountMainPage,
                    onPricePeriodClick = { showPeriodSelector = true },
                    onPercentChangeToggled = onPercentChangeToggled,
                    onPriceChangeToggled = onPriceChangeToggled,
                    onRoundingAmountToggled = onRoundingAmountMainPageToggled,
                )
            }
        }
    }

    // Period Selector Dialog
    if (showPeriodSelector) {
        AlertGroup(
            title = R.string.display_options_price_period,
            select = Select(uiState.pricePeriod, DisplayPricePeriod.entries),
            onSelect = { selected ->
                onPricePeriodChanged(selected)
                showPeriodSelector = false
            },
            onDismiss = { showPeriodSelector = false }
        )
    }
}

@Composable
fun PriceParametersSection(
    pricePeriod: DisplayPricePeriod,
    displayDiffOptionType: DisplayDiffOptionType,
    isRoundingAmount: Boolean,
    onPricePeriodClick: () -> Unit,
    onPercentChangeToggled: (Boolean) -> Unit,
    onPriceChangeToggled: (Boolean) -> Unit,
    onRoundingAmountToggled: (Boolean) -> Unit,
) {
    InfoText(
        text = stringResource(R.string.Market_FilterSection_PriceParameters).uppercase(),
        paddingBottom = 8.dp
    )

    CellUniversalLawrenceSection(
        listOf(
            {
                HsSettingCell(
                    title = R.string.display_options_price_period,
                    value = pricePeriod.shortForm.getString(),
                    onClick = onPricePeriodClick
                )
            },
            {
                SwitchWithText(
                    text = stringResource(R.string.display_options_percent_change),
                    checked = displayDiffOptionType.hasPercentChange,
                    onCheckedChange = onPercentChangeToggled
                )
            },
            {
                SwitchWithText(
                    text = stringResource(R.string.display_options_price_change),
                    checked = displayDiffOptionType.hasPriceChange,
                    onCheckedChange = onPriceChangeToggled
                )
            }
        )
    )

    CellUniversalLawrenceSection(
        listOf {
            SwitchWithText(
                text = stringResource(R.string.sum_rounding),
                checked = isRoundingAmount,
                onCheckedChange = onRoundingAmountToggled
            )
        },
        Modifier.padding(top = 32.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun DisplayOptionsScreenPreview() {
    ComposeAppTheme {
        DisplayOptionsScreen(
            navController = rememberNavController(),
            uiState = DisplayOptionsUiState(
                isCoinManagerEnabled = true,
                isRoundingAmountMainPage = true,
                pricePeriod = DisplayPricePeriod.ONE_DAY,
                displayDiffOptionType = DisplayDiffOptionType.BOTH
            ),
            onPricePeriodChanged = {},
            onPercentChangeToggled = {},
            onPriceChangeToggled = {},
            onRoundingAmountMainPageToggled = {}
        )
    }
}
