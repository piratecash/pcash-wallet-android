package cash.p.terminal.modules.send.solana

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.send.SendConfirmationScreen

@Composable
fun SendSolanaConfirmationScreen(
        navController: NavController,
        sendViewModel: SendSolanaViewModel,
        amountInputModeViewModel: AmountInputModeViewModel
) {
    val confirmationData = sendViewModel.getConfirmationData()

    SendConfirmationScreen(
            navController = navController,
            coinMaxAllowedDecimals = sendViewModel.coinMaxAllowedDecimals,
            feeCoinMaxAllowedDecimals = sendViewModel.feeTokenMaxAllowedDecimals,
            fiatMaxAllowedDecimals = sendViewModel.fiatMaxAllowedDecimals,
            amountInputType = amountInputModeViewModel.inputType,
            rate = sendViewModel.coinRate,
            feeCoinRate = sendViewModel.feeCoinRate,
            sendResult = sendViewModel.sendResult,
            coin = confirmationData.coin,
            feeCoin = confirmationData.feeCoin,
            amount = confirmationData.amount,
            address = confirmationData.address,
            fee = confirmationData.fee,
            lockTimeInterval = confirmationData.lockTimeInterval,
            memo = confirmationData.memo,
            onClickSend = sendViewModel::onClickSend
    )
}