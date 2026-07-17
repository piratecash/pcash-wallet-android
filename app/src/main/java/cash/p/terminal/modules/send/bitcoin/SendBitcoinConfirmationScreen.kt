package cash.p.terminal.modules.send.bitcoin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.send.SendConfirmationScreen
import cash.p.terminal.modules.send.offline.OfflineSignFlowRoutes
import cash.p.terminal.modules.send.offline.OfflineSignableConfirmationHost
import cash.p.terminal.modules.syncerror.SyncErrorModule
import cash.p.terminal.modules.syncerror.SyncErrorViewModel
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
    val syncErrorViewModel = viewModel<SyncErrorViewModel>(
        factory = SyncErrorModule.Factory(sendViewModel.wallet)
    )
    OfflineSignableConfirmationHost(
        fragmentNavController = navController,
        sendViewModel = sendViewModel,
        confirmationRoute = BitcoinConfirmationPage,
        signFlowRoutes = OfflineSignFlowRoutes(
            signRoute = OfflineBitcoinSignPage,
            transferRoute = OfflineTransactionTransferPage,
            transferFormatArgument = OfflineTransactionTransferFormatArg,
        ),
        sourceChangeable = syncErrorViewModel.sourceChangeable,
        onChangeSourceClick = {
            navController.openBitcoinSourceSettings(syncErrorViewModel.blockchainWrapper)
        },
    ) { onRequestOfflineSign ->
        BitcoinOnlineConfirmation(
            navController = navController,
            sendViewModel = sendViewModel,
            sendEntryPointDestId = sendEntryPointDestId,
            onRequestOfflineSign = onRequestOfflineSign,
        )
    }
}

@Composable
private fun BitcoinOnlineConfirmation(
    navController: NavController,
    sendViewModel: SendBitcoinViewModel,
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

    LaunchedEffect(sendViewModel.isSynced) {
        if (sendViewModel.isSynced) {
            confirmationData = sendViewModel.getConfirmationData()
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
        feeCoin = confirmationData.coin,
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

private fun NavController.openBitcoinSourceSettings(
    blockchainWrapper: SyncErrorModule.BlockchainWrapper?,
) {
    if (blockchainWrapper?.type != SyncErrorModule.BlockchainWrapper.Type.Bitcoin) return

    slideFromBottom(
        R.id.btcBlockchainSettingsFragment,
        blockchainWrapper.blockchain
    )
}
