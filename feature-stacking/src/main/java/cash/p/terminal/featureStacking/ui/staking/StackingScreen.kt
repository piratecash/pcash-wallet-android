package cash.p.terminal.featureStacking.ui.staking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.featureStacking.R
import cash.p.terminal.featureStacking.ui.calculatorScreen.CalculatorScreen
import cash.p.terminal.featureStacking.ui.calculatorScreen.CalculatorUIState
import cash.p.terminal.featureStacking.ui.cosantaCoinScreen.CosantaCoinChartViewModel
import cash.p.terminal.featureStacking.ui.cosantaCoinScreen.CosantaCoinViewModel
import cash.p.terminal.featureStacking.ui.pirateCoinScreen.PirateCoinChartViewModel
import cash.p.terminal.featureStacking.ui.pirateCoinScreen.PirateCoinViewModel
import cash.p.terminal.featureStacking.ui.stackingCoinScreen.StackingCoinScreen
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.TabItem
import cash.p.terminal.ui_compose.components.Tabs
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal

@Composable
internal fun StackingScreen(
    uiState: StackingUIState,
    calculatorUIState: CalculatorUIState,
    onCalculatorValueChanged: (String) -> Unit,
    onTabChanged: (StackingType) -> Unit,
    setCalculatorData: (StackingType, BigDecimal) -> Unit,
    onBuyClicked: (Token) -> Unit,
    onChartClicked: (String) -> Unit,
    onClickClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val modalBottomSheetState = rememberModalBottomSheetState(
        skipHalfExpanded = true,
        initialValue = ModalBottomSheetValue.Hidden
    )

    ModalBottomSheetLayout(
        sheetState = modalBottomSheetState,
        sheetBackgroundColor = ComposeAppTheme.colors.transparent,
        sheetContent = {
            CalculatorScreen(
                uiState = calculatorUIState,
                onValueChanged = onCalculatorValueChanged,
                onDoneClicked = {
                    coroutineScope.launch {
                        modalBottomSheetState.hide()
                    }
                })
        },
    ) {
        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(R.string.stacking),
                    navigationIcon = {
                        HsBackButton(onClick = onClickClose)
                    }
                )
            },
            backgroundColor = ComposeAppTheme.colors.tyler,
        ) {
            Column(Modifier.padding(it)) {
                val pagerState = rememberPagerState(initialPage = uiState.tabs.indexOfFirst { tab -> tab.selected }) { uiState.tabs.size }
                val pirateViewModel = koinViewModel<PirateCoinViewModel>()
                val cosantaViewModel = koinViewModel<CosantaCoinViewModel>()

                Tabs(tabs = uiState.tabs, onClick = { tab ->
                    onTabChanged(tab)
                    coroutineScope.launch {
                        pagerState.scrollToPage(tab.ordinal)
                    }
                })
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false
                ) { page ->
                    when (uiState.tabs[page].item) {
                        StackingType.PCASH -> {
                            StackingCoinScreen(
                                onBuyClicked = onBuyClicked,
                                onCalculatorClicked = {
                                    coroutineScope.launch {
                                        setCalculatorData(StackingType.PCASH, pirateViewModel.uiState.value.balance)
                                        modalBottomSheetState.show()
                                    }
                                },
                                onChartClicked = onChartClicked,
                                viewModel = pirateViewModel,
                                chartViewModel = koinViewModel<PirateCoinChartViewModel>()
                            )
                        }

                        StackingType.COSANTA -> {
                            StackingCoinScreen(
                                onBuyClicked = onBuyClicked,
                                onCalculatorClicked = {
                                    coroutineScope.launch {
                                        setCalculatorData(StackingType.COSANTA, cosantaViewModel.uiState.value.balance)
                                        modalBottomSheetState.show()
                                    }
                                },
                                onChartClicked = onChartClicked,
                                viewModel = cosantaViewModel,
                                chartViewModel = koinViewModel<CosantaCoinChartViewModel>()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StackingScreenPreview() {
    ComposeAppTheme {
        StackingScreen(
            uiState = StackingUIState(
                tabs = listOf(
                    TabItem(stringResource(R.string.pirate_cash), true, StackingType.PCASH),
                    TabItem(stringResource(R.string.cosanta), false, StackingType.COSANTA)
                )
            ),
            calculatorUIState = CalculatorUIState(),
            onTabChanged = {},
            onBuyClicked = {},
            onClickClose = {},
            onChartClicked = {},
            onCalculatorValueChanged = { },
            setCalculatorData = { _, _ -> }
        )
    }
}