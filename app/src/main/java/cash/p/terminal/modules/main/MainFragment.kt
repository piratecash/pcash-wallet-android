package cash.p.terminal.modules.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BadgedBox
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.MainGraphDirections
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.managers.RateAppManager
import cash.p.terminal.core.notifications.TransactionNotificationManager
import cash.p.terminal.core.usecase.ResolveTransactionItemUseCase
import cash.p.terminal.core.restartMain
import cash.p.terminal.navigation.popBackStackOrExecute
import cash.p.terminal.modules.balance.ui.BalanceScreen
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.shared.main.MainDestinationIcon
import cash.p.terminal.shared.main.MainDestinationTitle
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredDialog
import cash.p.terminal.modules.market.MarketScreen
import cash.p.terminal.modules.pin.ConfirmPinFragment
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.rateapp.RateApp
import cash.p.terminal.modules.releasenotes.ReleaseNotesFragment
import cash.p.terminal.modules.rooteddevice.RootedDeviceModule
import cash.p.terminal.modules.rooteddevice.RootedDeviceScreen
import cash.p.terminal.modules.rooteddevice.RootedDeviceViewModel
import cash.p.terminal.modules.sendtokenselect.SendTokenSelectFragment
import cash.p.terminal.modules.settings.main.SettingsScreen
import cash.p.terminal.modules.transactions.TransactionsModule
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.modules.transactions.TransactionsScreen
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedDialog
import cash.p.terminal.modules.walletconnect.WCManager.SupportState
import cash.p.terminal.navigation.safeGetBackStackEntry
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.components.ConnectionStatusView
import cash.p.terminal.ui.compose.components.BadgeText
import cash.p.terminal.ui.compose.components.HsBottomNavigation
import cash.p.terminal.ui.compose.components.HsBottomNavigationItem
import cash.p.terminal.ui.extensions.WalletSwitchBottomSheet
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.BalanceHideOnFlipHandling
import cash.p.terminal.ui_compose.ModalOverlayTracker
import cash.p.terminal.ui_compose.findNavController
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.navigation.slideFromBottom
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.lang.ref.WeakReference

class MainFragment : BaseComposeFragment() {

    private var transactionsViewModelRef: WeakReference<TransactionsViewModel>? = null

    override val showConnectionPanel = false

    @Composable
    override fun GetContent(navController: NavController) {
        val backStackEntry = navController.safeGetBackStackEntry(R.id.mainFragment)
        val intent = (requireActivity() as MainActivity).viewModel.intentLiveData.observeAsState()

        backStackEntry?.let {
            val viewModel = ViewModelProvider(
                backStackEntry.viewModelStore,
                TransactionsModule.Factory()
            )[TransactionsViewModel::class.java]
            transactionsViewModelRef = WeakReference(viewModel)
            MainScreenWithRootedDeviceCheck(
                transactionsViewModel = viewModel,
                navController = navController,
                intent = intent.value,
                intentHandled = {
                    (requireActivity() as MainActivity).viewModel.intentHandled()
                }
            )
        } ?: run {
            // Back stack entry doesn't exist, try to pop or restart activity
            navController.popBackStackOrExecute { activity?.restartMain() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requireActivity().moveTaskToBack(true)
                }
            })
    }

    override fun onResume() {
        super.onResume()
        transactionsViewModelRef?.get()?.startStatusChecker()
    }

    override fun onPause() {
        super.onPause()
        transactionsViewModelRef?.get()?.stopStatusChecker()
        if (!skipHideTransactions()) {
            transactionsViewModelRef?.get()?.showAllTransactions(false)
        }
    }

    private fun skipHideTransactions(): Boolean {
        // No need to hide transactions when user goes to next screen
        // But hides when they go to background on back
        val currentDestination = findNavController().currentDestination?.id
        return currentDestination == R.id.transactionInfoFragment ||
                currentDestination == R.id.transactionFilterFragment
    }
}

@Composable
private fun MainScreenWithRootedDeviceCheck(
    transactionsViewModel: TransactionsViewModel,
    navController: NavController,
    intent: Intent?,
    intentHandled: () -> Unit,
    rootedDeviceViewModel: RootedDeviceViewModel = viewModel(factory = RootedDeviceModule.Factory())
) {
    if (rootedDeviceViewModel.showRootedDeviceWarning) {
        RootedDeviceScreen { rootedDeviceViewModel.ignoreRootedDeviceWarning() }
    } else {
        MainScreen(
            transactionsViewModel = transactionsViewModel,
            fragmentNavController = navController,
            intentLiveData = intent,
            intentHandled = intentHandled
        )
    }
}

@Composable
private fun MainScreen(
    transactionsViewModel: TransactionsViewModel,
    fragmentNavController: NavController,
    intentLiveData: Intent?,
    intentHandled: () -> Unit,
    viewModel: MainViewModel = koinViewModel()
) {
    val resolveTransactionItem = koinInject<ResolveTransactionItemUseCase>()
    val windowInfo = LocalWindowInfo.current
    val uiState = viewModel.uiState
    val selectedPage = uiState.selectedTabIndex
    val pagerState = rememberPagerState(initialPage = selectedPage) { uiState.mainNavItems.size }

    // On a non-first tab, back returns to the first (Balance) tab instead of leaving the app.
    // On the first tab this stays disabled, so back falls through to the activity callback (minimize).
    BackHandler(enabled = selectedPage != 0) {
        viewModel.onSelect(MainDestination.Balance)
    }

    var showWalletSheet by remember { mutableStateOf(false) }
    BalanceHideOnFlipHandling(
        allowed = !showWalletSheet && when (uiState.mainNavItems[selectedPage].mainNavItem) {
            MainDestination.Balance, MainDestination.Transactions -> true
            MainDestination.Market, MainDestination.Settings -> false
        },
    )
    LaunchedEffect(intentLiveData, uiState.contentHidden) {
        if (!uiState.contentHidden) {
            val recordUid = intentLiveData?.getStringExtra(TransactionNotificationManager.EXTRA_RECORD_UID)
            if (recordUid != null) {
                viewModel.onSelect(MainDestination.Transactions)
                val item = resolveTransactionItem(recordUid)
                intentHandled()
                if (item != null) {
                    transactionsViewModel.tmpItemToShow = item
                    fragmentNavController.slideFromBottom(R.id.transactionInfoFragment)
                }
            } else {
                intentLiveData?.data?.let {
                    intentHandled()
                    viewModel.handleDeepLink(it)
                }
            }
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        bottomBar = {
            Column {
                ConnectionStatusView()
                HsBottomNavigation(
                    backgroundColor = ComposeAppTheme.colors.tyler,
                    elevation = 10.dp
                ) {
                    uiState.mainNavItems.forEach { item ->
                        HsBottomNavigationItem(
                            icon = {
                                BadgedIcon(item.badge) {
                                    MainDestinationIcon(
                                        destination = item.mainNavItem,
                                        contentDescription = MainDestinationTitle(item.mainNavItem),
                                    )
                                }
                            },
                            selected = item.selected,
                            enabled = item.enabled,
                            selectedContentColor = ComposeAppTheme.colors.jacob,
                            unselectedContentColor = if (item.enabled) ComposeAppTheme.colors.grey else
                                ComposeAppTheme.colors.grey50,
                            onClick = {
                                viewModel.onSelect(item.mainNavItem)
                            },
                            onLongClick = {
                                if (item.mainNavItem == MainDestination.Balance) {
                                    showWalletSheet = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column {
            LaunchedEffect(key1 = selectedPage, block = {
                if (uiState.mainNavItems[selectedPage].mainNavItem != MainDestination.Transactions) {
                    transactionsViewModel.showAllTransactions(false)
                }
                pagerState.scrollToPage(selectedPage)
            })

            HorizontalPager(
                modifier = Modifier.weight(1f),
                state = pagerState,
                userScrollEnabled = false,
                verticalAlignment = Alignment.Top
            ) { page ->
                when (uiState.mainNavItems[page].mainNavItem) {
                    MainDestination.Market -> MarketScreen(fragmentNavController, paddingValues)
                    MainDestination.Balance -> BalanceScreen(
                        navController = fragmentNavController,
                        paddingValues = paddingValues,
                        onOpenTransactionInfo = { item ->
                            transactionsViewModel.tmpItemToShow = item
                            fragmentNavController.slideFromBottom(R.id.transactionInfoFragment)
                        },
                    )

                    MainDestination.Transactions -> TransactionsScreen(
                        navController = fragmentNavController,
                        paddingValues = paddingValues,
                        viewModel = transactionsViewModel,
                        onShowAllTransactionsClicked = {
                            fragmentNavController.authorizedAction(
                                ConfirmPinFragment.InputConfirm(
                                    descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                                    pinType = PinType.TRANSACTIONS_HIDE
                                )
                            ) {
                                transactionsViewModel.showAllTransactions(true)
                            }
                        }
                    )

                    MainDestination.Settings -> SettingsScreen(
                        fragmentNavController,
                        paddingValues
                    )
                }
            }
        }
    }
    // Losing activity-window focus happens both when a modal opens (foreground) and when the
    // app enters the recent-apps switcher. A foreground modal reports its own window focus via
    // hasForegroundModal, which stays true only while the modal is genuinely up front; on
    // entering recents the modal window loses focus too. So hide the content whenever the
    // activity window is unfocused and no modal holds focus — covering plain and modal cases.
    val hideForRecents =
        !windowInfo.isWindowFocused && !ModalOverlayTracker.hasForegroundModal
    HideContentBox(uiState.contentHidden || hideForRecents)

    // Wallet Selection Bottom Sheet
    if (showWalletSheet) {
        WalletSwitchBottomSheet(
            wallets = viewModel.wallets,
            watchingAddresses = viewModel.watchWallets,
            selectedAccount = uiState.activeWallet,
            premiumTypes = uiState.walletSwitchPremiumTypes,
            onSelectListener = { viewModel.onSelect(it) },
            onDismiss = {
                showWalletSheet = false
            }
        )
    }

    if (uiState.showWhatsNew) {
        LaunchedEffect(Unit) {
            fragmentNavController.slideFromBottom(
                R.id.releaseNotesFragment,
                ReleaseNotesFragment.Input(true)
            )
            viewModel.whatsNewShown()
        }
    }

    if (uiState.showRateAppDialog) {
        val context = LocalContext.current
        RateApp(
            onRateClick = {
                RateAppManager.openPlayMarket(context)
                viewModel.closeRateDialog()
            },
            onCancelClick = { viewModel.closeRateDialog() }
        )
    }

    if (uiState.wcSupportState != null) {
        when (val wcSupportState = uiState.wcSupportState) {
            SupportState.NotSupportedDueToNoActiveAccount -> {
                fragmentNavController.slideFromBottom(R.id.wcErrorNoAccountFragment)
            }

            is SupportState.NotSupportedDueToNonBackedUpAccount -> {
                val text = stringResource(R.string.WalletConnect_Error_NeedBackup)
                fragmentNavController.slideFromBottom(
                    R.id.backupRequiredDialog,
                    BackupRequiredDialog.Input(wcSupportState.account, text)
                )
            }

            is SupportState.NotSupported -> {
                fragmentNavController.slideFromBottom(
                    MainGraphDirections.actionGlobalToAccountTypeNotSupportedDialog(
                        AccountTypeNotSupportedDialog.Input(
                            iconResId = R.drawable.ic_wallet_connect_24,
                            titleResId = R.string.WalletConnect_Title,
                            connectionLabel = stringResource(R.string.WalletConnect_Title)
                        )
                    )
                )
            }

            else -> {}
        }
        viewModel.wcSupportStateHandled()
    }

    uiState.deeplinkPage?.let { deepLinkPage ->
        LaunchedEffect(Unit) {
            delay(500)
            if (deepLinkPage.navigationId == R.id.connectMiniAppFragment) {
                fragmentNavController.slideFromBottom(
                    deepLinkPage.navigationId,
                    deepLinkPage.input
                )
            } else {
                fragmentNavController.slideFromRight(
                    deepLinkPage.navigationId,
                    deepLinkPage.input
                )
            }
            viewModel.deeplinkPageHandled()
        }
    }

    uiState.openSend?.let { openSend ->
        fragmentNavController.slideFromRight(
            R.id.sendTokenSelectFragment,
            SendTokenSelectFragment.Input(
                openSend.blockchainTypes,
                openSend.tokenTypes,
                openSend.prefilledData
            )
        )
        viewModel.onSendOpened()
    }


    LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }
}

@Composable
private fun HideContentBox(contentHidden: Boolean) {
    val backgroundModifier = if (contentHidden) {
        Modifier.background(ComposeAppTheme.colors.tyler)
    } else {
        Modifier
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(backgroundModifier)
    )
}

@Composable
private fun BadgedIcon(
    badge: MainModule.BadgeType?,
    icon: @Composable BoxScope.() -> Unit,
) {
    when (badge) {
        is MainModule.BadgeType.BadgeNumber ->
            BadgedBox(
                badge = {
                    BadgeText(
                        text = badge.number.toString(),
                    )
                },
                content = icon
            )

        MainModule.BadgeType.BadgeDot ->
            BadgedBox(
                badge = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                ComposeAppTheme.colors.lucian,
                                shape = RoundedCornerShape(4.dp)
                            )
                    ) { }
                },
                content = icon
            )

        else -> {
            Box {
                icon()
            }
        }
    }
}
