package cash.p.terminal.modules.send.zcash

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

private const val ZCashConfirmationPage = "zcash_confirmation"
private const val OfflineZCashSignPage = "offline_zcash_confirmation_sign"
private const val OfflineZCashTransactionTransferPage = "offline_zcash_confirmation_transfer"

@Composable
fun SendZCashConfirmationScreen(
    navController: NavController,
    sendViewModel: SendZCashViewModel,
    sendEntryPointDestId: Int
) {
    OfflineSignableConfirmationHost(
        fragmentNavController = navController,
        sendViewModel = sendViewModel,
        confirmationRoute = ZCashConfirmationPage,
        signFlowRoutes = OfflineSignFlowRoutes(
            signRoute = OfflineZCashSignPage,
            transferRoute = OfflineZCashTransactionTransferPage,
        ),
        sourceChangeable = false,
        onChangeSourceClick = {},
    ) { onRequestOfflineSign ->
        ZCashOnlineConfirmation(
            navController = navController,
            sendViewModel = sendViewModel,
            sendEntryPointDestId = sendEntryPointDestId,
            onRequestOfflineSign = onRequestOfflineSign,
        )
    }
}

@Composable
private fun ZCashOnlineConfirmation(
    navController: NavController,
    sendViewModel: SendZCashViewModel,
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
        feeCoinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
        rate = sendViewModel.coinRate,
        feeCoinRate = sendViewModel.coinRate,
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
