package cash.p.terminal.modules.send.solana

import android.view.View
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.BuildConfig
import cash.p.terminal.R
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.AddressParserModule
import cash.p.terminal.modules.address.AddressParserViewModel
import cash.p.terminal.modules.address.AmountUnique
import cash.p.terminal.modules.address.HSAddressInput
import cash.p.terminal.modules.amount.AmountInputType
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.amount.HSAmountInput
import cash.p.terminal.modules.fee.FeeInfoSection
import cash.p.terminal.modules.send.SendConfirmationFragment
import cash.p.terminal.modules.send.SendFragment.ProceedActionData
import cash.p.terminal.modules.send.SendScreen
import cash.p.terminal.modules.send.SendSuggestionsBar
import cash.p.terminal.modules.send.address.AddressCheckerControl
import cash.p.terminal.modules.send.address.SmartContractCheckSection
import cash.p.terminal.modules.send.offline.OfflineSignFlowRoutes
import cash.p.terminal.modules.send.offline.offlineSignFlowRoutes
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.ui.compose.components.PoisonAddressRiskSection
import cash.p.terminal.ui.compose.components.PoisonWarningCell
import cash.p.terminal.ui.compose.components.TextPreprocessor
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.solanakit.SolanaKit
import java.math.BigDecimal

@Composable
fun SendSolanaNavHost(
    title: String,
    fragmentNavController: NavController,
    viewModel: SendSolanaViewModel,
    amountInputModeViewModel: AmountInputModeViewModel,
    prefilledData: PrefilledData?,
    addressCheckerControl: AddressCheckerControl,
    onNextClick: (ProceedActionData) -> Unit,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = SendSolanaPage,
    ) {
        composable(SendSolanaPage) {
            SendSolanaScreen(
                title = title,
                navController = fragmentNavController,
                state = viewModel.toScreenState(amountInputModeViewModel.inputType),
                prefilledData = prefilledData,
                addressCheckerControl = addressCheckerControl,
                actions = SendSolanaScreenActions(
                    onEnterAmount = viewModel::onEnterAmount,
                    onEnterAddress = viewModel::onEnterAddress,
                    onToggleInputType = amountInputModeViewModel::onToggleInputType,
                    hasConnection = viewModel::hasConnection,
                    onRiskAcceptedChange = viewModel::onRiskAcceptedChange,
                    onBalanceClicked = viewModel::toggleHideBalance,
                    onDebugOfflineSignClick = { navController.navigate(DebugOfflineSolanaSignPage) },
                    onNextClick = onNextClick,
                ),
            )
        }
        offlineSignFlowRoutes(
            routes = OfflineSignFlowRoutes(
                signRoute = DebugOfflineSolanaSignPage,
                transferRoute = DebugOfflineSolanaTransactionTransferPage,
                transferFormatArgument = DebugOfflineTransactionTransferFormatArg,
            ),
            navController = navController,
            fragmentNavController = fragmentNavController,
            sendViewModel = viewModel,
        )
    }
}

private const val SendSolanaPage = "send_solana"
private const val DebugOfflineSolanaSignPage = "debug_offline_solana_sign"
private const val DebugOfflineSolanaTransactionTransferPage = "debug_offline_solana_transaction_transfer"
private const val DebugOfflineTransactionTransferFormatArg = "format"

data class SendSolanaScreenState(
    val wallet: Wallet,
    val uiState: SendSolanaModule.SendUiState,
    val coinMaxAllowedDecimals: Int,
    val fiatMaxAllowedDecimals: Int,
    val amountInputType: AmountInputType,
    val coinRate: CurrencyValue?,
    val fee: SendSolanaFeeState,
    val displayBalance: BigDecimal?,
    val balanceHidden: Boolean,
    val offlineSignSupported: Boolean,
)

data class SendSolanaFeeState(
    val token: Token,
    val balance: BigDecimal?,
    val feePrimary: String,
    val feeSecondary: String,
    val insufficientFeeBalance: Boolean,
)

data class SendSolanaScreenActions(
    val onEnterAmount: (BigDecimal?) -> Unit,
    val onEnterAddress: (Address?) -> Unit,
    val onToggleInputType: () -> Unit,
    val hasConnection: () -> Boolean,
    val onRiskAcceptedChange: (Boolean) -> Unit,
    val onBalanceClicked: () -> Unit,
    val onDebugOfflineSignClick: () -> Unit,
    val onNextClick: (ProceedActionData) -> Unit,
)

@Composable
fun SendSolanaScreen(
    title: String,
    navController: NavController,
    state: SendSolanaScreenState,
    prefilledData: PrefilledData?,
    addressCheckerControl: AddressCheckerControl,
    actions: SendSolanaScreenActions,
) {
    val view = LocalView.current
    val wallet = state.wallet

    val paymentAddressViewModel = viewModel<AddressParserViewModel>(
        factory = AddressParserModule.Factory(wallet.token, prefilledData)
    )
    val textPreprocessor: TextPreprocessor = paymentAddressViewModel
    val amountUnique = paymentAddressViewModel.amountUnique

    ComposeAppTheme {
        val form = rememberSendSolanaForm(
            amountUnique = amountUnique,
            onProceed = { handleSolanaProceed(view, state, actions) },
        )

        LaunchedEffect(form.state.focusRequester) {
            form.state.focusRequester.requestFocus()
        }

        SendScreen(
            title = title,
            onCloseClick = { navController.popBackStackSafely() },
            proceedEnabled = state.uiState.canBeSend,
            onSendClick = form.actions.onProceed,
            bottomOverlay = {
                SendSuggestionsBar(
                    availableBalance = state.uiState.availableBalance,
                    coinDecimal = state.coinMaxAllowedDecimals,
                    coinAmount = form.state.coinAmount,
                    onAmountChange = { amount ->
                        form.actions.onCoinAmountChange(amount)
                        actions.onEnterAmount(amount)
                    },
                    onPercentageAmountUnique = form.actions.onPercentageAmountUnique,
                )
            }
        ) {
            val inputContext = SendSolanaInputContext(
                textPreprocessor = textPreprocessor,
                navController = navController,
                prefilledData = prefilledData,
                addressCheckerControl = addressCheckerControl,
            )
            SendSolanaFormContent(
                state = state,
                formState = form.state,
                inputContext = inputContext,
                actions = actions,
                formActions = form.actions,
            )
        }
    }
}

private data class SendSolanaForm(
    val state: SendSolanaFormState,
    val actions: SendSolanaFormActions,
)

private data class SendSolanaFormState(
    val focusRequester: FocusRequester,
    val amountUnique: AmountUnique?,
    val percentageAmountUnique: AmountUnique?,
    val coinAmount: BigDecimal?,
)

private data class SendSolanaFormActions(
    val onCoinAmountChange: (BigDecimal?) -> Unit,
    val onPercentageAmountUnique: (AmountUnique?) -> Unit,
    val onProceed: () -> Unit,
)

private data class SendSolanaInputContext(
    val textPreprocessor: TextPreprocessor,
    val navController: NavController,
    val prefilledData: PrefilledData?,
    val addressCheckerControl: AddressCheckerControl,
)

@Composable
private fun rememberSendSolanaForm(
    amountUnique: AmountUnique?,
    onProceed: () -> Unit,
): SendSolanaForm {
    val focusRequester = remember { FocusRequester() }
    var percentageAmountUnique by remember { mutableStateOf<AmountUnique?>(null) }
    var coinAmount by remember { mutableStateOf<BigDecimal?>(null) }

    return SendSolanaForm(
        state = SendSolanaFormState(
            focusRequester = focusRequester,
            amountUnique = amountUnique,
            percentageAmountUnique = percentageAmountUnique,
            coinAmount = coinAmount,
        ),
        actions = SendSolanaFormActions(
            onCoinAmountChange = { coinAmount = it },
            onPercentageAmountUnique = { percentageAmountUnique = it },
            onProceed = onProceed,
        ),
    )
}

private fun handleSolanaProceed(
    view: View,
    state: SendSolanaScreenState,
    actions: SendSolanaScreenActions,
) {
    if (actions.hasConnection()) {
        actions.onNextClick(
            ProceedActionData(
                address = state.uiState.address?.hex,
                wallet = state.wallet,
                type = SendConfirmationFragment.Type.Solana,
            )
        )
    } else {
        HudHelper.showErrorMessage(view, R.string.Hud_Text_NoInternet)
    }
}

@Composable
@Suppress("MultipleEmitters")
private fun SendSolanaFormContent(
    state: SendSolanaScreenState,
    formState: SendSolanaFormState,
    inputContext: SendSolanaInputContext,
    actions: SendSolanaScreenActions,
    formActions: SendSolanaFormActions,
) {
    if (state.uiState.isPoisonAddress) {
        PoisonWarningCell()
        Spacer(modifier = Modifier.height(12.dp))
    }
    SendSolanaAddressInput(state, inputContext, actions)
    SendSolanaAmountInput(state, formState, actions, formActions)
    SendSolanaFeeInfo(state, actions)
    SendSolanaAddressChecks(state, inputContext, actions)
    SendSolanaButtons(state, actions, formActions)
}

@Composable
@Suppress("MultipleEmitters")
private fun SendSolanaAddressInput(
    state: SendSolanaScreenState,
    inputContext: SendSolanaInputContext,
    actions: SendSolanaScreenActions,
) {
    if (!state.uiState.showAddressInput) return

    val wallet = state.wallet
    HSAddressInput(
        modifier = Modifier.padding(horizontal = 16.dp),
        initial = inputContext.prefilledData?.address?.let { Address(it) },
        tokenQuery = wallet.token.tokenQuery,
        coinCode = wallet.coin.code,
        error = state.uiState.addressError,
        textPreprocessor = inputContext.textPreprocessor,
        navController = inputContext.navController,
        isPoisonAddress = state.uiState.isPoisonAddress,
        onValueChange = actions.onEnterAddress,
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun SendSolanaAmountInput(
    state: SendSolanaScreenState,
    formState: SendSolanaFormState,
    actions: SendSolanaScreenActions,
    formActions: SendSolanaFormActions,
) {
    HSAmountInput(
        modifier = Modifier.padding(horizontal = 16.dp),
        focusRequester = formState.focusRequester,
        availableBalance = state.uiState.availableBalance,
        caution = state.uiState.amountCaution,
        coinCode = state.wallet.coin.code,
        coinDecimal = state.coinMaxAllowedDecimals,
        fiatDecimal = state.fiatMaxAllowedDecimals,
        onClickHint = actions.onToggleInputType,
        onValueChange = {
            formActions.onCoinAmountChange(it)
            actions.onEnterAmount(it)
        },
        inputType = state.amountInputType,
        rate = state.coinRate,
        amountUnique = formState.amountUnique,
        percentageAmountUnique = formState.percentageAmountUnique,
    )
    VSpacer(height = 12.dp)
}

@Composable
private fun SendSolanaFeeInfo(
    state: SendSolanaScreenState,
    actions: SendSolanaScreenActions,
) {
    FeeInfoSection(
        tokenIn = state.wallet.token,
        displayBalance = state.displayBalance,
        balanceHidden = state.balanceHidden,
        feeToken = state.fee.token,
        feeCoinBalance = state.fee.balance,
        feePrimary = state.fee.feePrimary,
        feeSecondary = state.fee.feeSecondary,
        insufficientFeeBalance = state.fee.insufficientFeeBalance,
        onBalanceClicked = actions.onBalanceClicked,
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun SendSolanaAddressChecks(
    state: SendSolanaScreenState,
    inputContext: SendSolanaInputContext,
    actions: SendSolanaScreenActions,
) {
    SectionUniversalLawrence {
        SwitchWithText(
            text = stringResource(R.string.SettingsAddressChecker_RecipientCheck),
            checked = inputContext.addressCheckerControl.uiState.addressCheckByBaseEnabled,
            onCheckedChange = inputContext.addressCheckerControl::onCheckBaseAddressClick,
        )
    }
    SmartContractCheckSection(
        token = state.wallet.token,
        navController = inputContext.navController,
        addressCheckerControl = inputContext.addressCheckerControl,
        modifier = Modifier.padding(top = 8.dp),
    )
    PoisonAddressRiskSection(
        isPoisonAddress = state.uiState.isPoisonAddress,
        riskAccepted = state.uiState.riskAccepted,
        onRiskAcceptedChange = actions.onRiskAcceptedChange,
    )
}

@Composable
@Suppress("MultipleEmitters")
private fun SendSolanaButtons(
    state: SendSolanaScreenState,
    actions: SendSolanaScreenActions,
    formActions: SendSolanaFormActions,
) {
    val proceedEnabled = state.uiState.canBeSend
    ButtonPrimaryYellow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        title = stringResource(R.string.Send_DialogProceed),
        onClick = formActions.onProceed,
        enabled = proceedEnabled,
    )

    if (BuildConfig.SHOW_DEBUG_OFFLINE_SIGN_BUTTON) {
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            title = stringResource(R.string.offline_transaction_sign_title),
            onClick = actions.onDebugOfflineSignClick,
            enabled = state.offlineSignSupported && proceedEnabled,
        )
    }
}

private fun SendSolanaViewModel.toScreenState(amountInputType: AmountInputType) =
    SendSolanaScreenState(
        wallet = wallet,
        uiState = uiState,
        coinMaxAllowedDecimals = coinMaxAllowedDecimals,
        fiatMaxAllowedDecimals = fiatMaxAllowedDecimals,
        amountInputType = amountInputType,
        coinRate = coinRate,
        fee = SendSolanaFeeState(
            token = feeToken,
            balance = feeCoinBalance,
            feePrimary = formatFeePrimary(SolanaKit.fee),
            feeSecondary = formatFeeSecondary(SolanaKit.fee, feeCoinRate),
            insufficientFeeBalance = isInsufficientFeeBalance(SolanaKit.fee),
        ),
        displayBalance = displayBalance,
        balanceHidden = balanceHidden,
        offlineSignSupported = offlineSignSupported,
    )
