package cash.p.terminal.modules.send.solana

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.address.AddressParserModule
import cash.p.terminal.modules.address.AddressParserViewModel
import cash.p.terminal.modules.address.HSAddressCell
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.amount.HSAmountInput
import cash.p.terminal.modules.availablebalance.AvailableBalance
import cash.p.terminal.modules.send.SendConfirmationFragment
import cash.p.terminal.modules.send.SendScreen
import cash.p.terminal.modules.send.openConfirm
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.HudHelper

@Composable
fun SendSolanaScreen(
    title: String,
    navController: NavController,
    viewModel: SendSolanaViewModel,
    amountInputModeViewModel: AmountInputModeViewModel,
    sendEntryPointDestId: Int,
    prefilledData: PrefilledData?,
    riskyAddress: Boolean
) {
    val view = LocalView.current
    val wallet = viewModel.wallet
    val uiState = viewModel.uiState

    val availableBalance = uiState.availableBalance
    val amountCaution = uiState.amountCaution
    val proceedEnabled = uiState.canBeSend
    val amountInputType = amountInputModeViewModel.inputType

    val paymentAddressViewModel = viewModel<AddressParserViewModel>(
        factory = AddressParserModule.Factory(wallet.token, prefilledData)
    )
    val amountUnique = paymentAddressViewModel.amountUnique
    val keyboardController = LocalSoftwareKeyboardController.current

    ComposeAppTheme {
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        SendScreen(
            title = title,
            onCloseClick = { navController.popBackStack() }
        ) {

            if (uiState.showAddressInput) {
                HSAddressCell(
                    title = stringResource(R.string.Send_Confirmation_To),
                    value = uiState.address.hex,
                    riskyAddress = riskyAddress
                ) {
                    navController.popBackStack()
                }
                VSpacer(16.dp)
            }

            HSAmountInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                focusRequester = focusRequester,
                availableBalance = availableBalance,
                caution = amountCaution,
                coinCode = wallet.coin.code,
                coinDecimal = viewModel.coinMaxAllowedDecimals,
                fiatDecimal = viewModel.fiatMaxAllowedDecimals,
                onClickHint = {
                    amountInputModeViewModel.onToggleInputType()
                },
                onValueChange = {
                    viewModel.onEnterAmount(it)
                },
                inputType = amountInputType,
                rate = viewModel.coinRate,
                amountUnique = amountUnique
            )
            Spacer(modifier = Modifier.height(12.dp))

            AvailableBalance(
                coinCode = wallet.coin.code,
                coinDecimal = viewModel.coinMaxAllowedDecimals,
                fiatDecimal = viewModel.fiatMaxAllowedDecimals,
                availableBalance = availableBalance,
                amountInputType = amountInputType,
                rate = viewModel.coinRate
            )

            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                title = stringResource(R.string.Send_DialogProceed),
                onClick = {
                    if (viewModel.hasConnection()) {
                        navController.openConfirm(
                            type = SendConfirmationFragment.Type.Solana,
                            riskyAddress = riskyAddress,
                            keyboardController = keyboardController,
                            sendEntryPointDestId = sendEntryPointDestId
                        )
                    } else {
                        HudHelper.showErrorMessage(view, R.string.Hud_Text_NoInternet)
                    }
                },
                enabled = proceedEnabled
            )
        }
    }

}
