package cash.p.terminal.modules.send.ton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.fee.NetworkFeeWarningData
import cash.p.terminal.modules.send.fee.NetworkFeeWarningOverlay
import cash.p.terminal.modules.send.offline.OfflineSignFlowRoutes
import cash.p.terminal.modules.send.offline.OfflineSignableConfirmationHost
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
    OfflineSignableConfirmationHost(
        fragmentNavController = navController,
        sendViewModel = sendViewModel,
        confirmationRoute = TonConfirmationPage,
        signFlowRoutes = OfflineSignFlowRoutes(
            signRoute = OfflineTonSignPage,
            transferRoute = OfflineTonTransactionTransferPage,
            transferFormatArgument = OfflineTransactionTransferFormatArg,
        ),
        sourceChangeable = false,
        onChangeSourceClick = {},
    ) { onRequestOfflineSign ->
        TonOnlineConfirmation(
            navController = navController,
            sendViewModel = sendViewModel,
            sendEntryPointDestId = sendEntryPointDestId,
            onRequestOfflineSign = onRequestOfflineSign,
        )
    }
}

@Composable
private fun TonOnlineConfirmation(
    navController: NavController,
    sendViewModel: SendTonViewModel,
    sendEntryPointDestId: Int,
    onRequestOfflineSign: (() -> Unit)?,
) {
    var confirmationData by remember { mutableStateOf(sendViewModel.getConfirmationData()) }
    var refresh by remember { mutableStateOf(false) }

    TonConfirmationRefreshEffect(
        isSynced = sendViewModel.isSynced,
        refresh = refresh,
        onRefreshData = { confirmationData = sendViewModel.getConfirmationData() },
        onPause = { refresh = true },
    )

    TonConfirmationForm(
        navController = navController,
        sendEntryPointDestId = sendEntryPointDestId,
        state = sendViewModel.confirmationState(confirmationData),
        onRequestOfflineSign = onRequestOfflineSign,
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
private fun TonConfirmationForm(
    navController: NavController,
    sendEntryPointDestId: Int,
    state: TonConfirmationState,
    onRequestOfflineSign: (() -> Unit)?,
    callbacks: TonConfirmationCallbacks,
) {
    SendConfirmationScreen(
        navController = navController,
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
        sendEntryPointDestId = sendEntryPointDestId,
        isSynced = state.isSynced,
        hasAdapterError = state.hasAdapterError,
        onRetrySync = callbacks.onRetrySync,
        sendEnabled = state.isEffectivelySynced,
        onSignOfflineOnFailure = onRequestOfflineSign,
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
        isEffectivelySynced = isEffectivelySynced,
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

private data class TonConfirmationState(
    val confirmationData: SendConfirmationData,
    val coinMaxAllowedDecimals: Int,
    val feeCoinMaxAllowedDecimals: Int,
    val rate: CurrencyValue?,
    val feeCoinRate: CurrencyValue?,
    val sendResult: SendResult?,
    val blockchainType: BlockchainType,
    val isSynced: Boolean,
    val isEffectivelySynced: Boolean,
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
