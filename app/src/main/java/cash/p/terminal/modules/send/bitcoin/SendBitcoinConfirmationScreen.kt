package cash.p.terminal.modules.send.bitcoin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.offline.OfflineSyncRetryProgressEffect
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorCallbacks
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorScreen
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorState
import cash.p.terminal.modules.send.offline.OfflineSignFlowRoutes
import cash.p.terminal.modules.send.offline.offlineSignFlowRoutes
import cash.p.terminal.modules.syncerror.SyncErrorModule
import cash.p.terminal.modules.syncerror.SyncErrorViewModel
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.slideFromBottom

private const val BitcoinConfirmationPage = "bitcoin_confirmation"
private const val OfflineBitcoinSignPage = "offline_bitcoin_sign"
private const val OfflineTransactionTransferPage = "offline_transaction_transfer"
private const val OfflineTransactionTransferFormatArg = "format"

@Composable
fun SendBitcoinConfirmationScreen(
    navController: NavController,
    sendViewModel: SendBitcoinViewModel,
    sendEntryPointDestId: Int
) {
    val navState = BitcoinConfirmationNavState(
        fragmentNavController = navController,
        composeNavController = rememberNavController(),
        sendEntryPointDestId = sendEntryPointDestId,
    )
    NavHost(
        navController = navState.composeNavController,
        startDestination = BitcoinConfirmationPage,
    ) {
        bitcoinConfirmationRoute(navState, sendViewModel)
        offlineBitcoinSignFlowRoutes(navState, sendViewModel)
    }
}

private fun NavGraphBuilder.bitcoinConfirmationRoute(
    navState: BitcoinConfirmationNavState,
    sendViewModel: SendBitcoinViewModel,
) {
    composable(BitcoinConfirmationPage) {
        SendBitcoinConfirmationContent(
            navState = navState,
            sendViewModel = sendViewModel,
        )
    }
}

private fun NavGraphBuilder.offlineBitcoinSignFlowRoutes(
    navState: BitcoinConfirmationNavState,
    sendViewModel: SendBitcoinViewModel,
) {
    offlineSignFlowRoutes(
        routes = OfflineSignFlowRoutes(
            signRoute = OfflineBitcoinSignPage,
            transferRoute = OfflineTransactionTransferPage,
            transferFormatArgument = OfflineTransactionTransferFormatArg,
        ),
        navController = navState.composeNavController,
        fragmentNavController = navState.fragmentNavController,
        sendViewModel = sendViewModel,
    )
}

@Composable
private fun SendBitcoinConfirmationContent(
    navState: BitcoinConfirmationNavState,
    sendViewModel: SendBitcoinViewModel,
) {
    val isConnected by App.connectivityManager.isConnected.collectAsStateWithLifecycle()
    val syncErrorViewModel = viewModel<SyncErrorViewModel>(
        factory = SyncErrorModule.Factory(sendViewModel.wallet)
    )
    var confirmationData by remember { mutableStateOf(sendViewModel.getConfirmationData()) }
    var refresh by remember { mutableStateOf(false) }
    var retrying by remember { mutableStateOf(false) }
    val showSyncBlocker = sendViewModel.offlineSignSupported &&
            (!isConnected || !sendViewModel.isSynced || sendViewModel.hasAdapterError)
    val retryInProgress = retrying ||
            (sendViewModel.syncRetrying && isConnected && !sendViewModel.hasAdapterError)

    LifecycleResumeEffect(sendViewModel) {
        if (refresh) {
            confirmationData = sendViewModel.getConfirmationData()
        }

        onPauseOrDispose {
            refresh = true
        }
    }

    LaunchedEffect(sendViewModel.isSynced) {
        if (sendViewModel.isSynced) {
            confirmationData = sendViewModel.getConfirmationData()
        }
    }

    OfflineSyncRetryProgressEffect(
        retrying = retrying,
        isConnected = isConnected,
        isSynced = sendViewModel.isSynced,
        hasAdapterError = sendViewModel.hasAdapterError,
        onRetryFinish = { retrying = false },
    )

    if (showSyncBlocker) {
        BitcoinOfflineSyncBlocker(
            navState = navState,
            coinCode = sendViewModel.wallet.coin.code,
            noConnection = !isConnected,
            inProgress = retryInProgress,
            sourceChangeable = syncErrorViewModel.sourceChangeable,
            blockchainWrapper = syncErrorViewModel.blockchainWrapper,
            onRetryClick = {
                retrying = true
                sendViewModel.retryAdapterSync()
            },
        )
        return
    }

    BitcoinOnlineConfirmation(
        navState = navState,
        sendViewModel = sendViewModel,
        confirmationData = confirmationData,
    )
}

@Composable
private fun BitcoinOnlineConfirmation(
    navState: BitcoinConfirmationNavState,
    sendViewModel: SendBitcoinViewModel,
    confirmationData: SendConfirmationData,
) {
    SendConfirmationScreen(
        navController = navState.fragmentNavController,
        coinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
        feeCoinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
        rate = sendViewModel.coinRate,
        feeCoinRate = sendViewModel.coinRate,
        sendResult = sendViewModel.sendResult,
        blockchainType = sendViewModel.blockchainType,
        coin = confirmationData.coin,
        feeCoin = confirmationData.coin,
        amount = confirmationData.amount,
        address = confirmationData.address,
        contact = confirmationData.contact,
        fee = confirmationData.fee,
        lockTimeInterval = confirmationData.lockTimeInterval,
        memo = confirmationData.memo,
        rbfEnabled = confirmationData.rbfEnabled,
        onClickSend = sendViewModel::onClickSend,
        sendEntryPointDestId = navState.sendEntryPointDestId,
        isSynced = sendViewModel.isSynced,
        hasAdapterError = sendViewModel.hasAdapterError,
        onRetrySync = sendViewModel::retryAdapterSync,
        sendToken = sendViewModel.wallet.token,
        feeToken = sendViewModel.feeToken,
        feeCoinBalance = sendViewModel.feeCoinBalance,
        displayBalance = sendViewModel.displayBalance,
        insufficientFeeBalance = sendViewModel.isInsufficientFeeBalance(confirmationData.fee),
        balanceHidden = sendViewModel.balanceHidden,
        onBalanceClicked = sendViewModel::toggleHideBalance,
    )
}

@Composable
private fun BitcoinOfflineSyncBlocker(
    navState: BitcoinConfirmationNavState,
    coinCode: String,
    noConnection: Boolean,
    inProgress: Boolean,
    sourceChangeable: Boolean,
    blockchainWrapper: SyncErrorModule.BlockchainWrapper?,
    onRetryClick: () -> Unit,
) {
    OfflineSendSyncErrorScreen(
        state = OfflineSendSyncErrorState(
            title = stringResource(R.string.Send_Title, coinCode),
            coinCode = coinCode,
            noConnection = noConnection,
            inProgress = inProgress,
            sourceChangeable = sourceChangeable,
        ),
        callbacks = OfflineSendSyncErrorCallbacks(
            onBackClick = navState.fragmentNavController::popBackStackSafely,
            onRetryClick = onRetryClick,
            onChangeSourceClick = {
                navState.fragmentNavController.openBitcoinSourceSettings(blockchainWrapper)
            },
            onSignOfflineClick = {
                navState.composeNavController.openOfflineBitcoinSign()
            },
        ),
    )
}

private data class BitcoinConfirmationNavState(
    val fragmentNavController: NavController,
    val composeNavController: NavHostController,
    val sendEntryPointDestId: Int,
)

private fun NavController.openBitcoinSourceSettings(
    blockchainWrapper: SyncErrorModule.BlockchainWrapper?,
) {
    if (blockchainWrapper?.type != SyncErrorModule.BlockchainWrapper.Type.Bitcoin) return

    slideFromBottom(
        R.id.btcBlockchainSettingsFragment,
        blockchainWrapper.blockchain
    )
}

private fun NavHostController.openOfflineBitcoinSign() {
    navigate(OfflineBitcoinSignPage)
}
