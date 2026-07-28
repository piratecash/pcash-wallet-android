package cash.p.terminal.modules.balance.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.MainGraphDirections
import cash.p.terminal.R
import cash.p.terminal.core.Caution
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.navigation.openQrScanner
import cash.p.terminal.modules.backupalert.BackupAlert
import cash.p.terminal.modules.balance.AccountViewItem
import cash.p.terminal.modules.balance.BalanceModule
import cash.p.terminal.modules.balance.BalanceViewItem2
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.balance.TotalUIState
import cash.p.terminal.modules.contacts.screen.ConfirmationBottomSheet
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.walletconnect.list.WalletConnectListViewModel
import cash.p.terminal.modules.zcashmigration.ZcashMigrationFlow
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.rememberDebouncedAction
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.launch

@Composable
fun BalanceForAccount(
    navController: NavController,
    accountViewItem: AccountViewItem,
    paddingValuesParent: PaddingValues,
    onOpenTransactionInfo: (TransactionItem) -> Unit,
) {
    val viewModel = viewModel<BalanceViewModel>(factory = BalanceModule.Factory())

    LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    val scannerTitle = stringResource(R.string.qr_scanner_title_smart_scan)
    var showInvalidUrlSheet by rememberSaveable { mutableStateOf(false) }

    viewModel.uiState.errorMessage?.let { message ->
        val view = LocalView.current
        HudHelper.showErrorMessage(view, text = message)
        viewModel.errorShown()
    }

    LaunchedEffect(viewModel.connectionResult) {
        if (viewModel.connectionResult == WalletConnectListViewModel.ConnectionResult.Error) {
            showInvalidUrlSheet = true
            viewModel.onHandleRoute()
        }
    }

    // The Material 3 sheet renders in its own window above the in-activity lock/calculator
    // overlay. Gating the render on the current lock state (below) keeps it off the lock
    // screen whether the app locks while it is open or an error arrives while already locked.
    val pinComponent = remember { getKoinInstance<IPinComponent>() }
    val isLocked by pinComponent.isLockedFlow.collectAsStateWithLifecycle()

    BackupAlert(navController)

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = {
                    val walletName = if (viewModel.totalUiState is TotalUIState.Hidden) {
                        "*****"
                    } else {
                        accountViewItem.name
                    }
                    BalanceTitleRow(navController, walletName)
                },
                menuItems = buildList {
                    if (accountViewItem.isCoinManagerEnabled) {
                        add(
                            MenuItem(
                                title = TranslatableString.ResString(R.string.display_options),
                                icon = R.drawable.ic_search,
                                onClick = {
                                    navController.slideFromRight(R.id.manageWalletsFragment)
                                })
                        )
                    }
                    if (!accountViewItem.type.isWatchAccountType) {
                        add(
                            MenuItem(
                                title = TranslatableString.ResString(R.string.WalletConnect_NewConnect),
                                icon = R.drawable.ic_qr_scan_20,
                                onClick = {
                                    navController.openQrScanner(
                                        title = scannerTitle,
                                        showPasteButton = true
                                    ) { scannedText ->
                                        viewModel.handleScannedData(scannedText)
                                    }
                                }
                            )
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val uiState = viewModel.uiState

        val navigateToTokenBalance: (BalanceViewItem2) -> Unit = rememberDebouncedAction { item ->
            navController.navigate(
                MainGraphDirections.actionToTokenBalance(item.wallet)
            )
        }

        Crossfade(
            targetState = uiState.viewState,
            modifier = Modifier
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValuesParent.calculateBottomPadding()
                )
                .fillMaxSize(),
            label = ""
        ) { viewState ->
            when (viewState) {
                ViewState.Success -> {
                    val balanceViewItems = uiState.balanceViewItems
                    BalanceItems(
                        balanceViewItems = balanceViewItems,
                        viewModel = viewModel,
                        onItemClick = navigateToTokenBalance,
                        onBalanceClick = viewModel::onBalanceClick,
                        accountViewItem = accountViewItem,
                        navController = navController,
                        uiState = uiState,
                        totalState = viewModel.totalUiState,
                        onOpenTransactionInfo = onOpenTransactionInfo,
                    )
                }

                ViewState.Loading,
                is ViewState.Error,
                null -> {
                }
            }
        }
    }

    if (showInvalidUrlSheet && !isLocked) {
        InvalidUrlConnectionBottomSheet(
            onRetry = {
                showInvalidUrlSheet = false
                navController.openQrScanner(
                    title = scannerTitle,
                    showPasteButton = true
                ) { scannedText ->
                    viewModel.handleScannedData(scannedText)
                }
            },
            onDismiss = { showInvalidUrlSheet = false }
        )
    }

    viewModel.uiState.zcashMigrationAlertWallet?.let { wallet ->
        ZcashMigrationFlow(
            wallet = wallet,
            onClose = { viewModel.zcashMigrationAlertHandled(wallet) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvalidUrlConnectionBottomSheet(
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ConfirmationBottomSheet(
            title = stringResource(R.string.WalletConnect_Title),
            text = stringResource(R.string.WalletConnect_Error_InvalidUrl),
            iconPainter = painterResource(R.drawable.ic_wallet_connect_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            confirmText = stringResource(R.string.Button_TryAgain),
            cautionType = Caution.Type.Warning,
            cancelText = stringResource(R.string.Button_Cancel),
            onConfirm = {
                scope.launch {
                    sheetState.hide()
                    onRetry()
                }
            },
            onClose = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }
        )
    }
}

@Composable
fun BalanceTitleRow(
    navController: NavController,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        title3_leah(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f, fill = false)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(id = R.drawable.ic_down_24),
            contentDescription = null,
            tint = ComposeAppTheme.colors.yellowD,
            modifier = Modifier
                .testTag("wallet_switcher")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    navController.slideFromBottom(
                        R.id.manageAccountsFragment,
                        ManageAccountsModule.Mode.Switcher
                    )
                },
        )
    }
}
