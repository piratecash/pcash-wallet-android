package cash.p.terminal.modules.balance.cex

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.balance.AccountViewItem
import cash.p.terminal.modules.balance.BalanceModule
import cash.p.terminal.modules.balance.ui.BalanceSortingSelector
import cash.p.terminal.modules.balance.ui.BalanceTitleRow
import cash.p.terminal.modules.balance.ui.TotalBalanceRow
import cash.p.terminal.modules.balance.ui.wallets
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui.compose.components.CoinImage
import cash.p.terminal.ui.compose.components.diffText
import cash.p.terminal.ui.extensions.RotatingCircleProgressView
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellMultilineClear
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HeaderSorting
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.diffColor
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.helpers.HudHelper

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BalanceForAccountCex(
    navController: NavController,
    accountViewItem: AccountViewItem,
    paddingValuesParent: PaddingValues
) {
    val viewModel = viewModel<BalanceCexViewModel>(factory = BalanceModule.FactoryCex())
    val uiState = viewModel.uiState
    val totalState = viewModel.totalUiState

    val context = LocalContext.current

    val activeScreen = uiState.isActiveScreen
    if (activeScreen) {
        Scaffold(
            containerColor = ComposeAppTheme.colors.tyler,
            topBar = {
                AppBar(
                    title = {
                        BalanceTitleRow(navController, accountViewItem.name)
                    }
                )
            }
        ) { paddingValues ->
            Column(
                Modifier.padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValuesParent.calculateBottomPadding()
                )
            ) {

                HSSwipeRefresh(
                    refreshing = uiState.isRefreshing,
                    onRefresh = viewModel::onRefresh
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = rememberSaveable(
                            accountViewItem.id,
                            uiState.sortType,
                            saver = LazyListState.Saver
                        ) {
                            LazyListState()
                        }
                    ) {
                        item {
                            TotalBalanceRow(
                                totalState = totalState,
                                onClickTitle = {
                                    viewModel.toggleBalanceVisibility()
                                    HudHelper.vibrate(context)
                                },
                                onClickSubtitle = {
                                    viewModel.toggleTotalType()
                                    HudHelper.vibrate(context)
                                }
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 16.dp)
                            ) {
                                ButtonPrimaryYellow(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(R.string.Balance_Withdraw),
                                    enabled = uiState.withdrawEnabled,
                                    onClick = {},
                                )

                                HSpacer(width = 8.dp)

                                ButtonPrimaryDefault(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(R.string.Balance_Deposit),
                                    onClick = {
                                        navController.slideFromRight(R.id.depositCexFragment)
                                    }
                                )
                            }
                        }

                        item {
                            Divider(
                                thickness = 1.dp,
                                color = ComposeAppTheme.colors.steel10,
                            )
                        }

                        if (uiState.viewItems.isNotEmpty()) {
                            stickyHeader {
                                HeaderSorting {
                                    BalanceSortingSelector(
                                        sortType = uiState.sortType,
                                        sortTypes = viewModel.sortTypes,
                                        onSelectSortType = viewModel::onSelectSortType
                                    )
                                }
                            }

                            wallets(
                                items = uiState.viewItems,
                                key = { it.assetId },
                            ) { item ->
                                BalanceCardCex(navController, item)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun BalanceCardCex(
    navController: NavController,
    viewItem: BalanceCexViewItem
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    navController.slideFromRight(
                        R.id.cexAssetFragment,
                        viewItem.cexAsset
                    )
                }
            )
    ) {
        CellMultilineClear(height = 64.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WalletIconCex(viewItem)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(weight = 1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            body_leah(
                                text = viewItem.coinCode,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!viewItem.badge.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ComposeAppTheme.colors.jeremy)
                                ) {
                                    Text(
                                        modifier = Modifier.padding(
                                            start = 4.dp,
                                            end = 4.dp,
                                            bottom = 1.dp
                                        ),
                                        text = viewItem.badge,
                                        color = ComposeAppTheme.colors.bran,
                                        style = ComposeAppTheme.typography.microSB,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(24.dp))
                        if (viewItem.primaryValue.visible) {
                            Text(
                                text = viewItem.primaryValue.value,
                                color = if (viewItem.primaryValue.dimmed) ComposeAppTheme.colors.grey else ComposeAppTheme.colors.leah,
                                style = ComposeAppTheme.typography.headline2,
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                        ) {
                            if (viewItem.exchangeValue.visible) {
                                Row {
                                    Text(
                                        text = viewItem.exchangeValue.value,
                                        color = if (viewItem.exchangeValue.dimmed) ComposeAppTheme.colors.grey50 else ComposeAppTheme.colors.grey,
                                        style = ComposeAppTheme.typography.subhead2,
                                        maxLines = 1,
                                    )
                                    Text(
                                        modifier = Modifier.padding(start = 4.dp),
                                        text = diffText(viewItem.diff),
                                        color = diffColor(viewItem.diff),
                                        style = ComposeAppTheme.typography.subhead2,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier.padding(start = 16.dp),
                        ) {
                            if (viewItem.secondaryValue.visible) {
                                Text(
                                    text = viewItem.secondaryValue.value,
                                    color = if (viewItem.secondaryValue.dimmed) ComposeAppTheme.colors.grey50 else ComposeAppTheme.colors.grey,
                                    style = ComposeAppTheme.typography.subhead2,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))
            }
        }
    }
}

@Composable
fun WalletIconCex(
    viewItem: BalanceCexViewItem
) {
    Box(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        viewItem.syncingProgress.progress?.let { progress ->
            AndroidView(
                modifier = Modifier
                    .size(52.dp),
                factory = { context ->
                    RotatingCircleProgressView(context)
                },
                update = { view ->
                    val color = when (viewItem.syncingProgress.dimmed) {
                        true -> R.color.grey_50
                        false -> R.color.grey
                    }
                    view.setProgressColored(progress, view.context.getColor(color))
                }
            )
        }
        if (viewItem.failedIconVisible) {
            val view = LocalView.current
            val clickableModifier = if (viewItem.errorMessage != null) {
                Modifier.clickable(onClick = {
                    HudHelper.showErrorMessage(
                        view,
                        viewItem.errorMessage
                    )
                })
            } else {
                Modifier
            }

            Image(
                modifier = Modifier
                    .size(32.dp)
                    .then(clickableModifier),
                painter = painterResource(id = R.drawable.ic_attention_24),
                contentDescription = "coin icon",
                colorFilter = ColorFilter.tint(ComposeAppTheme.colors.lucian)
            )
        } else {
            CoinImage(
                coin = viewItem.coin,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
