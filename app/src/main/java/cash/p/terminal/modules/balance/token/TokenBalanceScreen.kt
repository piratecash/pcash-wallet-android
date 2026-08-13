@file:OptIn(ExperimentalFoundationApi::class)

package cash.p.terminal.modules.balance.token

import android.view.View
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.rememberUpdatedState
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
import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.modules.balance.BalanceViewItem
import cash.p.terminal.modules.balance.SyncingProgress
import cash.p.terminal.modules.balance.ui.FlipHiddenBalanceInfoHost
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusButton
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.SendFragment
import cash.p.terminal.modules.transactions.AmlCheckInfoBottomSheet
import cash.p.terminal.modules.transactions.AmlCheckPromoBanner
import cash.p.terminal.modules.transactions.Filter
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.FilterTypeTabs
import cash.p.terminal.modules.transactions.SearchEmptyResultsView
import cash.p.terminal.modules.transactions.SearchInProgressView
import cash.p.terminal.modules.transactions.TransactionSearchField
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.transactionList
import cash.p.terminal.modules.transactions.transactionsHiddenBlock
import cash.p.terminal.navigation.entity.SwapParams
import cash.p.terminal.modules.zcashmigration.ZcashMigrationFlow
import cash.p.terminal.navigation.popBackStackSafely
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
import kotlinx.coroutines.flow.Flow

private const val HEADER_CONTENT_TYPE = "token_balance_sticky_header"
private const val PLACEHOLDER_CONTENT_TYPE = "token_balance_empty_placeholder"

// Distinct type for the sticky header's Lazy key so it can never collide with the
// transaction rows' String uid keys (an enum never equals a String).
private enum class TokenBalanceLazyKey { SearchHeader }

@Composable
fun TokenBalanceScreen(
    params: TokenBalanceScreenParams,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var showMoneroSendPreparation by rememberSaveable { mutableStateOf(false) }
    TokenBalanceScreenEffects(
        events = params.events,
        navController = params.navController,
        showMoneroSendPreparation = showMoneroSendPreparation,
        onDismissMoneroSendPreparation = { showMoneroSendPreparation = false },
        moneroSpendReadiness = params.moneroSpendReadiness,
    )

    Box(modifier = modifier) {
        TokenBalanceScreenContentHost(
            params = params,
            view = view,
            onShowMoneroSendPreparation = { showMoneroSendPreparation = true },
        )
        MoneroSendPreparationBottomSheetHost(
            MoneroSendPreparationBottomSheetParams(
                visible = showMoneroSendPreparation,
                syncInProgress = params.moneroKeyImageSyncInProgress,
                error = params.moneroKeyImageSyncError,
                fullWalletRecoveryAvailable = params.moneroFullWalletRecoveryAvailable,
                onSync = params.actions.onSyncMoneroKeyImages,
                onFullWalletRecovery = params.actions.onFullMoneroWalletRecovery,
                onCancel = params.actions.onCancelMoneroKeyImageSync,
                onDismiss = { showMoneroSendPreparation = false },
            ),
        )
    }
}

@Composable
private fun TokenBalanceScreenContentHost(
    params: TokenBalanceScreenParams,
    view: View,
    onShowMoneroSendPreparation: () -> Unit,
) {
    tokenBalanceScreenContent(
        TokenBalanceScreenContentParams(
            uiState = params.uiState,
            secondaryValue = params.secondaryValue,
            sendResult = params.sendResult,
            navController = params.navController,
            refreshing = params.refreshing,
            onToggleFavorite = params.actions.onToggleFavorite,
            onToggleBalanceVisibility = params.actions.onToggleBalanceVisibility,
            onSearchClick = params.actions.onSearchClick,
            onSearchClose = params.actions.onSearchClose,
            onSearchQueryChange = params.actions.onSearchQueryChange,
            onSetTransactionType = params.actions.onSetTransactionType,
            onWillShow = params.actions.onWillShow,
            onTransactionClick = params.actions.onTransactionClick,
            onSensitiveTransactionClick = params.actions.onSensitiveTransactionClick,
            onBottomReached = params.actions.onBottomReached,
            onSetAmlCheckEnabled = params.actions.onSetAmlCheckEnabled,
            onDismissAmlPromo = { params.actions.onDismissAmlPromo(view) },
            onDismissNetworkFeeWarning = params.actions.onDismissNetworkFeeWarning,
            onSendClick = { params.actions.onSendClick(onShowMoneroSendPreparation) },
            onReceiveClick = params.actions.onReceiveClick,
            onShieldClick = params.actions.onShieldClick,
            onSyncErrorClick = params.actions.onSyncErrorClick,
            onStackingClicked = params.actions.onStackingClicked,
            onShowAllTransactionsClicked = params.actions.onShowAllTransactionsClicked,
            onClickSubtitle = params.actions.onClickSubtitle,
            onRefresh = params.actions.onRefresh,
            onSettingsClick = params.actions.onSettingsClick,
        )
    )
}

data class TokenBalanceScreenParams(
    val uiState: TokenBalanceModule.TokenBalanceUiState,
    val secondaryValue: DeemedValue<String>,
    val events: Flow<TokenBalanceModule.Event>,
    val navController: NavController,
    val refreshing: Boolean,
    val sendResult: SendResult?,
    val moneroSpendReadiness: MoneroSpendReadiness?,
    val moneroKeyImageSyncInProgress: Boolean,
    @StringRes val moneroKeyImageSyncError: Int?,
    val moneroFullWalletRecoveryAvailable: Boolean,
    val actions: TokenBalanceScreenActions,
)

data class TokenBalanceScreenActions(
    val onToggleFavorite: () -> Unit,
    val onToggleBalanceVisibility: () -> Unit,
    val onSearchClick: () -> Unit,
    val onSearchClose: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onSetTransactionType: (FilterTransactionType) -> Unit,
    val onWillShow: (TransactionViewItem) -> Unit,
    val onTransactionClick: (TransactionViewItem) -> Unit,
    val onSensitiveTransactionClick: (TransactionViewItem) -> Unit,
    val onBottomReached: () -> Unit,
    val onSetAmlCheckEnabled: (Boolean) -> Unit,
    val onDismissAmlPromo: (View) -> Unit,
    val onDismissNetworkFeeWarning: () -> Unit,
    val onSendClick: ((() -> Unit)) -> Unit,
    val onReceiveClick: () -> Unit,
    val onShieldClick: () -> Unit,
    val onSyncErrorClick: (BalanceViewItem) -> Unit,
    val onSyncMoneroKeyImages: () -> Unit,
    val onFullMoneroWalletRecovery: () -> Unit,
    val onCancelMoneroKeyImageSync: () -> Unit,
    val onStackingClicked: () -> Unit,
    val onShowAllTransactionsClicked: () -> Unit,
    val onClickSubtitle: () -> Unit,
    val onRefresh: () -> Unit,
    val onSettingsClick: () -> Unit,
)

@Composable
private fun TokenBalanceScreenEffects(
    events: Flow<TokenBalanceModule.Event>,
    navController: NavController,
    showMoneroSendPreparation: Boolean,
    onDismissMoneroSendPreparation: () -> Unit,
    moneroSpendReadiness: MoneroSpendReadiness?,
) {
    val dismissMoneroSendPreparation by rememberUpdatedState(onDismissMoneroSendPreparation)
    LaunchedEffect(events, navController) {
        events.collect { event ->
            if (event is TokenBalanceModule.Event.OpenSend) {
                dismissMoneroSendPreparation()
                navController.openSend(event.wallet)
            }
        }
    }
    LaunchedEffect(showMoneroSendPreparation, moneroSpendReadiness) {
        if (showMoneroSendPreparation &&
            moneroSpendReadiness == MoneroSpendReadiness.ReconcilingSpentStatus
        ) {
            dismissMoneroSendPreparation()
        }
    }
}

internal fun NavController.openSend(wallet: Wallet) {
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
private fun MoneroSendPreparationBottomSheetHost(
    params: MoneroSendPreparationBottomSheetParams,
) {
    if (!params.visible) return
    MoneroSendPreparationBottomSheet(
        syncInProgress = params.syncInProgress,
        error = params.error,
        fullWalletRecoveryAvailable = params.fullWalletRecoveryAvailable,
        onSync = params.onSync,
        onFullWalletRecovery = params.onFullWalletRecovery,
        onDismiss = {
            params.onCancel()
            params.onDismiss()
        },
    )
}

private data class MoneroSendPreparationBottomSheetParams(
    val visible: Boolean,
    val syncInProgress: Boolean,
    @StringRes val error: Int?,
    val fullWalletRecoveryAvailable: Boolean,
    val onSync: () -> Unit,
    val onFullWalletRecovery: () -> Unit,
    val onCancel: () -> Unit,
    val onDismiss: () -> Unit,
)

private val tokenBalanceScreenContent: @Composable (TokenBalanceScreenContentParams) -> Unit = { params ->
    val uiState = params.uiState
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
            params.onSyncErrorClick(viewItem)
        }
    }

    Box {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = uiState.title,
                navigationIcon = {
                    HsBackButton(onClick = { params.navController.popBackStackSafely() })
                },
                menuItems = buildList {
                    add(
                        MenuItem(
                            title = TranslatableString.ResString(
                                if (uiState.isFavorite) {
                                    R.string.CoinPage_Unfavorite
                                } else {
                                    R.string.CoinPage_Favorite
                                }
                            ),
                            icon = if (uiState.isFavorite) R.drawable.ic_star_filled_20 else R.drawable.ic_star_20,
                            tint = if (uiState.isFavorite) {
                                ComposeAppTheme.colors.jacob
                            } else {
                                ComposeAppTheme.colors.grey
                            },
                            onClick = params.onToggleFavorite
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
                                    params.navController.slideFromRight(R.id.coinFragment, arguments)
                                }
                            )
                        )
                    }
                    add(
                        MenuItem(
                            title = TranslatableString.ResString(R.string.Settings_Title),
                            icon = R.drawable.ic_manage_2_24,
                            onClick = params.onSettingsClick
                        )
                    )
                    if (failedIconVisible && !loading) {
                        add(
                            MenuItem(
                                title = TranslatableString.ResString(R.string.BalanceSyncError_Title),
                                icon = R.drawable.ic_attention_red_24,
                                tint = ComposeAppTheme.colors.lucian,
                                onClick = {
                                    uiState.balanceViewItem?.let(params.onSyncErrorClick)
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
        when (params.sendResult) {
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
                    params.sendResult.caution.getDescription() ?: params.sendResult.caution.getString()
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
            refreshing = params.refreshing,
            modifier = Modifier.padding(paddingValues),
            onRefresh = params.onRefresh
        ) {
            Box {
                // Overscroll is disabled: the stretch effect moves the pinned header (inside the
                // list) but not the date overlay (drawn outside it), opening a gap between them.
                LazyColumn(state = listState, overscrollEffect = null) {
                    item {
                        uiState.balanceViewItem?.let {
                            TokenBalanceHeader(
                                TokenBalanceHeaderParams(
                                balanceViewItem = it,
                                navController = params.navController,
                                uiState = uiState,
                                secondaryValue = params.secondaryValue,
                                onStackingClicked = params.onStackingClicked,
                                onClickSubtitle = params.onClickSubtitle,
                                onToggleBalanceVisibility = params.onToggleBalanceVisibility,
                                onSendClick = params.onSendClick,
                                onReceiveClick = params.onReceiveClick,
                                onShieldClick = params.onShieldClick,
                                onSyncErrorClick = params.onSyncErrorClick,
                                onDismissNetworkFeeWarning = params.onDismissNetworkFeeWarning,
                                isShowShieldFunds = uiState.isShowShieldFunds,
                                )
                            )
                        }
                    }

                    if (failedIconVisible) {
                        item {
                            TokenNotSyncedSection(
                                onBlockchainStatusClick = {
                                    uiState.balanceViewItem?.wallet?.token?.blockchain?.let { blockchain ->
                                        params.navController.slideFromRight(
                                            R.id.blockchainStatusFragment,
                                            blockchain
                                        )
                                    }
                                },
                                onRetry = params.onRefresh,
                            )
                        }
                    }

                    if (uiState.showAmlPromo) {
                        item {
                            AmlCheckPromoBanner(
                                amlCheckEnabled = uiState.amlCheckEnabled,
                                onToggleChange = params.onSetAmlCheckEnabled,
                                onInfoClick = { showAmlInfoSheet = true },
                                onClose = params.onDismissAmlPromo,
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
                                    onTransactionTypeClick = params.onSetTransactionType,
                                )
                            }
                            if (uiState.searchActive) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HsBackButton(onClick = params.onSearchClose)
                                    TransactionSearchField(
                                        query = uiState.searchQuery,
                                        onQueryChange = params.onSearchQueryChange,
                                    )
                                }
                            } else {
                                HideBalanceSearchRow(
                                    hideBalance = !uiState.balanceViewItem.primaryValue.visible,
                                    onToggleBalanceVisibility = params.onToggleBalanceVisibility,
                                    onSearchClick = params.onSearchClick,
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
                            willShow = params.onWillShow,
                            onClick = params.onTransactionClick,
                            isItemBalanceHidden = { !it.showAmount },
                            onSensitiveValueClick = params.onSensitiveTransactionClick,
                            onBottomReached = params.onBottomReached,
                            stickyDateHeaders = false
                        )
                        if (uiState.hasHiddenTransactions) {
                            transactionsHiddenBlock(
                                shortBlock = transactionItems.isNotEmpty(),
                                onShowAllTransactionsClicked = params.onShowAllTransactionsClicked
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
                params.navController.slideFromRight(
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
}

private data class TokenBalanceScreenContentParams(
    val uiState: TokenBalanceModule.TokenBalanceUiState,
    val secondaryValue: DeemedValue<String>,
    val sendResult: SendResult?,
    val navController: NavController,
    val refreshing: Boolean,
    val onToggleFavorite: () -> Unit,
    val onToggleBalanceVisibility: () -> Unit,
    val onSearchClick: () -> Unit,
    val onSearchClose: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onSetTransactionType: (FilterTransactionType) -> Unit,
    val onWillShow: (TransactionViewItem) -> Unit,
    val onTransactionClick: (TransactionViewItem) -> Unit,
    val onSensitiveTransactionClick: (TransactionViewItem) -> Unit,
    val onBottomReached: () -> Unit,
    val onSetAmlCheckEnabled: (Boolean) -> Unit,
    val onDismissAmlPromo: () -> Unit,
    val onDismissNetworkFeeWarning: () -> Unit,
    val onSendClick: () -> Unit,
    val onReceiveClick: () -> Unit,
    val onShieldClick: () -> Unit,
    val onSyncErrorClick: (BalanceViewItem) -> Unit,
    val onStackingClicked: () -> Unit,
    val onShowAllTransactionsClicked: () -> Unit,
    val onClickSubtitle: () -> Unit,
    val onRefresh: () -> Unit,
    val onSettingsClick: () -> Unit,
)


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

@Composable
private fun TokenBalanceHeader(
    params: TokenBalanceHeaderParams,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        TokenBalanceAssetIdentity(params)
        TokenBalanceValues(params)
        TokenBalanceStakingDetails(params)
        VSpacer(height = 12.dp)
        ButtonsRow(
            TokenBalanceButtonsParams(
                params.balanceViewItem, params.navController, params.uiState.sendEntryEnabled,
                params.onSendClick, params.onReceiveClick, params.onShieldClick,
                params.onStackingClicked, params.isShowShieldFunds,
            )
        )
        TokenBalanceHeaderAlerts(params)
        VSpacer(height = 16.dp)
    }
}

@Composable
private fun TokenBalanceAssetIdentity(params: TokenBalanceHeaderParams) {
    val item = params.balanceViewItem
    VSpacer(height = 12.dp)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            CoinIconWithSyncProgress(
                token = item.wallet.token,
                syncingProgress = item.syncingProgress,
                failedIconVisible = item.failedIconVisible,
                onClickSyncError = { params.onSyncErrorClick(item) },
            )
        }
        HSpacer(16.dp)
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = params.uiState.coinCode,
                color = ComposeAppTheme.colors.grey,
                style = ComposeAppTheme.typography.subhead1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            params.uiState.badge?.let { HSpacer(6.dp); Badge(text = it) }
        }
        params.uiState.stakingStatus?.let { HSpacer(8.dp); StakingStatusBadge(status = it) }
    }
}

@Composable
private fun TokenBalanceValues(params: TokenBalanceHeaderParams) {
    val item = params.balanceViewItem
    val context = LocalContext.current
    Column {
        VSpacer(height = 22.dp)
        Text(
            modifier = Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) {
                params.onToggleBalanceVisibility(); HudHelper.vibrate(context)
            }, text = if (item.primaryValue.visible) item.primaryValue.value else "*****",
            color = if (item.primaryValue.dimmed) ComposeAppTheme.colors.grey else ComposeAppTheme.colors.leah,
            style = ComposeAppTheme.typography.title2R, textAlign = TextAlign.Start,
        )
        TokenBalanceSecondaryValue(params)
        TokenBalanceExchangeValue(params)
    }
}

@Composable
private fun TokenBalanceSecondaryValue(params: TokenBalanceHeaderParams) {
    val item = params.balanceViewItem
    VSpacer(height = 6.dp)
    if (item.syncingTextValue != null) {
        body_grey(text = item.syncingTextValue + (item.syncedUntilTextValue?.let { " - $it" } ?: ""), maxLines = 1)
        return
    }
    Text(
        text = if (item.secondaryValue.visible) params.secondaryValue.value else "*****",
        color = if (item.secondaryValue.dimmed) ComposeAppTheme.colors.grey50 else ComposeAppTheme.colors.grey,
        style = ComposeAppTheme.typography.body, maxLines = 1,
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
            if (item.secondaryValue.visible) params.onClickSubtitle()
        },
    )
}

@Composable
private fun TokenBalanceExchangeValue(params: TokenBalanceHeaderParams) {
    val item = params.balanceViewItem
    if (!item.exchangeValue.visible) return
    VSpacer(height = 4.dp)
    Row {
        Text(
            text = "1${params.uiState.coinCode} = ${item.exchangeValue.value}",
            color = ComposeAppTheme.colors.grey,
            style = ComposeAppTheme.typography.subhead2,
        )
        item.fullDiff.takeIf { item.displayDiffOptionType != DisplayDiffOptionType.NONE && it.isNotBlank() }?.let {
            val color = diffColor(item.diff)
            HSpacer(6.dp)
            BadgeText(text = it, background = color.copy(alpha = 0.1f), textColor = color)
        }
    }
}

@Composable
private fun TokenBalanceStakingDetails(params: TokenBalanceHeaderParams) {
    val stackingType = params.uiState.stackingType ?: return
    var showInfoSheet by rememberSaveable { mutableStateOf(false) }
    VSpacer(height = 21.dp)
    HorizontalDivider(color = ComposeAppTheme.colors.steel20, thickness = 1.dp)
    TokenBalanceUnpaidRow(params, onInfoClick = { showInfoSheet = true })
    TokenBalanceNextAccrual(params.uiState.hoursUntilNextAccrual)
    if (showInfoSheet) StakingUnpaidInfoSheet(stackingType) { showInfoSheet = false }
}

@Composable
private fun TokenBalanceUnpaidRow(params: TokenBalanceHeaderParams, onInfoClick: () -> Unit) {
    val item = params.balanceViewItem
    VSpacer(height = 12.dp)
    RowUniversal(verticalPadding = 0.dp) {
        subhead2_grey(stringResource(R.string.staking_unpaid), maxLines = 1, overflow = TextOverflow.Ellipsis)
        HSpacer(4.dp)
        HsIconButton(onClick = onInfoClick, modifier = Modifier.size(20.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_info_20),
                contentDescription = stringResource(R.string.staking_unpaid_info_title),
                tint = ComposeAppTheme.colors.grey,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = params.uiState.stakingUnpaid?.let { if (item.primaryValue.visible) it else "*****" } ?: "—",
            color = if (params.uiState.stakingUnpaid == null || item.primaryValue.dimmed) {
                ComposeAppTheme.colors.grey50
            } else {
                ComposeAppTheme.colors.leah
            },
            style = ComposeAppTheme.typography.subhead2, maxLines = 1,
        )
    }
}

@Composable
private fun TokenBalanceNextAccrual(hours: Int?) {
    AnimatedVisibility(
        visible = hours != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        hours?.let { subhead2_jacob(pluralStringResource(R.plurals.staking_next_accrual_in_hours, it, it)) }
    }
}

@Composable
private fun StakingUnpaidInfoSheet(stackingType: StackingType, onDismiss: () -> Unit) {
    val bodyRes = when (stackingType) {
        StackingType.PCASH -> R.string.staking_unpaid_info_body_pirate
        StackingType.COSANTA -> R.string.staking_unpaid_info_body_cosanta
    }
    InfoBottomSheet(
        title = stringResource(R.string.staking_unpaid_info_title),
        text = stringResource(bodyRes),
        onDismiss = onDismiss,
    )
}

@Composable
private fun TokenBalanceHeaderAlerts(params: TokenBalanceHeaderParams) {
    val item = params.balanceViewItem
    params.uiState.zcashMigrationRequiredAmount?.let { amount ->
            ZcashMigrationRequiredSection(
                amount = amount,
                amountVisible = item.primaryValue.visible,
                wallet = item.wallet
            )
        }
        LockedBalanceSection(item)
        item.warning?.let {
            VSpacer(height = 8.dp)
            TextImportantWarning(
                icon = R.drawable.ic_attention_20,
                title = it.title.getString(),
                text = it.text.getString()
            )
        }
        params.uiState.networkFeeWarning?.let { warningData ->
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
                onClose = params.onDismissNetworkFeeWarning
            )
        }
    }

private data class TokenBalanceHeaderParams(
    val balanceViewItem: BalanceViewItem,
    val navController: NavController,
    val uiState: TokenBalanceModule.TokenBalanceUiState,
    val secondaryValue: DeemedValue<String>,
    val onStackingClicked: () -> Unit,
    val onClickSubtitle: () -> Unit,
    val onToggleBalanceVisibility: () -> Unit,
    val onSendClick: () -> Unit,
    val onReceiveClick: () -> Unit,
    val onShieldClick: () -> Unit,
    val onSyncErrorClick: (BalanceViewItem) -> Unit,
    val onDismissNetworkFeeWarning: () -> Unit,
    val isShowShieldFunds: Boolean,
)

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
                MoneroPreparationParams(
                    syncInProgress = syncInProgress,
                    error = error,
                    fullWalletRecoveryAvailable = fullWalletRecoveryAvailable,
                    onSync = onSync,
                    onFullWalletRecovery = onFullWalletRecovery,
                    onDismiss = onDismiss,
                ),
            )
        }
    }
}

@Composable
private fun MoneroSendPreparationContent(
    params: MoneroPreparationParams,
) {
    Column {
        body_leah(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = stringResource(R.string.monero_prepare_trezor_description),
        )
        params.error?.let {
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                text = stringResource(it),
            )
        }
        MoneroSendPreparationActions(params)
    }
}

@Composable
private fun MoneroSendPreparationActions(
    params: MoneroPreparationParams,
) {
    val actionUiState = moneroPreparationActionUiState(params.syncInProgress)
    Column {
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            title = stringResource(actionUiState.title),
            onClick = params.onSync,
            enabled = actionUiState.enabled,
            loadingIndicator = actionUiState.loading,
        )
        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            title = stringResource(R.string.Button_Cancel),
            onClick = params.onDismiss,
        )
        if (params.fullWalletRecoveryAvailable) {
            body_leah(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                text = stringResource(R.string.monero_full_wallet_recovery_warning),
            )
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.monero_full_wallet_recovery),
                onClick = params.onFullWalletRecovery,
            )
        }
        VSpacer(32.dp)
    }
}

private data class MoneroPreparationParams(
    val syncInProgress: Boolean,
    @StringRes val error: Int?,
    val fullWalletRecoveryAvailable: Boolean,
    val onSync: () -> Unit,
    val onFullWalletRecovery: () -> Unit,
    val onDismiss: () -> Unit,
)

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
private fun ButtonsRow(params: TokenBalanceButtonsParams) {
    TokenBalanceMainButtons(params)
    if (params.isShowShieldFunds) ShieldFundsButton(params.onShieldClick)
}

private data class TokenBalanceButtonsParams(
    val viewItem: BalanceViewItem,
    val navController: NavController,
    val sendEnabled: Boolean,
    val onSendClick: () -> Unit,
    val onReceiveClick: () -> Unit,
    val onShieldClick: () -> Unit,
    val onStackingClicked: () -> Unit,
    val isShowShieldFunds: Boolean,
)

@Composable
private fun TokenBalanceMainButtons(params: TokenBalanceButtonsParams) {
    Row(
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
    ) {
        if (params.viewItem.isWatchAccount) WatchAccountButtons(params) else RegularAccountButtons(params)
    }
}

@Composable
private fun RowScope.WatchAccountButtons(params: TokenBalanceButtonsParams) {
    ButtonPrimaryDefault(Modifier.weight(1f), stringResource(R.string.Balance_Address), params.onReceiveClick)
    StakingButton(params)
}

@Composable
private fun RowScope.RegularAccountButtons(params: TokenBalanceButtonsParams) {
    val item = params.viewItem
    if (!item.isSendDisabled) {
        ButtonPrimaryYellow(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.Balance_Send),
            onClick = params.onSendClick,
            enabled = params.sendEnabled,
        )
        HSpacer(8.dp)
    }
    ReceiveButton(params)
    if (item.swapVisible) SwapButton(params)
    StakingButton(params)
}

@Composable
private fun RowScope.ReceiveButton(params: TokenBalanceButtonsParams) {
    if (params.viewItem.swapVisible) {
        ButtonPrimaryCircle(
            icon = R.drawable.ic_arrow_down_left_24,
            contentDescription = stringResource(R.string.Balance_Receive),
            onClick = params.onReceiveClick,
            iconTint = Color.Black,
            background = Color.White,
        )
    } else {
        ButtonPrimaryDefault(Modifier.weight(1f), stringResource(R.string.Balance_Receive), params.onReceiveClick)
    }
}

@Composable
private fun SwapButton(params: TokenBalanceButtonsParams) {
    HSpacer(8.dp)
    ButtonPrimaryCircle(
        icon = R.drawable.ic_swap_24,
        contentDescription = stringResource(R.string.Swap),
        onClick = {
            params.navController.slideFromRight(
                R.id.multiswap,
                SwapParams.TOKEN_IN to params.viewItem.wallet.token,
            )
        },
        enabled = params.viewItem.swapEnabled, iconTint = Color.Black, background = Color.White,
    )
}

@Composable
private fun StakingButton(params: TokenBalanceButtonsParams) {
    if (!params.viewItem.wallet.isStakingWallet()) return
    HSpacer(8.dp)
    ButtonPrimaryCircle(
        icon = R.drawable.ic_coins_stacking,
        contentDescription = stringResource(R.string.stacking),
        onClick = params.onStackingClicked,
        iconTint = Color.Black, background = Color.White,
    )
}

@Composable
private fun ShieldFundsButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.shield_funds),
            onClick = onClick,
        )
        body_grey(
            text = stringResource(R.string.typical_fee),
            modifier = Modifier.padding(top = 4.dp),
        )
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
        tokenBalanceScreenContent(
            TokenBalanceScreenContentParams(
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
