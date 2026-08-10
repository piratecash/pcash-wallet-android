@file:OptIn(ExperimentalFoundationApi::class)

package cash.p.terminal.modules.balance.token

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.MainGraphDirections
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.premiumAction
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.modules.balance.BackupRequiredError
import cash.p.terminal.modules.balance.BalanceViewItem
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.balance.SyncingProgress
import cash.p.terminal.modules.balance.ui.FlipHiddenBalanceInfoHost
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusButton
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredDialog
import cash.p.terminal.modules.receive.ReceiveFragment
import cash.p.terminal.modules.send.SendFragment
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.syncerror.showSyncErrorDialog
import cash.p.terminal.modules.transactions.AmlCheckInfoBottomSheet
import cash.p.terminal.modules.transactions.AmlCheckPromoBanner
import cash.p.terminal.modules.transactions.Filter
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.FilterTypeTabs
import cash.p.terminal.modules.transactions.SearchEmptyResultsView
import cash.p.terminal.modules.transactions.SearchInProgressView
import cash.p.terminal.modules.transactions.TransactionSearchField
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.modules.transactions.transactionList
import cash.p.terminal.modules.transactions.transactionsHiddenBlock
import cash.p.terminal.navigation.entity.SwapParams
import cash.p.terminal.modules.zcashmigration.ZcashMigrationFlow
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui.compose.components.Badge
import cash.p.terminal.ui.compose.components.BadgeText
import cash.p.terminal.ui.compose.components.CoinIconWithSyncProgress
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.ScreenSecurityState
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryCircle
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonSecondary
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HeaderStick
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SecondaryButtonDefaults
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.TextImportant
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.diffColor
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_jacob
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.balance.DeemedValue
import cash.p.terminal.wallet.isStakingWallet

private const val HEADER_CONTENT_TYPE = "token_balance_sticky_header"
private const val PLACEHOLDER_CONTENT_TYPE = "token_balance_empty_placeholder"

// Distinct type for the sticky header's Lazy key so it can never collide with the
// transaction rows' String uid keys (an enum never equals a String).
private enum class TokenBalanceLazyKey { SearchHeader }

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
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val view = LocalView.current
    var showMoneroSendPreparation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TokenBalanceModule.Event.OpenSend -> {
                    showMoneroSendPreparation = false
                    navController.openSend(event.wallet)
                }
            }
        }
    }

    LaunchedEffect(viewModel.uiState.moneroSpendReadiness) {
        if (
            showMoneroSendPreparation &&
            viewModel.uiState.moneroSpendReadiness ==
            MoneroSpendReadiness.ReconcilingSpentStatus
        ) {
            showMoneroSendPreparation = false
        }
    }

    TokenBalanceScreenContent(
        uiState = viewModel.uiState,
        secondaryValue = viewModel.secondaryValue,
        sendResult = sendResult,
        navController = navController,
        refreshing = refreshing,
        onToggleFavorite = viewModel::toggleFavorite,
        onToggleBalanceVisibility = viewModel::toggleBalanceVisibility,
        onSearchClick = viewModel::onSearchClick,
        onSearchClose = viewModel::onSearchClose,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSetTransactionType = viewModel::setTransactionType,
        onWillShow = viewModel::willShow,
        onTransactionClick = {
            onTransactionClick(it, viewModel, transactionsViewModel, navController)
        },
        onSensitiveTransactionClick = {
            HudHelper.vibrate(App.instance)
            transactionsViewModel.toggleTransactionInfoHidden(it.uid)
        },
        onBottomReached = viewModel::onBottomReached,
        onSetAmlCheckEnabled = { enabled ->
            if (enabled) {
                navController.premiumAction { viewModel.setAmlCheckEnabled(true) }
            } else {
                viewModel.setAmlCheckEnabled(false)
            }
        },
        onDismissAmlPromo = {
            viewModel.dismissAmlPromo()
            HudHelper.showPremiumMessage(
                view,
                R.string.aml_promo_dismiss_hud,
                SnackbarDuration.LONG
            )
        },
        onDismissNetworkFeeWarning = viewModel::dismissNetworkFeeWarning,
        onSendClick = sendClick@{
            val wallet = viewModel.uiState.balanceViewItem?.wallet ?: return@sendClick
            if (
                viewModel.uiState.moneroHardwareWallet &&
                viewModel.uiState.moneroSpendReadiness != MoneroSpendReadiness.Ready
            ) {
                showMoneroSendPreparation = true
                viewModel.prepareMoneroSend()
            } else {
                navController.openSend(wallet)
            }
        },
        onReceiveClick = { onReceiveClicked(viewModel, navController) },
        onShieldClick = viewModel::proposeShielding,
        onSyncErrorClick = { onSyncErrorClicked(it, viewModel, navController) },
        onStackingClicked = onStackingClicked,
        onShowAllTransactionsClicked = onShowAllTransactionsClicked,
        onClickSubtitle = onClickSubtitle,
        onRefresh = onRefresh,
        onSettingsClick = onSettingsClick,
    )

    if (showMoneroSendPreparation) {
        MoneroSendPreparationBottomSheet(
            syncInProgress = viewModel.uiState.moneroKeyImageSyncInProgress,
            error = viewModel.uiState.moneroKeyImageSyncError,
            fullWalletRecoveryAvailable = viewModel.uiState.moneroFullWalletRecoveryAvailable,
            onSync = viewModel::syncMoneroKeyImages,
            onFullWalletRecovery = viewModel::fullMoneroWalletRecovery,
            onDismiss = {
                viewModel.cancelMoneroKeyImageSync()
                showMoneroSendPreparation = false
            },
        )
    }
}

@Composable
private fun TokenBalanceScreenContent(
    uiState: TokenBalanceModule.TokenBalanceUiState,
    secondaryValue: DeemedValue<String>,
    sendResult: SendResult?,
    navController: NavController,
    refreshing: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleBalanceVisibility: () -> Unit,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSetTransactionType: (FilterTransactionType) -> Unit,
    onWillShow: (TransactionViewItem) -> Unit,
    onTransactionClick: (TransactionViewItem) -> Unit,
    onSensitiveTransactionClick: (TransactionViewItem) -> Unit,
    onBottomReached: () -> Unit,
    onSetAmlCheckEnabled: (Boolean) -> Unit,
    onDismissAmlPromo: () -> Unit,
    onDismissNetworkFeeWarning: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onShieldClick: () -> Unit,
    onSyncErrorClick: (BalanceViewItem) -> Unit,
    onStackingClicked: () -> Unit,
    onShowAllTransactionsClicked: () -> Unit,
    onClickSubtitle: () -> Unit,
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val view = LocalView.current

    var showAmlInfoSheet by remember { mutableStateOf(false) }

    val failedIconVisible = uiState.balanceViewItem?.failedIconVisible == true
    val loading = uiState.balanceViewItem?.syncingProgress?.progress != null

    LaunchedEffect(failedIconVisible) {
        val viewItem = uiState.balanceViewItem
        if (viewItem != null && shouldAutoShowSyncError(
                failedIconVisible,
                ScreenSecurityState.isAppLocked
            )
        ) {
            onSyncErrorClick(viewItem)
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = uiState.title,
                navigationIcon = {
                    HsBackButton(onClick = { navController.popBackStackSafely() })
                },
                menuItems = buildList {
                    add(
                        MenuItem(
                            title = TranslatableString.ResString(
                                if (uiState.isFavorite) R.string.CoinPage_Unfavorite else R.string.CoinPage_Favorite
                            ),
                            icon = if (uiState.isFavorite) R.drawable.ic_star_filled_20 else R.drawable.ic_star_20,
                            tint = if (uiState.isFavorite) ComposeAppTheme.colors.jacob else ComposeAppTheme.colors.grey,
                            onClick = onToggleFavorite
                        )
                    )
                    if (!uiState.isCustomToken) {
                        add(
                            MenuItem(
                                title = TranslatableString.ResString(R.string.Coin_Info),
                                icon = R.drawable.ic_chart_24,
                                onClick = {
                                    val coinUid = uiState.balanceViewItem?.wallet?.coin?.uid
                                        ?: return@MenuItem
                                    val arguments = CoinFragmentInput(coinUid)
                                    navController.slideFromRight(R.id.coinFragment, arguments)
                                }
                            )
                        )
                    }
                    add(
                        MenuItem(
                            title = TranslatableString.ResString(R.string.Settings_Title),
                            icon = R.drawable.ic_manage_2_24,
                            onClick = onSettingsClick
                        )
                    )
                    if (failedIconVisible && !loading) {
                        add(
                            MenuItem(
                                title = TranslatableString.ResString(R.string.BalanceSyncError_Title),
                                icon = R.drawable.ic_attention_red_24,
                                tint = ComposeAppTheme.colors.lucian,
                                onClick = {
                                    uiState.balanceViewItem?.let(onSyncErrorClick)
                                }
                            )
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val transactionItems = uiState.transactions
        // Tabs follow the user's filter setting only, never the transient load state. During a tab
        // switch transactions briefly goes null (loading window); coupling tab visibility to it
        // would make the tabs vanish mid-switch.
        val showFilterTabs = uiState.transactionFiltersEnabled
        val listState = rememberLazyListState()
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

            is SendResult.SentButQueued -> {
                HudHelper.showWarningMessage(
                    view,
                    R.string.send_success_queued,
                    SnackbarDuration.LONG
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
        if (uiState.balanceViewItem == null) {
            // Render the list only after the balance header has a real height. A zero-height
            // header item would make LazyColumn anchor on the item below it; when the header
            // later loads and grows, the list would open pre-scrolled with the tabs pinned and
            // the balance pushed off-screen.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                HSCircularProgressIndicator()
            }
            return@Scaffold
        }
        // A single LazyColumn renders every state (loading / empty / content) so the scroll
        // container is never recreated. Swapping to a separate Column when the list briefly
        // empties on a tab switch would reset the scroll position and make the pinned header jump.
        // The empty/loading placeholder is an item filling the viewport (fillParentMaxSize), which
        // keeps the scroll extent so the header stays pinned while the next filter loads.
        //
        // The tabs and the hide-balance/search panel are combined into a single sticky header
        // (they scroll up with the balance, then pin together). A LazyColumn pins just one
        // sticky header at a time, so the date can't be sticky too. Instead the date group
        // headers stay inline in the list and an opaque overlay, positioned right below the
        // pinned header, shows the current group's date.
        val uidToDate = remember(transactionItems) {
            buildMap<Any, String> {
                transactionItems?.forEach { (date, txs) ->
                    txs.forEach { put(it.uid, date) }
                }
            }
        }
        val currentStickyDate by remember(listState, uidToDate) {
            derivedStateOf { stickyTransactionDate(listState, uidToDate) }
        }
        // Live top offset of the empty/loading placeholder relative to the viewport top. It
        // shrinks as the balance header scrolls away, letting the placeholder's centered content
        // follow the visible gap below the pinned panel instead of drifting up behind it.
        val placeholderTopPx by remember(listState) {
            derivedStateOf {
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.contentType == PLACEHOLDER_CONTENT_TYPE }?.offset ?: 0
            }
        }
        var headerHeightPx by remember { mutableIntStateOf(0) }
        // Opening search auto-focuses the field and shows the keyboard
        val searchHeaderIndex = 1 +
            (if (failedIconVisible) 1 else 0) +
            (if (uiState.showAmlPromo) 1 else 0)
        LaunchedEffect(uiState.searchActive, searchHeaderIndex) {
            if (uiState.searchActive && listState.firstVisibleItemIndex < searchHeaderIndex) {
                listState.animateScrollToItem(searchHeaderIndex)
            }
        }
        HSSwipeRefresh(
            refreshing = refreshing,
            modifier = Modifier.padding(paddingValues),
            onRefresh = onRefresh
        ) {
            Box {
                // Overscroll is disabled: the stretch effect moves the pinned header (inside the
                // list) but not the date overlay (drawn outside it), opening a gap between them.
                LazyColumn(state = listState, overscrollEffect = null) {
                    item {
                        uiState.balanceViewItem?.let {
                            TokenBalanceHeader(
                                balanceViewItem = it,
                                navController = navController,
                                uiState = uiState,
                                secondaryValue = secondaryValue,
                                onStackingClicked = onStackingClicked,
                                onClickSubtitle = onClickSubtitle,
                                onToggleBalanceVisibility = onToggleBalanceVisibility,
                                onSendClick = onSendClick,
                                onReceiveClick = onReceiveClick,
                                onShieldClick = onShieldClick,
                                onSyncErrorClick = onSyncErrorClick,
                                onDismissNetworkFeeWarning = onDismissNetworkFeeWarning,
                                isShowShieldFunds = uiState.isShowShieldFunds
                            )
                        }
                    }

                    if (failedIconVisible) {
                        item {
                            TokenNotSyncedSection(
                                onBlockchainStatusClick = {
                                    uiState.balanceViewItem?.wallet?.token?.blockchain?.let { blockchain ->
                                        navController.slideFromRight(
                                            R.id.blockchainStatusFragment,
                                            blockchain
                                        )
                                    }
                                },
                                onRetry = onRefresh,
                            )
                        }
                    }

                    if (uiState.showAmlPromo) {
                        item {
                            AmlCheckPromoBanner(
                                amlCheckEnabled = uiState.amlCheckEnabled,
                                onToggleChange = onSetAmlCheckEnabled,
                                onInfoClick = { showAmlInfoSheet = true },
                                onClose = onDismissAmlPromo,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }

                    // NAMED contentType argument: stickyHeader's first positional param is `key`,
                    // so passing HEADER_CONTENT_TYPE positionally would set the key and leave
                    // contentType null, and stickyTransactionDate (which matches on contentType)
                    // would never find this header.
                    // Stable key so the pinned search/filter header keeps its slot (and the
                    // search field's internal TextFieldValue state) across recompositions
                    // instead of being recreated — Samsung problem.
                    stickyHeader(
                        key = TokenBalanceLazyKey.SearchHeader,
                        contentType = HEADER_CONTENT_TYPE
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Opaque background: once pinned, transactions scroll UNDER this
                                // header. FilterTypeTabs paints its own background, but the search
                                // row / HideBalanceSearchRow do not, so without this the list would
                                // bleed through the pinned header.
                                .background(ComposeAppTheme.colors.tyler)
                                .onSizeChanged { headerHeightPx = it.height }
                        ) {
                            if (showFilterTabs) {
                                FilterTypeTabs(
                                    filterTypes = uiState.transactionFilterTypes,
                                    offlineSignedSelected = false,
                                    onTransactionTypeClick = onSetTransactionType,
                                )
                            }
                            if (uiState.searchActive) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HsBackButton(onClick = onSearchClose)
                                    TransactionSearchField(
                                        query = uiState.searchQuery,
                                        onQueryChange = onSearchQueryChange,
                                    )
                                }
                            } else {
                                HideBalanceSearchRow(
                                    hideBalance = !uiState.balanceViewItem.primaryValue.visible,
                                    onToggleBalanceVisibility = onToggleBalanceVisibility,
                                    onSearchClick = onSearchClick,
                                )
                            }
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = ComposeAppTheme.colors.steel20,
                            )
                        }
                    }

                    if (transactionItems == null ||
                        (transactionItems.isEmpty() && !uiState.hasHiddenTransactions)
                    ) {
                        // Placeholder fills the viewport so switching to an empty filter keeps the
                        // sticky header pinned at the same scroll position instead of snapping the list.
                        item(contentType = PLACEHOLDER_CONTENT_TYPE) {
                            Box(modifier = Modifier.fillParentMaxSize()) {
                                // The placeholder fills the viewport (to keep the scroll extent), so its
                                // centered content is re-centered into the visible gap below the pinned
                                // panel. Paddings derive from placeholderTopPx (the live top offset of
                                // this item), so the centering tracks the scroll and the content no longer
                                // drifts up behind the panel as the header collapses. The keyboard overlap
                                // (IME height minus the navigation-bar inset the Scaffold already applied)
                                // is added to the bottom so, in search mode, the content stays centered
                                // above the keyboard instead of sliding under it.
                                val density = LocalDensity.current
                                val keyboardOverlapPx = (
                                    WindowInsets.ime.getBottom(density) -
                                        WindowInsets.navigationBars.getBottom(density)
                                    ).coerceAtLeast(0)
                                val paddingValues = PaddingValues(
                                    top = with(density) {
                                        (headerHeightPx - placeholderTopPx).coerceAtLeast(0).toDp()
                                    },
                                    bottom = with(density) {
                                        (placeholderTopPx.coerceAtLeast(0) + keyboardOverlapPx).toDp()
                                    }
                                )
                                if (uiState.searchScanning) {
                                    Box(modifier = Modifier.padding(paddingValues)) {
                                        SearchInProgressView()
                                    }
                                } else if (uiState.searchEmptyResult) {
                                    Box(modifier = Modifier.padding(paddingValues)) {
                                        SearchEmptyResultsView()
                                    }
                                } else if (transactionItems == null || uiState.syncing) {
                                    ListEmptyView(
                                        text = stringResource(R.string.Transactions_WaitForSync),
                                        icon = R.drawable.ic_clock,
                                        paddingValues = paddingValues
                                    )
                                } else {
                                    ListEmptyView(
                                        text = stringResource(R.string.Transactions_EmptyList),
                                        icon = R.drawable.ic_outgoingraw,
                                        paddingValues = paddingValues
                                    )
                                }
                            }
                        }
                    } else {
                        transactionList(
                            transactionsMap = transactionItems,
                            willShow = onWillShow,
                            onClick = onTransactionClick,
                            isItemBalanceHidden = { !it.showAmount },
                            onSensitiveValueClick = onSensitiveTransactionClick,
                            onBottomReached = onBottomReached,
                            stickyDateHeaders = false
                        )
                        if (uiState.hasHiddenTransactions) {
                            transactionsHiddenBlock(
                                shortBlock = transactionItems.isNotEmpty(),
                                onShowAllTransactionsClicked = onShowAllTransactionsClicked
                            )
                        }
                    }
                }
                PinnedDateOverlay(offsetY = { headerHeightPx }, date = { currentStickyDate })
            }
        }
    }

    if (showAmlInfoSheet) {
        AmlCheckInfoBottomSheet(
            onPremiumSettingsClick = {
                showAmlInfoSheet = false
                navController.slideFromRight(
                    R.id.premiumSettingsFragment
                )
            },
            onLaterClick = { showAmlInfoSheet = false },
            onDismiss = { showAmlInfoSheet = false }
        )
    }

    // Show the flip "balance hidden" info sheet on the asset screen too, so a flip here explains
    // itself immediately instead of only once the user returns to the balance screen. Gate it off
    // the lock and the AML sheet so it never stacks over them or leaks above the lock disguise.
    FlipHiddenBalanceInfoHost(
        canShow = !ScreenSecurityState.isAppLocked && !showAmlInfoSheet
    )
}


// Date for the overlay pinned below the combined sticky header (tabs + hide-balance/search
// panel): the day-group of the first transaction whose bottom edge is still below the header's
// bottom edge. Returns null until the header is actually pinned (header.offset == 0), so the
// overlay stays hidden while the balance is on screen and the inline date headers carry the
// date. Reading layout offsets makes the date flip exactly when a group meets the overlay's
// edge, not when it reaches the very top of the list.
private fun stickyTransactionDate(
    listState: LazyListState,
    uidToDate: Map<Any, String>
): String? {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    val header = visibleItems.firstOrNull { it.contentType == HEADER_CONTENT_TYPE } ?: return null
    if (header.offset > 0) return null
    val headerBottom = header.offset + header.size
    val anchor =
        visibleItems.firstOrNull { it.key in uidToDate && it.offset + it.size > headerBottom }
            ?: return null
    return uidToDate[anchor.key]
}

@Composable
private fun PinnedDateOverlay(offsetY: () -> Int, date: () -> String?) {
    val text = date() ?: return
    Box(modifier = Modifier.offset { IntOffset(0, offsetY()) }) {
        HeaderStick(text = text)
    }
}

@Composable
private fun HideBalanceSearchRow(
    hideBalance: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    onSearchClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    onToggleBalanceVisibility()
                    HudHelper.vibrate(context)
                }
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            subhead2_grey(text = stringResource(R.string.hide_balance))
            HSpacer(8.dp)
            Icon(
                painter = painterResource(
                    if (hideBalance) R.drawable.ic_eye_off else R.drawable.ic_eye_20
                ),
                contentDescription = null,
                tint = ComposeAppTheme.colors.grey,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        HsIconButton(onClick = onSearchClick) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.Button_Search),
                tint = ComposeAppTheme.colors.grey
            )
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

private fun NavController.openSend(wallet: Wallet) {
    val sendTitle = Translator.getString(
        R.string.Send_Title,
        wallet.token.fullCoin.coin.code,
    )
    navigate(
        MainGraphDirections.actionGlobalToSendFragment(
            SendFragment.Input(
                wallet = wallet,
                title = sendTitle,
                sendEntryPointDestId = R.id.tokenBalanceFragment,
                address = null,
            )
        )
    )
}

@Composable
private fun TokenBalanceHeader(
    balanceViewItem: BalanceViewItem,
    navController: NavController,
    uiState: TokenBalanceModule.TokenBalanceUiState,
    secondaryValue: DeemedValue<String>,
    onStackingClicked: () -> Unit,
    onClickSubtitle: () -> Unit,
    onToggleBalanceVisibility: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onShieldClick: () -> Unit,
    onSyncErrorClick: (BalanceViewItem) -> Unit,
    onDismissNetworkFeeWarning: () -> Unit,
    isShowShieldFunds: Boolean
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // Sub-header row: coin icon + ticker + badge + staking status
        VSpacer(height = 12.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CoinIconWithSyncProgress(
                    token = balanceViewItem.wallet.token,
                    syncingProgress = balanceViewItem.syncingProgress,
                    failedIconVisible = balanceViewItem.failedIconVisible,
                    onClickSyncError = {
                        onSyncErrorClick(balanceViewItem)
                    }
                )
            }
            HSpacer(16.dp)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.coinCode,
                    color = ComposeAppTheme.colors.grey,
                    style = ComposeAppTheme.typography.subhead1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                uiState.badge?.let { badgeText ->
                    HSpacer(6.dp)
                    Badge(text = badgeText)
                }
            }
            uiState.stakingStatus?.let { status ->
                HSpacer(8.dp)
                StakingStatusBadge(status = status)
            }
        }

        // Balance
        VSpacer(height = 22.dp)
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        onToggleBalanceVisibility()
                        HudHelper.vibrate(context)
                    }
                ),
            text = if (balanceViewItem.primaryValue.visible) balanceViewItem.primaryValue.value else "*****",
            color = if (balanceViewItem.primaryValue.dimmed) ComposeAppTheme.colors.grey else ComposeAppTheme.colors.leah,
            style = ComposeAppTheme.typography.title2R,
            textAlign = TextAlign.Start,
        )

        // Price line
        VSpacer(height = 6.dp)
        if (balanceViewItem.syncingTextValue != null) {
            body_grey(
                text = balanceViewItem.syncingTextValue + (balanceViewItem.syncedUntilTextValue?.let { " - $it" }
                    ?: ""),
                maxLines = 1,
            )
        } else {
            Text(
                text = if (balanceViewItem.secondaryValue.visible) secondaryValue.value else "*****",
                color = if (balanceViewItem.secondaryValue.dimmed) ComposeAppTheme.colors.grey50 else ComposeAppTheme.colors.grey,
                style = ComposeAppTheme.typography.body,
                maxLines = 1,
                modifier = Modifier.clickable(
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

        // Exchange rate + diff
        if (balanceViewItem.exchangeValue.visible) {
            VSpacer(height = 4.dp)
            Row {
                Text(
                    text = "1${uiState.coinCode} = ${balanceViewItem.exchangeValue.value}",
                    color = ComposeAppTheme.colors.grey,
                    style = ComposeAppTheme.typography.subhead2,
                )
                if (balanceViewItem.displayDiffOptionType != DisplayDiffOptionType.NONE) {
                    balanceViewItem.fullDiff.takeIf { it.isNotBlank() }?.let { fullDiff ->
                        val color = diffColor(balanceViewItem.diff)
                        HSpacer(width = 6.dp)
                        BadgeText(
                            text = fullDiff,
                            background = color.copy(alpha = 0.1f),
                            textColor = color
                        )
                    }
                }
            }
        }

        // Staking unpaid row (with info tooltip) + optional "next accrual" subtitle
        var showInfoSheet by rememberSaveable { mutableStateOf(false) }
        uiState.stackingType?.let { stackingType ->
            VSpacer(height = 21.dp)
            HorizontalDivider(color = ComposeAppTheme.colors.steel20, thickness = 1.dp)

            VSpacer(height = 12.dp)
            RowUniversal(verticalPadding = 0.dp) {
                subhead2_grey(
                    text = stringResource(R.string.staking_unpaid),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                HSpacer(4.dp)
                HsIconButton(
                    onClick = { showInfoSheet = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info_20),
                        contentDescription = stringResource(R.string.staking_unpaid_info_title),
                        tint = ComposeAppTheme.colors.grey
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                uiState.stakingUnpaid?.let { unpaid ->
                    Text(
                        text = if (balanceViewItem.primaryValue.visible) unpaid else "*****",
                        color = if (balanceViewItem.primaryValue.dimmed) ComposeAppTheme.colors.grey50 else ComposeAppTheme.colors.leah,
                        style = ComposeAppTheme.typography.subhead2,
                        maxLines = 1,
                    )
                } ?: Text(
                    text = "—",
                    color = ComposeAppTheme.colors.grey50,
                    style = ComposeAppTheme.typography.subhead2,
                )
            }

            val nextAccrualHours = uiState.hoursUntilNextAccrual
            AnimatedVisibility(
                visible = nextAccrualHours != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val hours = nextAccrualHours ?: return@AnimatedVisibility
                subhead2_jacob(
                    text = pluralStringResource(
                        R.plurals.staking_next_accrual_in_hours,
                        hours,
                        hours
                    )
                )
            }

            if (showInfoSheet) {
                val bodyRes = when (stackingType) {
                    StackingType.PCASH -> R.string.staking_unpaid_info_body_pirate
                    StackingType.COSANTA -> R.string.staking_unpaid_info_body_cosanta
                }
                InfoBottomSheet(
                    title = stringResource(R.string.staking_unpaid_info_title),
                    text = stringResource(bodyRes),
                    onDismiss = { showInfoSheet = false }
                )
            }
        }

        VSpacer(height = 12.dp)
        ButtonsRow(
            viewItem = balanceViewItem,
            navController = navController,
            sendEnabled = uiState.sendEntryEnabled,
            onSendClick = onSendClick,
            onReceiveClick = onReceiveClick,
            onShieldClick = onShieldClick,
            onStackingClicked = onStackingClicked,
            isShowShieldFunds = isShowShieldFunds
        )
        uiState.zcashMigrationRequiredAmount?.let { amount ->
            ZcashMigrationRequiredSection(
                amount = amount,
                amountVisible = balanceViewItem.primaryValue.visible,
                wallet = balanceViewItem.wallet
            )
        }
        LockedBalanceSection(balanceViewItem)
        balanceViewItem.warning?.let {
            VSpacer(height = 8.dp)
            TextImportantWarning(
                icon = R.drawable.ic_attention_20,
                title = it.title.getString(),
                text = it.text.getString()
            )
        }
        uiState.networkFeeWarning?.let { warningData ->
            VSpacer(height = 8.dp)
            val bodyText = buildAnnotatedString {
                val balanceStart = warningData.body.indexOf(warningData.formattedBalance)
                if (balanceStart >= 0) {
                    append(warningData.body.substring(0, balanceStart))
                    withStyle(SpanStyle(color = ComposeAppTheme.colors.jacob)) {
                        append(warningData.formattedBalance)
                    }
                    append(warningData.body.substring(balanceStart + warningData.formattedBalance.length))
                } else {
                    append(warningData.body)
                }
            }
            TextImportantWarning(
                icon = R.drawable.ic_attention_20,
                title = warningData.title,
                text = bodyText,
                onClose = onDismissNetworkFeeWarning
            )
        }
        VSpacer(height = 16.dp)
    }
}

@Composable
private fun ZcashMigrationRequiredSection(
    amount: String,
    amountVisible: Boolean,
    wallet: Wallet,
) {
    var migrating by remember { mutableStateOf(false) }

    VSpacer(height = 8.dp)
    RowUniversal(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ComposeAppTheme.colors.jacob, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        onClick = { migrating = true }
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_attention_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.jacob
        )
        HSpacer(8.dp)
        subhead2_jacob(
            text = stringResource(R.string.balance_zcash_migration_required),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        subhead2_jacob(
            modifier = Modifier.padding(start = 6.dp),
            text = if (amountVisible) amount else "*****",
            maxLines = 1
        )
    }

    if (migrating) {
        ZcashMigrationFlow(wallet = wallet, onClose = { migrating = false })
    }
}

@Composable
private fun LockedBalanceSection(balanceViewItem: BalanceViewItem) {
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
) {
    var showInfoDialog by remember { mutableStateOf(false) }

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
            onClick = { showInfoDialog = true }
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

    if (showInfoDialog) {
        InfoBottomSheet(
            title = infoTitle,
            text = infoText,
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
private fun TokenNotSyncedSection(
    onBlockchainStatusClick: () -> Unit,
    onRetry: () -> Unit,
) {
    Column {
        BlockchainStatusButton(onClick = onBlockchainStatusClick)
        VSpacer(12.dp)
        TextImportant(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = stringResource(R.string.token_not_synced_title),
            icon = R.drawable.ic_attention_24,
            borderColor = ComposeAppTheme.colors.steel20,
            backgroundColor = ComposeAppTheme.colors.lawrence,
            textColor = ComposeAppTheme.colors.leah,
            iconColor = ComposeAppTheme.colors.grey,
        ) {
            subhead2_grey(text = stringResource(R.string.token_not_synced_description))
            ButtonSecondary(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRetry,
                border = BorderStroke(1.dp, ComposeAppTheme.colors.steel20),
                buttonColors = SecondaryButtonDefaults.buttonColors(
                    backgroundColor = ComposeAppTheme.colors.transparent,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                content = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(R.drawable.ic_refresh_20),
                            contentDescription = null,
                            tint = ComposeAppTheme.colors.grey
                        )
                        HSpacer(8.dp)
                        subhead1_leah(text = stringResource(R.string.token_not_synced_retry))
                    }
                }
            )
        }
    }
}

@Preview
@Composable
private fun TokenNotSyncedSectionPreview() {
    ComposeAppTheme {
        TokenNotSyncedSection(
            onBlockchainStatusClick = {},
            onRetry = {},
        )
    }
}

// Never auto-open the wallet sync-error sheet while the app is locked: it lives in its
// own Window and would leak wallet UI above the calculator/PIN disguise (deanonymization).
internal fun shouldAutoShowSyncError(failedIconVisible: Boolean, appLocked: Boolean): Boolean =
    failedIconVisible && !appLocked

private fun onSyncErrorClicked(
    viewItem: BalanceViewItem,
    viewModel: TokenBalanceViewModel,
    navController: NavController
) {
    when (val syncErrorDetails = viewModel.getSyncErrorDetails(viewItem)) {
        is BalanceViewModel.SyncError.Dialog -> {
            val wallet = syncErrorDetails.wallet
            val errorMessage = syncErrorDetails.errorMessage

            navController.showSyncErrorDialog(wallet, errorMessage)
        }

        is BalanceViewModel.SyncError.NetworkNotAvailable -> Unit // We already show this at bottom panel
    }
}

private fun onReceiveClicked(
    viewModel: TokenBalanceViewModel,
    navController: NavController
) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoneroSendPreparationBottomSheet(
    syncInProgress: Boolean,
    @StringRes error: Int?,
    fullWalletRecoveryAvailable: Boolean,
    onSync: () -> Unit,
    onFullWalletRecovery: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.ic_attention_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            title = stringResource(R.string.monero_prepare_trezor_title),
            onCloseClick = onDismiss,
        ) {
            MoneroSendPreparationContent(
                syncInProgress = syncInProgress,
                error = error,
                fullWalletRecoveryAvailable = fullWalletRecoveryAvailable,
                onSync = onSync,
                onFullWalletRecovery = onFullWalletRecovery,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun MoneroSendPreparationContent(
    syncInProgress: Boolean,
    @StringRes error: Int?,
    fullWalletRecoveryAvailable: Boolean,
    onSync: () -> Unit,
    onFullWalletRecovery: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        body_leah(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = stringResource(R.string.monero_prepare_trezor_description),
        )
        error?.let {
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                text = stringResource(it),
            )
        }
        MoneroSendPreparationActions(
            syncInProgress = syncInProgress,
            fullWalletRecoveryAvailable = fullWalletRecoveryAvailable,
            onSync = onSync,
            onFullWalletRecovery = onFullWalletRecovery,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun MoneroSendPreparationActions(
    syncInProgress: Boolean,
    fullWalletRecoveryAvailable: Boolean,
    onSync: () -> Unit,
    onFullWalletRecovery: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actionUiState = moneroPreparationActionUiState(syncInProgress)
    Column {
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            title = stringResource(actionUiState.title),
            onClick = onSync,
            enabled = actionUiState.enabled,
            loadingIndicator = actionUiState.loading,
        )
        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            title = stringResource(R.string.Button_Cancel),
            onClick = onDismiss,
        )
        if (fullWalletRecoveryAvailable) {
            body_leah(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                text = stringResource(R.string.monero_full_wallet_recovery_warning),
            )
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.monero_full_wallet_recovery),
                onClick = onFullWalletRecovery,
            )
        }
        VSpacer(32.dp)
    }
}

internal data class MoneroPreparationActionUiState(
    @StringRes val title: Int,
    val enabled: Boolean,
    val loading: Boolean,
)

internal fun moneroPreparationActionUiState(
    syncInProgress: Boolean,
): MoneroPreparationActionUiState = MoneroPreparationActionUiState(
    title = if (syncInProgress) R.string.monero_updating_with_trezor else R.string.Button_Retry,
    enabled = !syncInProgress,
    loading = syncInProgress,
)

@Preview
@Composable
private fun MoneroSendPreparationBottomSheetPreview() {
    ComposeAppTheme {
        MoneroSendPreparationBottomSheet(
            syncInProgress = false,
            error = null,
            fullWalletRecoveryAvailable = false,
            onSync = {},
            onFullWalletRecovery = {},
            onDismiss = {},
        )
    }
}


@Composable
private fun ButtonsRow(
    viewItem: BalanceViewItem,
    navController: NavController,
    sendEnabled: Boolean,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onShieldClick: () -> Unit,
    onStackingClicked: () -> Unit,
    isShowShieldFunds: Boolean
) {
    Row(
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
    ) {
        if (viewItem.isWatchAccount) {
            ButtonPrimaryDefault(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.Balance_Address),
                onClick = onReceiveClick,
            )
            if (viewItem.wallet.isStakingWallet()) {
                HSpacer(8.dp)
                ButtonPrimaryCircle(
                    icon = R.drawable.ic_coins_stacking,
                    contentDescription = stringResource(R.string.stacking),
                    onClick = {
                        onStackingClicked()
                    },
                    iconTint = Color.Black,
                    background = Color.White,
                )
            }
        } else {
            if (!viewItem.isSendDisabled) {
                ButtonPrimaryYellow(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.Balance_Send),
                    onClick = onSendClick,
                    enabled = sendEnabled,
                )
                HSpacer(8.dp)
            }
            if (!viewItem.swapVisible) {
                ButtonPrimaryDefault(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.Balance_Receive),
                    onClick = onReceiveClick,
                )
            } else {
                ButtonPrimaryCircle(
                    icon = R.drawable.ic_arrow_down_left_24,
                    contentDescription = stringResource(R.string.Balance_Receive),
                    onClick = onReceiveClick,
                    iconTint = Color.Black,
                    background = Color.White,
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
                    enabled = viewItem.swapEnabled,
                    iconTint = Color.Black,
                    background = Color.White,
                )
            }
            if (viewItem.wallet.isStakingWallet()) {
                HSpacer(8.dp)
                ButtonPrimaryCircle(
                    icon = R.drawable.ic_coins_stacking,
                    contentDescription = stringResource(R.string.stacking),
                    onClick = {
                        onStackingClicked()
                    },
                    iconTint = Color.Black,
                    background = Color.White,
                )
            }
        }
    }
    if (isShowShieldFunds) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.shield_funds),
                onClick = onShieldClick
            )
            body_grey(
                text = stringResource(R.string.typical_fee),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun StakingStatusBadge(status: TokenBalanceModule.StakingStatus) {
    val (textRes, color) = when (status) {
        TokenBalanceModule.StakingStatus.ACTIVE ->
            R.string.staking_active to ComposeAppTheme.colors.remus

        TokenBalanceModule.StakingStatus.INACTIVE ->
            R.string.staking_inactive to ComposeAppTheme.colors.lucian
    }
    val text = stringResource(textRes)
    val statusCd = stringResource(R.string.staking_status_cd, text)
    BadgeText(
        modifier = Modifier.semantics { contentDescription = statusCd },
        text = text,
        background = color.copy(alpha = 0.1f),
        textColor = color
    )
}

@Preview(name = "With tabs")
@Composable
internal fun TokenBalanceScreenContentPreview() {
    PreviewTokenBalanceScreenContent(previewTokenBalanceUiState())
}

@Preview(name = "Without tabs")
@Composable
internal fun TokenBalanceScreenContentNoTabsPreview() {
    PreviewTokenBalanceScreenContent(
        previewTokenBalanceUiState(transactionFiltersEnabled = false)
    )
}

@Preview(name = "Search, with tabs")
@Composable
internal fun TokenBalanceScreenContentSearchWithTabsPreview() {
    PreviewTokenBalanceScreenContent(
        previewTokenBalanceUiState(searchActive = true)
    )
}

@Preview(name = "Search, without tabs")
@Composable
internal fun TokenBalanceScreenContentSearchNoTabsPreview() {
    PreviewTokenBalanceScreenContent(
        previewTokenBalanceUiState(searchActive = true, transactionFiltersEnabled = false)
    )
}

@Composable
private fun PreviewTokenBalanceScreenContent(
    uiState: TokenBalanceModule.TokenBalanceUiState
) {
    ComposeAppTheme(darkTheme = true) {
        TokenBalanceScreenContent(
            uiState = uiState,
            secondaryValue = DeemedValue("$1,234.56"),
            sendResult = null,
            navController = rememberNavController(),
            refreshing = false,
            onToggleFavorite = {},
            onToggleBalanceVisibility = {},
            onSearchClick = {},
            onSearchClose = {},
            onSearchQueryChange = {},
            onSetTransactionType = {},
            onWillShow = {},
            onTransactionClick = {},
            onSensitiveTransactionClick = {},
            onBottomReached = {},
            onSetAmlCheckEnabled = {},
            onDismissAmlPromo = {},
            onDismissNetworkFeeWarning = {},
            onSendClick = {},
            onReceiveClick = {},
            onShieldClick = {},
            onSyncErrorClick = {},
            onStackingClicked = {},
            onShowAllTransactionsClicked = {},
            onClickSubtitle = {},
            onRefresh = {},
            onSettingsClick = {},
        )
    }
}

private fun previewTokenBalanceUiState(
    transactionFiltersEnabled: Boolean = true,
    searchActive: Boolean = false,
) = TokenBalanceModule.TokenBalanceUiState(
    title = "Preview Coin",
    coinCode = "PCN",
    balanceViewItem = previewBalanceViewItem(),
    transactions = emptyMap(),
    hasHiddenTransactions = false,
    isFavorite = true,
    syncing = false,
    transactionFiltersEnabled = transactionFiltersEnabled,
    transactionFilterTypes = listOf(
        Filter(FilterTransactionType.All, selected = true),
        Filter(FilterTransactionType.Incoming, selected = false),
        Filter(FilterTransactionType.Outgoing, selected = false),
        Filter(FilterTransactionType.Swap, selected = false),
    ),
    searchActive = searchActive,
    searchQuery = if (searchActive) "0x1f9840" else "",
    searchEmptyResult = searchActive,
)

private fun previewBalanceViewItem() = BalanceViewItem(
    wallet = WalletFactory.previewWallet(),
    primaryValue = DeemedValue("1.2345 PCN"),
    exchangeValue = DeemedValue("$1,000.00", visible = false),
    secondaryValue = DeemedValue("$1,234.56"),
    lockedValues = emptyList(),
    sendEnabled = true,
    syncingProgress = SyncingProgress(null, null),
    syncingTextValue = null,
    syncedUntilTextValue = null,
    failedIconVisible = false,
    coinIconVisible = true,
    badge = null,
    swapVisible = true,
    swapEnabled = true,
    errorMessage = null,
    isWatchAccount = false,
    isSendDisabled = false,
    isShowShieldFunds = false,
    warning = null,
    displayDiffOptionType = DisplayDiffOptionType.NONE,
)
