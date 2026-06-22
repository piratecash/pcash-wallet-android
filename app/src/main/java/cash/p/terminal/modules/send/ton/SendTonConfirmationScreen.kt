package cash.p.terminal.modules.send.ton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.fee.NetworkFeeWarningData
import cash.p.terminal.modules.send.fee.NetworkFeeWarningOverlay
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorCallbacks
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorScreen
import cash.p.terminal.modules.send.offline.OfflineSendSyncErrorState
import cash.p.terminal.modules.send.offline.OfflineSignFlowRoutes
import cash.p.terminal.modules.send.offline.OfflineSyncRetryProgressEffect
import cash.p.terminal.modules.send.offline.offlineSignFlowRoutes
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import java.math.BigDecimal

private const val TonConfirmationPage = "ton_confirmation"
private const val OfflineTonSignPage = "offline_ton_sign"
private const val OfflineTonTransactionTransferPage = "offline_ton_transaction_transfer"
private const val OfflineTransactionTransferFormatArg = "format"

@Composable
fun SendTonConfirmationScreen(
    navController: NavController,
    sendViewModel: SendTonViewModel,
    sendEntryPointDestId: Int
) {
    val navState = TonConfirmationNavState(
        fragmentNavController = navController,
        composeNavController = rememberNavController(),
        sendEntryPointDestId = sendEntryPointDestId,
    )
    NavHost(
        navController = navState.composeNavController,
        startDestination = TonConfirmationPage,
    ) {
        tonConfirmationRoute(navState, sendViewModel)
        offlineSignFlowRoutes(
            routes = OfflineSignFlowRoutes(
                signRoute = OfflineTonSignPage,
                transferRoute = OfflineTonTransactionTransferPage,
                transferFormatArgument = OfflineTransactionTransferFormatArg,
            ),
            navController = navState.composeNavController,
            fragmentNavController = navState.fragmentNavController,
            sendViewModel = sendViewModel,
        )
    }
}

private fun NavGraphBuilder.tonConfirmationRoute(
    navState: TonConfirmationNavState,
    sendViewModel: SendTonViewModel,
) {
    composable(TonConfirmationPage) {
        SendTonConfirmationContent(navState, sendViewModel)
    }
}

@Composable
private fun SendTonConfirmationContent(
    navState: TonConfirmationNavState,
    sendViewModel: SendTonViewModel,
) {
    val isConnected by App.connectivityManager.isConnected.collectAsStateWithLifecycle()
    var confirmationData by remember { mutableStateOf(sendViewModel.getConfirmationData()) }
    var refresh by remember { mutableStateOf(false) }
    var retrying by remember { mutableStateOf(false) }
    val showSyncBlocker = sendViewModel.offlineSignSupported &&
        (!isConnected || !sendViewModel.isSynced || sendViewModel.hasAdapterError)
    val retryInProgress = retrying ||
        (sendViewModel.syncRetrying && isConnected && !sendViewModel.hasAdapterError)
    TonConfirmationRefreshEffect(
        isSynced = sendViewModel.isSynced,
        refresh = refresh,
        onRefreshData = { confirmationData = sendViewModel.getConfirmationData() },
        onPause = { refresh = true },
    )
    OfflineSyncRetryProgressEffect(
        retrying = retrying,
        isConnected = isConnected,
        isSynced = sendViewModel.isSynced,
        hasAdapterError = sendViewModel.hasAdapterError,
        onRetryFinish = { retrying = false },
    )
    if (showSyncBlocker) {
        TonOfflineSyncBlocker(
            state = sendViewModel.syncBlockerState(
                title = stringResource(R.string.Send_Title, sendViewModel.wallet.coin.code),
                noConnection = !isConnected,
                retryInProgress = retryInProgress,
            ),
            callbacks = TonSyncBlockerCallbacks(
                onBackClick = navState.fragmentNavController::popBackStackSafely,
                onRetryClick = {
                    retrying = true
                    sendViewModel.retryAdapterSync()
                },
                onSignOfflineClick = {
                    navState.composeNavController.navigate(OfflineTonSignPage)
                },
            )
        )
        return
    }
    TonConfirmationForm(
        navState = navState,
        state = sendViewModel.confirmationState(confirmationData),
        callbacks = TonConfirmationCallbacks(
            onClickSend = sendViewModel::onClickSendWithWarningCheck,
            onRetrySync = sendViewModel::retryAdapterSync,
            onBalanceClick = sendViewModel::toggleHideBalance,
            onFeeWarningConfirm = sendViewModel::onFeeWarningConfirmed,
            onFeeWarningCancel = sendViewModel::onFeeWarningCancelled,
        ),
    )
}

@Composable
private fun TonConfirmationRefreshEffect(
    isSynced: Boolean,
    refresh: Boolean,
    onRefreshData: () -> Unit,
    onPause: () -> Unit,
) {
    val currentRefresh by rememberUpdatedState(refresh)
    val currentOnRefreshData by rememberUpdatedState(onRefreshData)
    val currentOnPause by rememberUpdatedState(onPause)

    LifecycleResumeEffect(Unit) {
        if (currentRefresh) {
            currentOnRefreshData()
        }
        onPauseOrDispose {
            currentOnPause()
        }
    }
    LaunchedEffect(isSynced) {
        if (isSynced) {
            currentOnRefreshData()
        }
    }
}

@Composable
private fun TonOfflineSyncBlocker(
    state: TonSyncBlockerState,
    callbacks: TonSyncBlockerCallbacks,
) {
    OfflineSendSyncErrorScreen(
        state = OfflineSendSyncErrorState(
            title = state.title,
            coinCode = state.coinCode,
            noConnection = state.noConnection,
            inProgress = state.retryInProgress,
            sourceChangeable = false,
        ),
        callbacks = OfflineSendSyncErrorCallbacks(
            onBackClick = callbacks.onBackClick,
            onRetryClick = callbacks.onRetryClick,
            onChangeSourceClick = {},
            onSignOfflineClick = callbacks.onSignOfflineClick,
        ),
    )
}

@Composable
private fun TonConfirmationForm(
    navState: TonConfirmationNavState,
    state: TonConfirmationState,
    callbacks: TonConfirmationCallbacks,
) {
    SendConfirmationScreen(
        navController = navState.fragmentNavController,
        coinMaxAllowedDecimals = state.coinMaxAllowedDecimals,
        feeCoinMaxAllowedDecimals = state.feeCoinMaxAllowedDecimals,
        rate = state.rate,
        feeCoinRate = state.feeCoinRate,
        sendResult = state.sendResult,
        blockchainType = state.blockchainType,
        coin = state.confirmationData.coin,
        feeCoin = state.confirmationData.feeCoin,
        amount = state.confirmationData.amount,
        address = state.confirmationData.address,
        contact = state.confirmationData.contact,
        fee = state.confirmationData.fee,
        lockTimeInterval = state.confirmationData.lockTimeInterval,
        memo = state.confirmationData.memo,
        rbfEnabled = state.confirmationData.rbfEnabled,
        onClickSend = callbacks.onClickSend,
        sendEntryPointDestId = navState.sendEntryPointDestId,
        isSynced = state.isSynced,
        hasAdapterError = state.hasAdapterError,
        onRetrySync = callbacks.onRetrySync,
        sendToken = state.sendToken,
        feeToken = state.feeToken,
        feeCoinBalance = state.feeCoinBalance,
        displayBalance = state.displayBalance,
        insufficientFeeBalance = state.insufficientFeeBalance,
        balanceHidden = state.balanceHidden,
        onBalanceClicked = callbacks.onBalanceClick,
        feeWarningData = state.inlineFeeWarningData,
    )

    NetworkFeeWarningOverlay(
        feeWarningData = state.feeWarningData,
        onConfirm = callbacks.onFeeWarningConfirm,
        onCancel = callbacks.onFeeWarningCancel,
    )
}

private fun SendTonViewModel.confirmationState(confirmationData: SendConfirmationData) =
    TonConfirmationState(
        confirmationData = confirmationData,
        coinMaxAllowedDecimals = coinMaxAllowedDecimals,
        feeCoinMaxAllowedDecimals = feeTokenMaxAllowedDecimals,
        rate = coinRate,
        feeCoinRate = feeCoinRate,
        sendResult = sendResult,
        blockchainType = blockchainType,
        isSynced = isSynced,
        hasAdapterError = hasAdapterError,
        sendToken = wallet.token,
        feeToken = feeToken,
        feeCoinBalance = feeCoinBalance,
        displayBalance = displayBalance,
        insufficientFeeBalance = isInsufficientFeeBalance(confirmationData.fee),
        balanceHidden = balanceHidden,
        inlineFeeWarningData = inlineFeeWarningData,
        feeWarningData = feeWarningData,
    )

private fun SendTonViewModel.syncBlockerState(
    title: String,
    noConnection: Boolean,
    retryInProgress: Boolean,
) = TonSyncBlockerState(
    title = title,
    coinCode = wallet.coin.code,
    noConnection = noConnection,
    retryInProgress = retryInProgress,
)

private data class TonConfirmationNavState(
    val fragmentNavController: NavController,
    val composeNavController: NavHostController,
    val sendEntryPointDestId: Int,
)

private data class TonSyncBlockerState(
    val title: String,
    val coinCode: String,
    val noConnection: Boolean,
    val retryInProgress: Boolean,
)

private data class TonSyncBlockerCallbacks(
    val onBackClick: () -> Unit,
    val onRetryClick: () -> Unit,
    val onSignOfflineClick: () -> Unit,
)

private data class TonConfirmationState(
    val confirmationData: SendConfirmationData,
    val coinMaxAllowedDecimals: Int,
    val feeCoinMaxAllowedDecimals: Int,
    val rate: CurrencyValue?,
    val feeCoinRate: CurrencyValue?,
    val sendResult: SendResult?,
    val blockchainType: BlockchainType,
    val isSynced: Boolean,
    val hasAdapterError: Boolean,
    val sendToken: Token,
    val feeToken: Token?,
    val feeCoinBalance: BigDecimal?,
    val displayBalance: BigDecimal?,
    val insufficientFeeBalance: Boolean,
    val balanceHidden: Boolean,
    val inlineFeeWarningData: NetworkFeeWarningData?,
    val feeWarningData: NetworkFeeWarningData?,
)

private data class TonConfirmationCallbacks(
    val onClickSend: () -> Unit,
    val onRetrySync: () -> Unit,
    val onBalanceClick: () -> Unit,
    val onFeeWarningConfirm: () -> Unit,
    val onFeeWarningCancel: () -> Unit,
)
