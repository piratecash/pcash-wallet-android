package cash.p.terminal.feature.miniapp.ui.connect.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import cash.p.terminal.feature.miniapp.domain.model.CoinType
import cash.p.terminal.feature.miniapp.domain.model.SpecialProposalData
import cash.p.terminal.feature.miniapp.ui.components.JwtExpiredStepContent
import cash.p.terminal.feature.miniapp.ui.components.MiniAppStepScaffold
import cash.p.terminal.feature.miniapp.ui.components.StepDescriptionStyle
import cash.p.terminal.feature.miniapp.ui.components.StepIndicatorState
import cash.p.terminal.feature.miniapp.ui.components.rememberStepIndicatorState
import cash.p.terminal.strings.R
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.TabItem
import cash.p.terminal.ui_compose.components.Tabs
import cash.p.terminal.ui_compose.components.TitleAndTwoValuesCell
import cash.p.terminal.ui_compose.components.TitleAndValueCell
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
internal fun SpecialProposalStepScreen(
    uiState: SpecialProposalUiState,
    stepIndicatorState: StepIndicatorState?,
    onTabSelected: (CoinType) -> Unit,
    onBuyClick: () -> Unit,
    onConnectClick: () -> Unit,
    onRetryClick: () -> Unit,
    onOpenMiniAppClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Show JWT expired state
    if (uiState.isJwtExpired) {
        JwtExpiredStepContent(
            stepTitle = stringResource(R.string.connect_mini_app_step_5),
            stepIndicatorState = stepIndicatorState,
            onOpenMiniAppClick = onOpenMiniAppClick,
            modifier = modifier
        )
        return
    }

    // Show error state with retry
    if (uiState.error != null) {
        MiniAppStepScaffold(
            stepTitle = stringResource(R.string.connect_mini_app_step_5),
            stepDescription = uiState.error,
            descriptionStyle = StepDescriptionStyle.Red,
            stepIndicatorState = stepIndicatorState,
            modifier = modifier,
            bottomContent = {
                ButtonPrimaryYellow(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.Button_Retry),
                    onClick = onRetryClick
                )
            },
            content = {}
        )
        return
    }

    MiniAppStepScaffold(
        isLoading = uiState.isLoading,
        stepTitle = stringResource(R.string.connect_mini_app_step_5),
        stepDescription = stringResource(R.string.connect_mini_app_step_5_description),
        descriptionStyle = StepDescriptionStyle.Yellow,
        stepIndicatorState = stepIndicatorState,
        modifier = modifier,
        bottomContent = {
            // Buy button
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = if (uiState.selectedTab == CoinType.PIRATE) {
                    stringResource(R.string.connect_mini_app_buy_pirate)
                } else {
                    stringResource(R.string.connect_mini_app_buy_cosanta)
                },
                onClick = onBuyClick
            )

            VSpacer(16.dp)

            // Connect button
            ButtonPrimaryDefault(
                modifier = Modifier.fillMaxWidth(),
                title = if (uiState.isPremium) {
                    stringResource(R.string.connect_mini_app_connect)
                } else {
                    stringResource(R.string.connect_mini_app_connect_without_premium)
                },
                onClick = onConnectClick
            )
        },
        content = {
            val coroutineScope = rememberCoroutineScope()

            // Guaranteed bonus text
            uiState.data?.let { data ->
                GuaranteedBonusText(
                    bonus = data.guaranteedBonus,
                    bonusFiat = data.guaranteedBonusFiat,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 28.dp
                    )
                )
            }

            VSpacer(16.dp)

            // Tabs
            val tabs = listOf(
                TabItem(
                    title = stringResource(R.string.connect_mini_app_tab_pirate_cash),
                    selected = uiState.selectedTab == CoinType.PIRATE,
                    item = CoinType.PIRATE
                ),
                TabItem(
                    title = stringResource(R.string.connect_mini_app_tab_cosanta),
                    selected = uiState.selectedTab == CoinType.COSA,
                    item = CoinType.COSA
                )
            )

            val pagerState = rememberPagerState(
                initialPage = tabs.indexOfFirst { it.selected }
            ) { tabs.size }

            Tabs(tabs = tabs, onClick = { coinType ->
                onTabSelected(coinType)
                coroutineScope.launch {
                    pagerState.scrollToPage(if (coinType == CoinType.PIRATE) 0 else 1)
                }
            })

            VSpacer(16.dp)

            // Stats card with HorizontalPager
            uiState.data?.let { data ->
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false
                ) { page ->
                    val selectedTab = if (page == 0) CoinType.PIRATE else CoinType.COSA
                    StatsCard(
                        data = data,
                        selectedTab = selectedTab
                    )
                }
            }
        }
    )
}

@Composable
private fun GuaranteedBonusText(
    bonus: Int,
    bonusFiat: String,
    modifier: Modifier = Modifier
) {
    val prefix = stringResource(R.string.connect_mini_app_bonus_text_prefix)
    val suffix = stringResource(R.string.connect_mini_app_bonus_text_suffix)
    val bonusText = "+$bonus PIRATE ($bonusFiat)"

    val annotatedText = buildAnnotatedString {
        withStyle(SpanStyle(color = ComposeAppTheme.colors.leah)) {
            append(prefix)
        }
        append("\n")
        withStyle(SpanStyle(color = ComposeAppTheme.colors.jacob)) {
            append(bonusText)
        }
        append(" ")
        withStyle(SpanStyle(color = ComposeAppTheme.colors.leah)) {
            append(suffix)
        }
    }

    Text(
        text = annotatedText,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = ComposeAppTheme.typography.body
    )
}

@Composable
private fun StatsCard(
    data: SpecialProposalData,
    selectedTab: CoinType
) {
    val isPirate = selectedTab == CoinType.PIRATE
    val notEnough = if (isPirate) data.pirateNotEnough else data.cosaNotEnough
    val notEnoughFiat = if (isPirate) data.pirateNotEnoughFiat else data.cosaNotEnoughFiat
    val roi = if (isPirate) data.pirateRoi else data.cosaRoi
    val monthlyIncome = if (isPirate) data.pirateMonthlyIncome else data.cosaMonthlyIncome
    val monthlyIncomeFiat = if (isPirate) data.pirateMonthlyIncomeFiat else data.cosaMonthlyIncomeFiat

    CellUniversalLawrenceSection(
        listOf(
            {
                TitleAndTwoValuesCell(
                    stringResource(R.string.connect_mini_app_not_enough),
                    notEnough,
                    notEnoughFiat
                )
            },
            { TitleAndValueCell(stringResource(R.string.connect_mini_app_roi), roi) },
            {
                TitleAndTwoValuesCell(
                    stringResource(R.string.connect_mini_app_monthly_income),
                    monthlyIncome,
                    monthlyIncomeFiat
                )
            }
        )
    )
}

/**
 * UI State for SpecialProposalStepScreen
 */
data class SpecialProposalUiState(
    val data: SpecialProposalData? = null,
    val selectedTab: CoinType = CoinType.PIRATE,
    val isLoading: Boolean = false,
    val isPremium: Boolean = false,
    val error: String? = null,
    val isJwtExpired: Boolean = false
)

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpecialProposalStepScreenPreview() {
    ComposeAppTheme {
        SpecialProposalStepScreen(
            uiState = SpecialProposalUiState(
                data = SpecialProposalData(
                    guaranteedBonus = 1000,
                    guaranteedBonusFiat = "$20",
                    pirateBalance = BigDecimal.ZERO,
                    pirateNotEnough = "10,000 PIRATE",
                    pirateNotEnoughFiat = "$234",
                    pirateRoi = "7.5%",
                    pirateMonthlyIncome = "+56.873 PIRATE",
                    pirateMonthlyIncomeFiat = "+$1.1888",
                    cosaBalance = BigDecimal.ZERO,
                    cosaNotEnough = "100 COSA",
                    cosaNotEnoughFiat = "$143",
                    cosaRoi = "23.5%",
                    cosaMonthlyIncome = "+2.1255 COSA",
                    cosaMonthlyIncomeFiat = "+$2.9237",
                    cheaperOption = CoinType.COSA,
                    hasPiratePremium = false,
                    hasCosaPremium = false,
                    hasPremium = false
                ),
                selectedTab = CoinType.PIRATE,
                isPremium = false
            ),
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5),
            onTabSelected = {},
            onBuyClick = {},
            onConnectClick = {},
            onRetryClick = {},
            onOpenMiniAppClick = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpecialProposalStepScreenCosaTabPreview() {
    ComposeAppTheme {
        SpecialProposalStepScreen(
            uiState = SpecialProposalUiState(
                data = SpecialProposalData(
                    guaranteedBonus = 1000,
                    guaranteedBonusFiat = "$20",
                    pirateBalance = BigDecimal.ZERO,
                    pirateNotEnough = "10,000 PIRATE",
                    pirateNotEnoughFiat = "$234",
                    pirateRoi = "7.5%",
                    pirateMonthlyIncome = "+56.873 PIRATE",
                    pirateMonthlyIncomeFiat = "+$1.1888",
                    cosaBalance = BigDecimal.ZERO,
                    cosaNotEnough = "100 COSA",
                    cosaNotEnoughFiat = "$143",
                    cosaRoi = "23.5%",
                    cosaMonthlyIncome = "+2.1255 COSA",
                    cosaMonthlyIncomeFiat = "+$2.9237",
                    cheaperOption = CoinType.COSA,
                    hasPiratePremium = false,
                    hasCosaPremium = false,
                    hasPremium = false
                ),
                selectedTab = CoinType.COSA,
                isPremium = false
            ),
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5),
            onTabSelected = {},
            onBuyClick = {},
            onConnectClick = {},
            onRetryClick = {},
            onOpenMiniAppClick = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpecialProposalStepScreenPremiumPreview() {
    ComposeAppTheme {
        SpecialProposalStepScreen(
            uiState = SpecialProposalUiState(
                data = SpecialProposalData(
                    guaranteedBonus = 1000,
                    guaranteedBonusFiat = "$20",
                    pirateBalance = BigDecimal(15000),
                    pirateNotEnough = "0 PIRATE",
                    pirateNotEnoughFiat = "$0",
                    pirateRoi = "7.5%",
                    pirateMonthlyIncome = "+112.5 PIRATE",
                    pirateMonthlyIncomeFiat = "+$2.34",
                    cosaBalance = BigDecimal(150),
                    cosaNotEnough = "0 COSA",
                    cosaNotEnoughFiat = "$0",
                    cosaRoi = "23.5%",
                    cosaMonthlyIncome = "+3.5 COSA",
                    cosaMonthlyIncomeFiat = "+$5.00",
                    cheaperOption = CoinType.PIRATE,
                    hasPiratePremium = true,
                    hasCosaPremium = true,
                    hasPremium = true
                ),
                selectedTab = CoinType.PIRATE,
                isPremium = true
            ),
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5),
            onTabSelected = {},
            onBuyClick = {},
            onConnectClick = {},
            onRetryClick = {},
            onOpenMiniAppClick = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpecialProposalStepScreenLoadingPreview() {
    ComposeAppTheme {
        SpecialProposalStepScreen(
            uiState = SpecialProposalUiState(isLoading = true),
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5),
            onTabSelected = {},
            onBuyClick = {},
            onConnectClick = {},
            onRetryClick = {},
            onOpenMiniAppClick = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpecialProposalStepScreenErrorPreview() {
    ComposeAppTheme {
        SpecialProposalStepScreen(
            uiState = SpecialProposalUiState(error = "Failed to load data"),
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5),
            onTabSelected = {},
            onBuyClick = {},
            onConnectClick = {},
            onRetryClick = {},
            onOpenMiniAppClick = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpecialProposalStepScreenJwtExpiredPreview() {
    ComposeAppTheme {
        SpecialProposalStepScreen(
            uiState = SpecialProposalUiState(isJwtExpired = true),
            stepIndicatorState = rememberStepIndicatorState(initialStep = 5),
            onTabSelected = {},
            onBuyClick = {},
            onConnectClick = {},
            onRetryClick = {},
            onOpenMiniAppClick = {}
        )
    }
}
