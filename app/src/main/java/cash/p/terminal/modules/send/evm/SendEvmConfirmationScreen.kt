package cash.p.terminal.modules.send.evm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.offline.OfflineSyncRetryProgressEffect
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorCallbacks
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorScreen
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorState
import cash.p.terminal.modules.send.offline.OfflineSignFlowRoutes
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.modules.send.offline.offlineSignFlowRoutes

private const val EvmConfirmationPage = "evm_confirmation"
private const val OfflineEvmSignPage = "offline_evm_sign"
private const val OfflineEvmTransactionTransferPage = "offline_evm_transaction_transfer"
private const val OfflineTransactionTransferFormatArg = "format"

@Composable
internal fun SendEvmConfirmationScreen(
    navController: NavController,
    sendViewModel: SendEvmViewModel,
    sendEntryPointDestId: Int
) {
    val navState = EvmConfirmationNavState(
        fragmentNavController = navController,
        composeNavController = rememberNavController(),
        sendEntryPointDestId = sendEntryPointDestId,
    )
    NavHost(
        navController = navState.composeNavController,
        startDestination = EvmConfirmationPage,
    ) {
        evmConfirmationRoute(navState, sendViewModel)
        offlineSignFlowRoutes(
            routes = OfflineSignFlowRoutes(
                signRoute = OfflineEvmSignPage,
                transferRoute = OfflineEvmTransactionTransferPage,
                transferFormatArgument = OfflineTransactionTransferFormatArg,
            ),
            navController = navState.composeNavController,
            fragmentNavController = navState.fragmentNavController,
            sendViewModel = sendViewModel,
        )
    }
}

private fun NavGraphBuilder.evmConfirmationRoute(
    navState: EvmConfirmationNavState,
    sendViewModel: SendEvmViewModel,
) {
    composable(EvmConfirmationPage) {
        SendEvmConfirmationContent(
            navState = navState,
            sendViewModel = sendViewModel,
        )
    }
}

@Composable
private fun SendEvmConfirmationContent(
    navState: EvmConfirmationNavState,
    sendViewModel: SendEvmViewModel,
) {
    val isConnected by App.connectivityManager.isConnected.collectAsStateWithLifecycle()
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
        OfflineSendSyncErrorScreen(
            state = OfflineSendSyncErrorState(
                title = stringResource(
                    R.string.Send_Title,
                    sendViewModel.wallet.coin.code,
                ),
                coinCode = sendViewModel.wallet.coin.code,
                noConnection = !isConnected,
                inProgress = retryInProgress,
                sourceChangeable = false,
            ),
            callbacks = OfflineSendSyncErrorCallbacks(
                onBackClick = navState.fragmentNavController::popBackStackSafely,
                onRetryClick = {
                    retrying = true
                    sendViewModel.retryAdapterSync()
                },
                onChangeSourceClick = {},
                onSignOfflineClick = {
                    navState.composeNavController.navigate(OfflineEvmSignPage)
                },
            ),
        )
        return
    }

    SendConfirmationScreen(
        navController = navState.fragmentNavController,
        coinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
        feeCoinMaxAllowedDecimals = sendViewModel.feeTokenMaxAllowedDecimals,
        rate = sendViewModel.coinRate,
        feeCoinRate = sendViewModel.feeCoinRate,
        sendResult = sendViewModel.sendResult,
        blockchainType = sendViewModel.blockchainType,
        coin = confirmationData.coin,
        feeCoin = confirmationData.feeCoin,
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

private data class EvmConfirmationNavState(
    val fragmentNavController: NavController,
    val composeNavController: NavHostController,
    val sendEntryPointDestId: Int,
)
