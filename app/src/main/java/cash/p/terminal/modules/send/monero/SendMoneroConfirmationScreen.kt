package cash.p.terminal.modules.send.monero

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.offline.OfflineSignFlowRoutes
import cash.p.terminal.modules.send.offline.OfflineSignableConfirmationHost

private const val MoneroConfirmationPage = "monero_confirmation"
private const val OfflineMoneroSignPage = "offline_monero_confirmation_sign"
private const val OfflineMoneroTransactionTransferPage = "offline_monero_confirmation_transfer"
private const val OfflineTransactionTransferFormatArg = "format"

@Composable
fun SendMoneroConfirmationScreen(
    navController: NavController,
    sendViewModel: SendMoneroViewModel,
    sendEntryPointDestId: Int
) {
    OfflineSignableConfirmationHost(
        fragmentNavController = navController,
        sendViewModel = sendViewModel,
        confirmationRoute = MoneroConfirmationPage,
        signFlowRoutes = OfflineSignFlowRoutes(
            signRoute = OfflineMoneroSignPage,
            transferRoute = OfflineMoneroTransactionTransferPage,
            transferFormatArgument = OfflineTransactionTransferFormatArg,
        ),
        sourceChangeable = false,
        onChangeSourceClick = {},
    ) { onRequestOfflineSign ->
        MoneroOnlineConfirmation(
            navController = navController,
            sendViewModel = sendViewModel,
            sendEntryPointDestId = sendEntryPointDestId,
            onRequestOfflineSign = onRequestOfflineSign,
        )
    }
}

@Composable
private fun MoneroOnlineConfirmation(
    navController: NavController,
    sendViewModel: SendMoneroViewModel,
    sendEntryPointDestId: Int,
    onRequestOfflineSign: (() -> Unit)?,
) {
    var confirmationData by remember { mutableStateOf(sendViewModel.getConfirmationData()) }
    var refresh by remember { mutableStateOf(false) }

    LifecycleResumeEffect(sendViewModel) {
        if (refresh) {
            confirmationData = sendViewModel.getConfirmationData()
        }

        onPauseOrDispose {
            refresh = true
        }
    }

    SendConfirmationScreen(
        navController = navController,
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
        sendEntryPointDestId = sendEntryPointDestId,
        isSynced = sendViewModel.isSynced,
        hasAdapterError = sendViewModel.hasAdapterError,
        onRetrySync = sendViewModel::retryAdapterSync,
        sendEnabled = sendViewModel.isEffectivelySynced,
        onSignOfflineOnFailure = onRequestOfflineSign,
        sendToken = sendViewModel.wallet.token,
        feeToken = sendViewModel.feeToken,
        feeCoinBalance = sendViewModel.feeCoinBalance,
        displayBalance = sendViewModel.displayBalance,
        insufficientFeeBalance = sendViewModel.isInsufficientFeeBalance(confirmationData.fee),
        balanceHidden = sendViewModel.balanceHidden,
        onBalanceClicked = sendViewModel::toggleHideBalance,
    )
}
