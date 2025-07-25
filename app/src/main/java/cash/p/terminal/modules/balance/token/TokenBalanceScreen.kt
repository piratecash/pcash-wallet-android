package cash.p.terminal.modules.balance.token

import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.isCustom
import io.horizontalsystems.core.slideFromBottom
import cash.p.terminal.modules.balance.BackupRequiredError
import cash.p.terminal.modules.balance.BalanceViewItem
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.evmfee.FeeSettingsInfoDialog
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredDialog
import cash.p.terminal.modules.receive.ReceiveFragment
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.syncerror.SyncErrorDialog
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.modules.transactions.transactionList
import cash.p.terminal.modules.transactions.transactionsHiddenBlock
import cash.p.terminal.navigation.entity.SwapParams
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui.compose.components.CoinImage
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui.extensions.RotatingCircleProgressView
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryCircle
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.balance.DeemedValue
import cash.p.terminal.wallet.isCosanta
import cash.p.terminal.wallet.isPirateCash
import cash.p.terminal.modules.send.address.EnterAddressFragment
import io.horizontalsystems.core.SnackbarDuration
import io.horizontalsystems.core.helpers.HudHelper

@Composable
fun TokenBalanceScreen(
    viewModel: TokenBalanceViewModel,
    transactionsViewModel: TransactionsViewModel,
    sendResult: SendResult? = viewModel.sendResult,
    navController: NavController,
    refreshing: Boolean,
    onStackingClicked: () -> Unit,
    onShowAllTransactionsClicked: () -> Unit,
    onClickSubtitle: () -> Unit,
    onRefresh: () -> Unit
) {
    val uiState = viewModel.uiState

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = uiState.title,
                navigationIcon = {
                    HsBackButton(onClick = { navController.popBackStack() })
                }
            )
        }
    ) { paddingValues ->
        val transactionItems = uiState.transactions
        val view = LocalView.current
        when (sendResult) {
            SendResult.Sending -> {
                HudHelper.showInProcessMessage(
                    view,
                    R.string.Send_Sending,
                    SnackbarDuration.INDEFINITE
                )
            }

            is SendResult.Sent -> {
                HudHelper.showSuccessMessage(
                    view,
                    R.string.Send_Success,
                    SnackbarDuration.MEDIUM
                )
            }

            is SendResult.Failed -> {
                HudHelper.showErrorMessage(
                    view,
                    sendResult.caution.getDescription() ?: sendResult.caution.getString()
                )
            }

            null -> Unit
        }
        if (transactionItems == null || (transactionItems.isEmpty() && !uiState.hasHiddenTransactions)) {
            HSSwipeRefresh(
                refreshing = refreshing,
                modifier = Modifier.padding(paddingValues),
                onRefresh = onRefresh
            ) {
                Column {
                    uiState.balanceViewItem?.let {
                        TokenBalanceHeader(
                            balanceViewItem = it,
                            navController = navController,
                            viewModel = viewModel,
                            onStackingClicked = onStackingClicked,
                            onClickSubtitle = onClickSubtitle
                        )
                    }
                    if (transactionItems == null) {
                        ListEmptyView(
                            text = stringResource(R.string.Transactions_WaitForSync),
                            icon = R.drawable.ic_clock
                        )
                    } else {
                        ListEmptyView(
                            text = stringResource(R.string.Transactions_EmptyList),
                            icon = R.drawable.ic_outgoingraw
                        )
                    }
                }
            }
        } else {
            HSSwipeRefresh(
                refreshing = refreshing,
                modifier = Modifier.padding(paddingValues),
                onRefresh = onRefresh
            ) {
                LazyColumn(state = rememberLazyListState()) {
                    item {
                        uiState.balanceViewItem?.let {
                            TokenBalanceHeader(
                                balanceViewItem = it,
                                navController = navController,
                                viewModel = viewModel,
                                onStackingClicked = onStackingClicked,
                                onClickSubtitle = onClickSubtitle
                            )
                        }
                    }

                    transactionList(
                        transactionsMap = transactionItems,
                        willShow = { viewModel.willShow(it) },
                        onClick = {
                            onTransactionClick(
                                it,
                                viewModel,
                                transactionsViewModel,
                                navController
                            )
                        },
                        onBottomReached = { viewModel.onBottomReached() }
                    )
                    if (uiState.hasHiddenTransactions) {
                        transactionsHiddenBlock(
                            shortBlock = transactionItems.isNotEmpty(),
                            onShowAllTransactionsClicked = onShowAllTransactionsClicked
                        )
                    }
                }
            }
        }
    }
}


private fun onTransactionClick(
    transactionViewItem: TransactionViewItem,
    tokenBalanceViewModel: TokenBalanceViewModel,
    transactionsViewModel: TransactionsViewModel,
    navController: NavController
) {
    val transactionItem = tokenBalanceViewModel.getTransactionItem(transactionViewItem) ?: return
    transactionsViewModel.tmpItemToShow = transactionItem

    navController.slideFromBottom(R.id.transactionInfoFragment)
}

@Composable
private fun TokenBalanceHeader(
    balanceViewItem: BalanceViewItem,
    navController: NavController,
    viewModel: TokenBalanceViewModel,
    onStackingClicked: () -> Unit,
    onClickSubtitle: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VSpacer(height = (24.dp))
        WalletIcon(
            viewItem = balanceViewItem,
            viewModel = viewModel,
            navController = navController,
        )
        VSpacer(height = 12.dp)
        Text(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        viewModel.toggleBalanceVisibility()
                        HudHelper.vibrate(context)
                    }
                ),
            text = if (balanceViewItem.primaryValue.visible) balanceViewItem.primaryValue.value else "*****",
            color = if (balanceViewItem.primaryValue.dimmed) ComposeAppTheme.colors.grey else ComposeAppTheme.colors.leah,
            style = ComposeAppTheme.typography.title2R,
            textAlign = TextAlign.Center,
        )
        VSpacer(height = 6.dp)
        if (balanceViewItem.syncingTextValue != null) {
            body_grey(
                text = balanceViewItem.syncingTextValue + (balanceViewItem.syncedUntilTextValue?.let { " - $it" }
                    ?: ""),
                maxLines = 1,
            )
        } else {
            Text(
                text = if (balanceViewItem.secondaryValue.visible) viewModel.secondaryValue.value else "*****",
                color = if (balanceViewItem.secondaryValue.dimmed) ComposeAppTheme.colors.grey50 else ComposeAppTheme.colors.grey,
                style = ComposeAppTheme.typography.body,
                maxLines = 1,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (balanceViewItem.secondaryValue.visible) {
                                onClickSubtitle()
                            }
                        }
                    )
            )
        }
        VSpacer(height = 24.dp)
        ButtonsRow(
            viewItem = balanceViewItem,
            navController = navController,
            viewModel = viewModel,
            onStackingClicked = onStackingClicked
        )
        LockedBalanceSection(balanceViewItem, navController)
        balanceViewItem.warning?.let {
            VSpacer(height = 8.dp)
            TextImportantWarning(
                icon = R.drawable.ic_attention_20,
                title = it.title.getString(),
                text = it.text.getString()
            )
        }
        VSpacer(height = 16.dp)
    }
}

@Composable
private fun LockedBalanceSection(balanceViewItem: BalanceViewItem, navController: NavController) {
    if (balanceViewItem.lockedValues.isNotEmpty()) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ComposeAppTheme.colors.steel20, RoundedCornerShape(12.dp))
        ) {
            balanceViewItem.lockedValues.forEach { lockedValue ->
                LockedBalanceCell(
                    title = lockedValue.title.getString(),
                    infoTitle = lockedValue.infoTitle.getString(),
                    infoText = lockedValue.info.getString(),
                    lockedAmount = lockedValue.coinValue,
                    navController = navController
                )
            }
        }
    }
}

@Composable
private fun LockedBalanceCell(
    title: String,
    infoTitle: String,
    infoText: String,
    lockedAmount: DeemedValue<String>,
    navController: NavController
) {

    RowUniversal(
        modifier = Modifier
            .padding(horizontal = 16.dp),
    ) {
        subhead2_grey(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        HSpacer(8.dp)
        HsIconButton(
            modifier = Modifier.size(20.dp),
            onClick = {
                navController.slideFromBottom(
                    R.id.feeSettingsInfoDialog,
                    FeeSettingsInfoDialog.Input(infoTitle, infoText)
                )
            }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_info_20),
                contentDescription = "info button",
                tint = ComposeAppTheme.colors.grey
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            modifier = Modifier.padding(start = 6.dp),
            text = if (lockedAmount.visible) lockedAmount.value else "*****",
            color = if (lockedAmount.dimmed) ComposeAppTheme.colors.grey50 else ComposeAppTheme.colors.leah,
            style = ComposeAppTheme.typography.subhead2,
            maxLines = 1,
        )
    }
}

@Composable
private fun WalletIcon(
    viewItem: BalanceViewItem,
    viewModel: TokenBalanceViewModel,
    navController: NavController
) {
    Box(
        modifier = Modifier
            .height(52.dp)
            .fillMaxWidth(),
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
            Image(
                modifier = Modifier
                    .size(32.dp)
                    .clickable {
                        onSyncErrorClicked(viewItem, viewModel, navController, view)
                    },
                painter = painterResource(id = R.drawable.ic_attention_24),
                contentDescription = "coin icon",
                colorFilter = ColorFilter.tint(ComposeAppTheme.colors.lucian)
            )
        } else {
            CoinImage(
                token = viewItem.wallet.token,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun onSyncErrorClicked(
    viewItem: BalanceViewItem,
    viewModel: TokenBalanceViewModel,
    navController: NavController,
    view: View
) {
    when (val syncErrorDetails = viewModel.getSyncErrorDetails(viewItem)) {
        is BalanceViewModel.SyncError.Dialog -> {
            val wallet = syncErrorDetails.wallet
            val errorMessage = syncErrorDetails.errorMessage

            navController.slideFromBottom(
                R.id.syncErrorDialog,
                SyncErrorDialog.Input(wallet, errorMessage)
            )
        }

        is BalanceViewModel.SyncError.NetworkNotAvailable -> {
            HudHelper.showErrorMessage(view, R.string.Hud_Text_NoInternet)
        }
    }
}


@Composable
private fun ButtonsRow(
    viewItem: BalanceViewItem,
    navController: NavController,
    viewModel: TokenBalanceViewModel,
    onStackingClicked: () -> Unit
) {
    val onClickReceive = {
        try {
            val wallet = viewModel.getWalletForReceive()
            navController.slideFromRight(R.id.receiveFragment, ReceiveFragment.Input(wallet))
        } catch (e: BackupRequiredError) {
            val text = Translator.getString(
                R.string.ManageAccount_BackupRequired_Description,
                e.account.name,
                e.coinTitle
            )
            navController.slideFromBottom(
                R.id.backupRequiredDialog,
                BackupRequiredDialog.Input(e.account, text)
            )
        }
    }

    Row(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp)
    ) {
        if (viewItem.isWatchAccount) {
            ButtonPrimaryDefault(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.Balance_Address),
                onClick = onClickReceive,
            )
            if (viewItem.wallet.isCosanta() || viewItem.wallet.isPirateCash()) {
                HSpacer(8.dp)
                ButtonPrimaryCircle(
                    icon = R.drawable.ic_coins_stacking,
                    contentDescription = stringResource(R.string.stacking),
                    onClick = {
                        onStackingClicked()
                    }
                )
            }
        } else {
            if (!viewItem.isSendDisabled) {
                ButtonPrimaryYellow(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.Balance_Send),
                    onClick = {
                        val sendTitle = Translator.getString(R.string.Send_Title, viewItem.wallet.token.fullCoin.coin.code)
                        navController.slideFromRight(
                            R.id.enterAddressFragment,
                            EnterAddressFragment.Input(
                                wallet = viewItem.wallet,
                                title = sendTitle
                            )
                        )
                    },
                    enabled = viewItem.sendEnabled
                )
                HSpacer(8.dp)
            }
            if (!viewItem.swapVisible) {
                ButtonPrimaryDefault(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.Balance_Receive),
                    onClick = onClickReceive,
                )
            } else {
                ButtonPrimaryCircle(
                    icon = R.drawable.ic_arrow_down_left_24,
                    contentDescription = stringResource(R.string.Balance_Receive),
                    onClick = onClickReceive,
                )
            }
            if (viewItem.swapVisible) {
                HSpacer(8.dp)
                ButtonPrimaryCircle(
                    icon = R.drawable.ic_swap_24,
                    contentDescription = stringResource(R.string.Swap),
                    onClick = {
                        navController.slideFromRight(
                            R.id.multiswap,
                            SwapParams.TOKEN_IN to viewItem.wallet.token
                        )
                    },
                    enabled = viewItem.swapEnabled
                )
            }
            if (viewItem.wallet.isCosanta() || viewItem.wallet.isPirateCash()) {
                HSpacer(8.dp)
                ButtonPrimaryCircle(
                    icon = R.drawable.ic_coins_stacking,
                    contentDescription = stringResource(R.string.stacking),
                    onClick = {
                        onStackingClicked()
                    }
                )
            }
        }
        HSpacer(8.dp)
        ButtonPrimaryCircle(
            icon = R.drawable.ic_chart_24,
            contentDescription = stringResource(R.string.Coin_Info),
            enabled = !viewItem.wallet.token.isCustom,
            onClick = {
                val coinUid = viewItem.wallet.coin.uid
                val arguments = CoinFragmentInput(coinUid)

                navController.slideFromRight(R.id.coinFragment, arguments)
            },
        )
    }
    if (viewItem.isShowShieldFunds) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.shield_funds),
                onClick = viewModel::proposeShielding
            )
            body_grey(
                text = stringResource(R.string.typical_fee),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
