package cash.p.terminal.modules.send.solana

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.offline.OfflineSignFlowRoutes
import cash.p.terminal.modules.send.offline.OfflineSignableConfirmationHost

private const val SolanaConfirmationPage = "solana_confirmation"
private const val OfflineSolanaSignPage = "offline_solana_sign"
private const val OfflineSolanaTransactionTransferPage = "offline_solana_transaction_transfer"

@Composable
fun SendSolanaConfirmationScreen(
    navController: NavController,
    sendViewModel: SendSolanaViewModel,
    sendEntryPointDestId: Int
) {
    OfflineSignableConfirmationHost(
        fragmentNavController = navController,
        sendViewModel = sendViewModel,
        confirmationRoute = SolanaConfirmationPage,
        signFlowRoutes = OfflineSignFlowRoutes(
            signRoute = OfflineSolanaSignPage,
            transferRoute = OfflineSolanaTransactionTransferPage,
        ),
        sourceChangeable = false,
        onChangeSourceClick = {},
    ) { onRequestOfflineSign ->
        SolanaOnlineConfirmation(
            navController = navController,
            sendViewModel = sendViewModel,
            sendEntryPointDestId = sendEntryPointDestId,
            onRequestOfflineSign = onRequestOfflineSign,
        )
    }
}

@Composable
private fun SolanaOnlineConfirmation(
    navController: NavController,
    sendViewModel: SendSolanaViewModel,
    sendEntryPointDestId: Int,
    onRequestOfflineSign: (() -> Unit)?,
) {
    var confirmationData by remember { mutableStateOf(sendViewModel.getConfirmationData()) }
    var refresh by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        if (refresh) confirmationData = sendViewModel.getConfirmationData()
        onPauseOrDispose {
            refresh = true
        }
    }

    LaunchedEffect(sendViewModel.isSynced) {
        if (sendViewModel.isSynced) confirmationData = sendViewModel.getConfirmationData()
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
