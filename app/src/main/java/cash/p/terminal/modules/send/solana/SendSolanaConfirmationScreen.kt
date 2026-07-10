package cash.p.terminal.modules.send.solana

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
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.SendResult
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

private const val SolanaConfirmationPage = "solana_confirmation"
private const val OfflineSolanaSignPage = "offline_solana_sign"
private const val OfflineSolanaTransactionTransferPage = "offline_solana_transaction_transfer"
private const val OfflineTransactionTransferFormatArg = "format"

@Composable
fun SendSolanaConfirmationScreen(
    navController: NavController,
    sendViewModel: SendSolanaViewModel,
    sendEntryPointDestId: Int
) {
    val navState = SolanaConfirmationNavState(
        fragmentNavController = navController,
        composeNavController = rememberNavController(),
        sendEntryPointDestId = sendEntryPointDestId,
    )
    NavHost(
        navController = navState.composeNavController,
        startDestination = SolanaConfirmationPage,
    ) {
        solanaConfirmationRoute(navState, sendViewModel)
        offlineSignFlowRoutes(
            routes = OfflineSignFlowRoutes(
                signRoute = OfflineSolanaSignPage,
                transferRoute = OfflineSolanaTransactionTransferPage,
                transferFormatArgument = OfflineTransactionTransferFormatArg,
            ),
            navController = navState.composeNavController,
            fragmentNavController = navState.fragmentNavController,
            sendViewModel = sendViewModel,
        )
    }
}

private fun NavGraphBuilder.solanaConfirmationRoute(
    navState: SolanaConfirmationNavState,
    sendViewModel: SendSolanaViewModel,
) {
    composable(SolanaConfirmationPage) {
        SendSolanaConfirmationContent(
            navState = navState,
            sendViewModel = sendViewModel,
        )
    }
}

@Composable
private fun SendSolanaConfirmationContent(
    navState: SolanaConfirmationNavState,
    sendViewModel: SendSolanaViewModel,
) {
    val isConnected by App.connectivityManager.isConnected.collectAsStateWithLifecycle()
    var confirmationData by remember { mutableStateOf(sendViewModel.getConfirmationData()) }
    var refresh by remember { mutableStateOf(false) }
    var retrying by remember { mutableStateOf(false) }
    val showSyncBlocker = sendViewModel.offlineSignSupported &&
        (!isConnected || !sendViewModel.isSynced || sendViewModel.hasAdapterError)
    val retryInProgress = retrying ||
        (sendViewModel.syncRetrying && isConnected && !sendViewModel.hasAdapterError)

    LifecycleResumeEffect(Unit) {
        if (refresh) confirmationData = sendViewModel.getConfirmationData()
        onPauseOrDispose {
            refresh = true
        }
    }

    LaunchedEffect(sendViewModel.isSynced) {
        if (sendViewModel.isSynced) confirmationData = sendViewModel.getConfirmationData()
    }

    OfflineSyncRetryProgressEffect(
        retrying = retrying,
        isConnected = isConnected,
        isSynced = sendViewModel.isSynced,
        hasAdapterError = sendViewModel.hasAdapterError,
        onRetryFinish = { retrying = false },
    )

    if (showSyncBlocker) {
        val coinCode = sendViewModel.wallet.coin.code
        SolanaOfflineSendSyncBlocker(
            title = stringResource(R.string.Send_Title, coinCode),
            coinCode = coinCode,
            noConnection = !isConnected,
            retryInProgress = retryInProgress,
            onBackClick = navState.fragmentNavController::popBackStackSafely,
            onRetryClick = {
                retrying = true
                sendViewModel.retryAdapterSync()
            },
            onSignOfflineClick = { navState.composeNavController.navigate(OfflineSolanaSignPage) },
        )
        return
    }

    SolanaOnlineConfirmationScreen(
        navController = navState.fragmentNavController,
        sendEntryPointDestId = navState.sendEntryPointDestId,
        state = sendViewModel.toConfirmationState(confirmationData),
        actions = SolanaConfirmationActions(
            onClickSend = sendViewModel::onClickSend,
            onRetrySync = sendViewModel::retryAdapterSync,
            onBalanceClicked = sendViewModel::toggleHideBalance,
        ),
    )
}

private data class SolanaConfirmationState(
    val data: SendConfirmationData,
    val amount: SolanaConfirmationAmountState,
    val status: SolanaConfirmationStatusState,
    val wallet: SolanaConfirmationWalletState,
)

private data class SolanaConfirmationAmountState(
    val coinMaxAllowedDecimals: Int,
    val feeCoinMaxAllowedDecimals: Int,
    val rate: CurrencyValue?,
    val feeCoinRate: CurrencyValue?,
)

private data class SolanaConfirmationStatusState(
    val sendResult: SendResult?,
    val isSynced: Boolean,
    val hasAdapterError: Boolean,
)

private data class SolanaConfirmationWalletState(
    val blockchainType: BlockchainType,
    val sendToken: Token,
    val feeToken: Token,
    val feeCoinBalance: BigDecimal?,
    val displayBalance: BigDecimal?,
    val insufficientFeeBalance: Boolean,
    val balanceHidden: Boolean,
)

private data class SolanaConfirmationActions(
    val onClickSend: () -> Unit,
    val onRetrySync: () -> Unit,
    val onBalanceClicked: () -> Unit,
)

@Composable
private fun SolanaOnlineConfirmationScreen(
    navController: NavController,
    sendEntryPointDestId: Int,
    state: SolanaConfirmationState,
    actions: SolanaConfirmationActions,
) {
    SendConfirmationScreen(
        navController = navController,
        coinMaxAllowedDecimals = state.amount.coinMaxAllowedDecimals,
        feeCoinMaxAllowedDecimals = state.amount.feeCoinMaxAllowedDecimals,
        rate = state.amount.rate,
        feeCoinRate = state.amount.feeCoinRate,
        sendResult = state.status.sendResult,
        blockchainType = state.wallet.blockchainType,
        coin = state.data.coin,
        feeCoin = state.data.feeCoin,
        amount = state.data.amount,
        address = state.data.address,
        contact = state.data.contact,
        fee = state.data.fee,
        lockTimeInterval = state.data.lockTimeInterval,
        memo = state.data.memo,
        rbfEnabled = state.data.rbfEnabled,
        onClickSend = actions.onClickSend,
        sendEntryPointDestId = sendEntryPointDestId,
        isSynced = state.status.isSynced,
        hasAdapterError = state.status.hasAdapterError,
        onRetrySync = actions.onRetrySync,
        sendToken = state.wallet.sendToken,
        feeToken = state.wallet.feeToken,
        feeCoinBalance = state.wallet.feeCoinBalance,
        displayBalance = state.wallet.displayBalance,
        insufficientFeeBalance = state.wallet.insufficientFeeBalance,
        balanceHidden = state.wallet.balanceHidden,
        onBalanceClicked = actions.onBalanceClicked,
    )
}

private fun SendSolanaViewModel.toConfirmationState(
    confirmationData: SendConfirmationData,
) = SolanaConfirmationState(
    data = confirmationData,
    amount = SolanaConfirmationAmountState(
        coinMaxAllowedDecimals = coinMaxAllowedDecimals,
        feeCoinMaxAllowedDecimals = feeTokenMaxAllowedDecimals,
        rate = coinRate,
        feeCoinRate = feeCoinRate,
    ),
    status = SolanaConfirmationStatusState(
        sendResult = sendResult,
        isSynced = isSynced,
        hasAdapterError = hasAdapterError,
    ),
    wallet = SolanaConfirmationWalletState(
        blockchainType = blockchainType,
        sendToken = wallet.token,
        feeToken = feeToken,
        feeCoinBalance = feeCoinBalance,
        displayBalance = displayBalance,
        insufficientFeeBalance = isInsufficientFeeBalance(confirmationData.fee),
        balanceHidden = balanceHidden,
    ),
)

@Composable
private fun SolanaOfflineSendSyncBlocker(
    title: String,
    coinCode: String,
    noConnection: Boolean,
    retryInProgress: Boolean,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onSignOfflineClick: () -> Unit,
) {
    OfflineSendSyncErrorScreen(
        state = OfflineSendSyncErrorState(
            title = title,
            coinCode = coinCode,
            noConnection = noConnection,
            inProgress = retryInProgress,
            sourceChangeable = false,
        ),
        callbacks = OfflineSendSyncErrorCallbacks(
            onBackClick = onBackClick,
            onRetryClick = onRetryClick,
            onChangeSourceClick = {},
            onSignOfflineClick = onSignOfflineClick,
        ),
    )
}

private data class SolanaConfirmationNavState(
    val fragmentNavController: NavController,
    val composeNavController: NavHostController,
    val sendEntryPointDestId: Int,
)
