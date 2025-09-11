package cash.p.terminal.modules.send.evm

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.premiumAction
import cash.p.terminal.modules.address.AddressParserModule
import cash.p.terminal.modules.address.AddressParserViewModel
import cash.p.terminal.modules.address.HSAddressInput
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.amount.HSAmountInput
import cash.p.terminal.modules.availablebalance.AvailableBalance
import cash.p.terminal.modules.send.SendConfirmationFragment
import cash.p.terminal.modules.send.SendFragment.ProceedActionData
import cash.p.terminal.modules.send.SendScreen
import cash.p.terminal.modules.send.address.AddressCheckerControl
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet
import java.math.BigDecimal

@Composable
internal fun SendEvmScreen(
    title: String,
    navController: NavController,
    viewModel: SendEvmViewModel,
    amountInputModeViewModel: AmountInputModeViewModel,
    wallet: Wallet,
    amount: BigDecimal?,
    addressCheckerControl: AddressCheckerControl,
    onNextClick: (ProceedActionData) -> Unit,
) {
    val uiState = viewModel.uiState

    val availableBalance = uiState.availableBalance
    val addressError = uiState.addressError
    val amountCaution = uiState.amountCaution
    val proceedEnabled = uiState.canBeSend
    val amountInputType = amountInputModeViewModel.inputType

    val paymentAddressViewModel = viewModel<AddressParserViewModel>(
        factory = AddressParserModule.Factory(
            wallet.token,
            PrefilledData(uiState.address?.hex.orEmpty(), amount)
        )
    )
    val amountUnique = paymentAddressViewModel.amountUnique

    ComposeAppTheme {
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        SendScreen(
            title = title,
            proceedEnabled = proceedEnabled,
            onCloseClick = { navController.popBackStack() },
            onSendClick = {
                onNextClick(
                    ProceedActionData(
                        address = uiState.address?.hex,
                        wallet = wallet,
                        type = SendConfirmationFragment.Type.Evm,
                    )
                )
            }
        ) {
            if (uiState.showAddressInput) {
                HSAddressInput(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    initial = uiState.address,
                    tokenQuery = wallet.token.tokenQuery,
                    coinCode = wallet.coin.code,
                    error = addressError,
                    textPreprocessor = paymentAddressViewModel,
                    navController = navController
                ) {
                    viewModel.onEnterAddress(it)
                }
                Spacer(modifier = Modifier.height(12.dp))
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
            Spacer(modifier = Modifier.height(12.dp))
            SectionUniversalLawrence {
                SwitchWithText(
                    text = stringResource(R.string.SettingsAddressChecker_RecipientCheck),
                    checkEnabled = addressCheckerControl.uiState.addressCheckByBaseEnabled,
                    onCheckedChange = addressCheckerControl::onCheckBaseAddressClick
                )
            }
            SectionUniversalLawrence(modifier = Modifier.padding(top = 8.dp)) {
                SwitchWithText(
                    text = stringResource(R.string.settings_smart_contract_check),
                    checkEnabled = addressCheckerControl.uiState.addressCheckSmartContractEnabled,
                    onCheckedChange = {
                        navController.premiumAction {
                            addressCheckerControl.onCheckSmartContractAddressClick(it)
                        }
                    }
                )
            }

            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                title = stringResource(R.string.Send_DialogProceed),
                onClick = {
                    onNextClick(
                        ProceedActionData(
                            address = uiState.address?.hex,
                            wallet = wallet,
                            type = SendConfirmationFragment.Type.Evm,
                        )
                    )
                },
                enabled = proceedEnabled
            )
        }
    }
}
