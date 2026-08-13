package cash.p.terminal.modules.balance.token

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.premiumAction
import cash.p.terminal.core.restartMain
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.modules.pin.ConfirmPinFragment
import cash.p.terminal.modules.balance.BackupRequiredError
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredDialog
import cash.p.terminal.modules.receive.ReceiveFragment
import cash.p.terminal.modules.syncerror.showSyncErrorDialog
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.popBackStackOrExecute
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.transactions.TransactionsModule
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.findNavController
import cash.p.terminal.wallet.isPirateCash
import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewScreen
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewModel
import cash.p.terminal.modules.balance.token.creationblock.CreationBlockScreen
import cash.p.terminal.modules.balance.token.creationblock.CreationBlockViewModel
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

    NavHost(
        navController = navController,
        startDestination = TokenBalanceRoute.Balance
    ) {
        composable<TokenBalanceRoute.Balance> {
            viewModel.refreshTransactionDisplaySettings()
            val uiState = viewModel.uiState
            TokenBalanceScreen(
                params = TokenBalanceScreenParams(
                    uiState = uiState,
                    secondaryValue = viewModel.secondaryValue,
                    events = viewModel.events,
                    navController = fragmentNavController,
                    refreshing = viewModel.refreshing,
                    sendResult = viewModel.sendResult,
                    moneroSpendReadiness = uiState.moneroSpendReadiness,
                    moneroKeyImageSyncInProgress = uiState.moneroKeyImageSyncInProgress,
                    moneroKeyImageSyncError = uiState.moneroKeyImageSyncError,
                    moneroFullWalletRecoveryAvailable = uiState.moneroFullWalletRecoveryAvailable,
                    actions = TokenBalanceScreenActions(
                        onToggleFavorite = viewModel::toggleFavorite,
                        onToggleBalanceVisibility = viewModel::toggleBalanceVisibility,
                        onSearchClick = viewModel::onSearchClick,
                        onSearchClose = viewModel::onSearchClose,
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        onSetTransactionType = viewModel::setTransactionType,
                        onWillShow = viewModel::willShow,
                        onTransactionClick = { transactionViewItem ->
                            viewModel.getTransactionItem(transactionViewItem)?.let { transactionItem ->
                                transactionsViewModel.tmpItemToShow = transactionItem
                                fragmentNavController.slideFromBottom(R.id.transactionInfoFragment)
                            }
                        },
                        onSensitiveTransactionClick = {
                            HudHelper.vibrate(App.instance)
                            transactionsViewModel.toggleTransactionInfoHidden(it.uid)
                        },
                        onBottomReached = viewModel::onBottomReached,
                        onSetAmlCheckEnabled = { enabled ->
                            if (enabled) fragmentNavController.premiumAction { viewModel.setAmlCheckEnabled(true) }
                            else viewModel.setAmlCheckEnabled(false)
                        },
                        onDismissAmlPromo = { view ->
                            viewModel.dismissAmlPromo()
                            HudHelper.showPremiumMessage(view, R.string.aml_promo_dismiss_hud, SnackbarDuration.LONG)
                        },
                        onDismissNetworkFeeWarning = viewModel::dismissNetworkFeeWarning,
                        onSendClick = { onPrepare -> viewModel.openSendOrPrepare(fragmentNavController, onPrepare) },
                        onReceiveClick = {
                            try {
                                val receiveWallet = viewModel.getWalletForReceive()
                                fragmentNavController.slideFromRight(
                                    R.id.receiveFragment,
                                    ReceiveFragment.Input(receiveWallet),
                                )
                            } catch (e: BackupRequiredError) {
                                val text = Translator.getString(
                                    R.string.ManageAccount_BackupRequired_Description,
                                    e.account.name,
                                    e.coinTitle,
                                )
                                fragmentNavController.slideFromBottom(
                                    R.id.backupRequiredDialog,
                                    BackupRequiredDialog.Input(e.account, text),
                                )
                            }
                        },
                        onShieldClick = viewModel::proposeShielding,
                        onSyncErrorClick = { viewItem ->
                            when (val syncErrorDetails = viewModel.getSyncErrorDetails(viewItem)) {
                                is BalanceViewModel.SyncError.Dialog -> {
                                    fragmentNavController.showSyncErrorDialog(
                                        syncErrorDetails.wallet,
                                        syncErrorDetails.errorMessage,
                                    )
                                }

                                is BalanceViewModel.SyncError.NetworkNotAvailable -> Unit
                            }
                        },
                        onSyncMoneroKeyImages = viewModel::syncMoneroKeyImages,
                        onFullMoneroWalletRecovery = viewModel::fullMoneroWalletRecovery,
                        onCancelMoneroKeyImageSync = viewModel::cancelMoneroKeyImageSync,
                        onStackingClicked = {
                            fragmentNavController.slideFromRight(
                                resId = R.id.stacking,
                                input = if (wallet.isPirateCash()) StackingType.PCASH else StackingType.COSANTA
                            )
                        },
                        onShowAllTransactionsClicked = onShowAllTransactionsClicked,
                        onClickSubtitle = onClickSubtitle,
                        onRefresh = viewModel::refresh,
                        onSettingsClick = { navController.navigate(TokenBalanceRoute.Settings) },
                    ),
                ),
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

private fun TokenBalanceViewModel.openSendOrPrepare(
    navController: NavController,
    onPrepare: () -> Unit,
) {
    val wallet = uiState.balanceViewItem?.wallet ?: return
    if (uiState.moneroHardwareWallet && uiState.moneroSpendReadiness != MoneroSpendReadiness.Ready) {
        onPrepare()
        prepareMoneroSend()
    } else {
        navController.openSend(wallet)
    }
}
