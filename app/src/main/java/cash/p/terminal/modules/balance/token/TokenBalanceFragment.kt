package cash.p.terminal.modules.balance.token

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.premiumAction
import cash.p.terminal.core.restartMain
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.modules.pin.ConfirmPinFragment
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.popBackStackOrExecute
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.transactions.TransactionsModule
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.BalanceHideOnFlipHandling
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.findNavController
import cash.p.terminal.wallet.isPirateCash
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewScreen
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewModel
import cash.p.terminal.modules.balance.token.creationblock.CreationBlockScreen
import cash.p.terminal.modules.balance.token.creationblock.CreationBlockViewModel
import cash.p.terminal.modules.offline.OfflineModeToggleViewModel
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.usecase.GetRestoreHeightForWalletUseCase
import cash.p.terminal.wallet.AccountOrigin
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private sealed class TokenBalanceRoute {
    @Serializable
    data object Balance : TokenBalanceRoute()

    @Serializable
    data object Settings : TokenBalanceRoute()

    @Serializable
    data object AddressPoisoningView : TokenBalanceRoute()

    @Serializable
    data object CreationBlock : TokenBalanceRoute()
}

class TokenBalanceFragment : BaseComposeFragment() {
    private var viewModel: TokenBalanceViewModel? = null

    @Composable
    override fun GetContent(navController: NavController) {
        val transactionsViewModel: TransactionsViewModel? = try {
            navGraphViewModels<TransactionsViewModel>(R.id.mainFragment) { TransactionsModule.Factory() }.value
        } catch (e: IllegalArgumentException) {
            null
        }

        if (transactionsViewModel == null) {
            navController.popBackStackOrExecute { activity?.restartMain() }
            return
        }

        val args: TokenBalanceFragmentArgs by navArgs()
        val wallet = args.wallet

        val viewModel by viewModels<TokenBalanceViewModel> { TokenBalanceModule.Factory(wallet) }
        this.viewModel = viewModel

        TokenBalanceNavHost(
            fragmentNavController = navController,
            viewModel = viewModel,
            transactionsViewModel = transactionsViewModel,
            wallet = wallet,
            onShowAllTransactionsClicked = {
                navController.authorizedAction(
                    ConfirmPinFragment.InputConfirm(
                        descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                        pinType = PinType.TRANSACTIONS_HIDE
                    )
                ) {
                    viewModel.showAllTransactions(true)
                }
            },
            onClickSubtitle = {
                viewModel.toggleTotalType()
                HudHelper.vibrate(requireContext())
            }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel?.startStatusChecker()
        viewModel?.onResume()
    }

    override fun onPause() {
        viewModel?.stopStatusChecker()
        super.onPause()


        if (!skipHideTransactions()) {
            viewModel?.showAllTransactions(false)
        }
    }

    private fun skipHideTransactions(): Boolean {
        val previousBackStackEntry = findNavController().previousBackStackEntry?.destination?.id
        // No need to hide transactions when user goes to next screen
        // But hides when they go to background on back
        return previousBackStackEntry == R.id.tokenBalanceFragment
    }
}

@Composable
private fun TokenBalanceNavHost(
    fragmentNavController: NavController,
    viewModel: TokenBalanceViewModel,
    transactionsViewModel: TransactionsViewModel,
    wallet: cash.p.terminal.wallet.Wallet,
    onShowAllTransactionsClicked: () -> Unit,
    onClickSubtitle: () -> Unit
) {
    val navController = rememberNavController()
    val offlineModeToggleViewModel: OfflineModeToggleViewModel =
        koinViewModel { parametersOf(wallet) }

    // Hosted above the NavHost: the transition is started from both the balance banner and the
    // settings switch, so only one of the destinations would otherwise ever report a failure.
    val view = LocalView.current
    LaunchedEffect(offlineModeToggleViewModel.uiState.error) {
        offlineModeToggleViewModel.uiState.error?.let {
            HudHelper.showErrorMessage(view, it)
            offlineModeToggleViewModel.errorShown()
        }
    }

    NavHost(
        navController = navController,
        startDestination = TokenBalanceRoute.Balance
    ) {
        composable<TokenBalanceRoute.Balance> {
            BalanceHideOnFlipHandling()
            viewModel.refreshTransactionDisplaySettings()
            TokenBalanceScreen(
                viewModel = viewModel,
                transactionsViewModel = transactionsViewModel,
                navController = fragmentNavController,
                onStackingClicked = {
                    fragmentNavController.slideFromRight(
                        resId = R.id.stacking,
                        input = if (wallet.isPirateCash()) StackingType.PCASH else StackingType.COSANTA
                    )
                },
                onShowAllTransactionsClicked = onShowAllTransactionsClicked,
                onClickSubtitle = onClickSubtitle,
                onRefresh = viewModel::refresh,
                refreshing = viewModel.refreshing,
                onSettingsClick = {
                    navController.navigate(TokenBalanceRoute.Settings)
                },
                onGoOnline = offlineModeToggleViewModel::goOnline,
            )
        }
        composablePage<TokenBalanceRoute.Settings> {
            // Zcash created-in-app wallets have no history before their creation checkpoint, so
            // editing the birthday height is meaningful only for restored (imported) Zcash wallets.
            val creationBlockVisible = wallet.token.blockchainType == BlockchainType.Monero ||
                (wallet.token.blockchainType == BlockchainType.Zcash &&
                    wallet.account.origin == AccountOrigin.Restored)
            val getRestoreHeight = remember { getKoinInstance<GetRestoreHeightForWalletUseCase>() }
            val currentHeightText by produceState<String?>(null, wallet, creationBlockVisible) {
                value = if (creationBlockVisible) getRestoreHeight(wallet)?.toString() else null
            }
            AssetSettingsScreen(
                amlCheckEnabled = viewModel.uiState.amlCheckEnabled,
                onAmlCheckChange = { enabled ->
                    if (enabled) {
                        fragmentNavController.premiumAction {
                            viewModel.setAmlCheckEnabled(true)
                        }
                    } else {
                        viewModel.setAmlCheckEnabled(false)
                    }
                },
                pricePeriod = viewModel.uiState.displayDiffPricePeriod,
                displayDiffOptionType = viewModel.uiState.displayDiffOptionType,
                isRoundingAmount = viewModel.uiState.isRoundingAmount,
                onPricePeriodChange = viewModel::setDisplayPricePeriod,
                onDisplayDiffOptionTypeChange = viewModel::setDisplayDiffOptionType,
                onRoundingAmountChange = viewModel::setRoundingAmount,
                onAddressPoisoningViewClick = {
                    navController.navigate(TokenBalanceRoute.AddressPoisoningView)
                },
                transactionFiltersEnabled = viewModel.uiState.transactionFiltersEnabled,
                onTransactionFiltersChange = viewModel::setTransactionFiltersEnabled,
                offlineUiState = offlineModeToggleViewModel.uiState,
                onConfirmOffline = offlineModeToggleViewModel::confirmOffline,
                onGoOnline = offlineModeToggleViewModel::goOnline,
                onOfflineSheetDismiss = offlineModeToggleViewModel::sheetClosed,
                navController = fragmentNavController,
                onBack = navController::popBackStackSafely,
                creationBlockVisible = creationBlockVisible,
                currentHeightText = currentHeightText,
                onCreationBlockClick = { navController.navigate(TokenBalanceRoute.CreationBlock) },
            )
        }
        composablePage<TokenBalanceRoute.CreationBlock> {
            val creationBlockViewModel: CreationBlockViewModel =
                koinViewModel { parametersOf(wallet) }
            CreationBlockScreen(
                uiState = creationBlockViewModel.uiState,
                onHeightChange = creationBlockViewModel::onHeightChange,
                onDatePick = creationBlockViewModel::onDatePicked,
                onRescanConfirm = creationBlockViewModel::onRescanConfirmed,
                onClose = navController::navigateUpSafely,
                onRescanStart = navController::navigateUp,
            )
        }
        composablePage<TokenBalanceRoute.AddressPoisoningView> {
            val addressPoisoningViewModel: AddressPoisoningViewModel = koinViewModel {
                parametersOf(wallet.coin.uid, wallet.isPirateCash(), wallet.token.blockchainType)
            }
            AddressPoisoningViewScreen(
                uiState = addressPoisoningViewModel.uiState,
                onSelect = addressPoisoningViewModel::onSelect,
                onClose = navController::navigateUpSafely,
            )
        }
    }
}
