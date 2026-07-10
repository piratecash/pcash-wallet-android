package cash.p.terminal.modules.send.stellar

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.amount.AmountInputType
import cash.p.terminal.modules.amount.HSAmountInput
import cash.p.terminal.modules.fee.FeeInfoSection
import cash.p.terminal.modules.memo.HSMemoInput
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
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.CurrencyValue
import java.math.BigDecimal

@Composable
fun SendStellarNavHost(
    title: String,
    fragmentNavController: NavController,
    viewModel: SendStellarViewModel,
    amountInputModeViewModel: AmountInputModeViewModel,
    prefilledData: PrefilledData?,
    addressCheckerControl: AddressCheckerControl,
    onNextClick: (ProceedActionData) -> Unit,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = SendStellarPage,
    ) {
        composable(SendStellarPage) {
            SendStellarScreen(
                navController = fragmentNavController,
                prefilledData = prefilledData,
                addressCheckerControl = addressCheckerControl,
                state = SendStellarScreenState(
                    title = title,
                    wallet = viewModel.wallet,
                    uiState = viewModel.uiState,
                    amountInputType = amountInputModeViewModel.inputType,
                    coinMaxAllowedDecimals = viewModel.coinMaxAllowedDecimals,
                    fiatMaxAllowedDecimals = viewModel.fiatMaxAllowedDecimals,
                    coinRate = viewModel.coinRate,
                    displayBalance = viewModel.displayBalance,
                    balanceHidden = viewModel.balanceHidden,
                    feeToken = viewModel.feeToken,
                    feeCoinBalance = viewModel.feeCoinBalance,
                    feePrimary = viewModel.formatFeePrimary(viewModel.uiState.fee),
                    feeSecondary = viewModel.formatFeeSecondary(viewModel.uiState.fee, viewModel.feeCoinRate),
                    insufficientFeeBalance = viewModel.isInsufficientFeeBalance(viewModel.uiState.fee),
                    offlineSignSupported = viewModel.offlineSignSupported,
                ),
                callbacks = SendStellarScreenCallbacks(
                    onDebugOfflineSignClick = { navController.navigate(DebugOfflineStellarSignPage) },
                    onNextClick = onNextClick,
                    onEnterAddress = viewModel::onEnterAddress,
                    onEnterAmount = viewModel::onEnterAmount,
                    onEnterMemo = viewModel::onEnterMemo,
                    onToggleAmountInputType = amountInputModeViewModel::onToggleInputType,
                    onToggleHideBalance = viewModel::toggleHideBalance,
                    onRiskAcceptedChange = viewModel::onRiskAcceptedChange,
                ),
            )
        }
        offlineSignFlowRoutes(
            routes = OfflineSignFlowRoutes(
                signRoute = DebugOfflineStellarSignPage,
                transferRoute = DebugOfflineStellarTransactionTransferPage,
                transferFormatArgument = DebugOfflineTransactionTransferFormatArg,
            ),
            navController = navController,
            fragmentNavController = fragmentNavController,
            sendViewModel = viewModel,
        )
    }
}

private const val SendStellarPage = "send_stellar"
private const val DebugOfflineStellarSignPage = "debug_offline_stellar_sign"
private const val DebugOfflineStellarTransactionTransferPage = "debug_offline_stellar_transaction_transfer"
private const val DebugOfflineTransactionTransferFormatArg = "format"

@Composable
private fun SendStellarScreen(
    navController: NavController,
    prefilledData: PrefilledData?,
    addressCheckerControl: AddressCheckerControl,
    state: SendStellarScreenState,
    callbacks: SendStellarScreenCallbacks,
) {
    val paymentAddressViewModel: AddressParserViewModel = viewModel(
        factory = AddressParserModule.Factory(state.wallet.token, prefilledData)
    )
    val addressTextPreprocessor: TextPreprocessor = paymentAddressViewModel

    ComposeAppTheme {
        SendStellarContent(
            navController = navController,
            prefilledData = prefilledData,
            addressCheckerControl = addressCheckerControl,
            state = state,
            callbacks = callbacks,
            addressTextPreprocessor = addressTextPreprocessor,
            amountUnique = paymentAddressViewModel.amountUnique,
        )
    }
}

private data class SendStellarScreenState(
    val title: String,
    val wallet: Wallet,
    val uiState: SendStellarUiState,
    val amountInputType: AmountInputType,
    val coinMaxAllowedDecimals: Int,
    val fiatMaxAllowedDecimals: Int,
    val coinRate: CurrencyValue?,
    val displayBalance: BigDecimal?,
    val balanceHidden: Boolean,
    val feeToken: Token?,
    val feeCoinBalance: BigDecimal?,
    val feePrimary: String,
    val feeSecondary: String,
    val insufficientFeeBalance: Boolean,
    val offlineSignSupported: Boolean,
)

private data class SendStellarScreenCallbacks(
    val onDebugOfflineSignClick: () -> Unit,
    val onNextClick: (ProceedActionData) -> Unit,
    val onEnterAddress: (Address?) -> Unit,
    val onEnterAmount: (BigDecimal?) -> Unit,
    val onEnterMemo: (String) -> Unit,
    val onToggleAmountInputType: () -> Unit,
    val onToggleHideBalance: () -> Unit,
    val onRiskAcceptedChange: (Boolean) -> Unit,
)

@Composable
private fun SendStellarContent(
    navController: NavController,
    prefilledData: PrefilledData?,
    addressCheckerControl: AddressCheckerControl,
    state: SendStellarScreenState,
    callbacks: SendStellarScreenCallbacks,
    addressTextPreprocessor: TextPreprocessor,
    amountUnique: AmountUnique?,
) {
    val focusRequester = remember { FocusRequester() }
    var percentageAmountUnique by remember { mutableStateOf<AmountUnique?>(null) }
    var coinAmount by remember { mutableStateOf<BigDecimal?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    SendScreen(
        title = state.title,
        onCloseClick = { navController.popBackStackSafely() },
        proceedEnabled = state.uiState.canBeSend,
        onSendClick = { callbacks.onNextClick(state.uiState.proceedActionData(state.wallet)) },
        bottomOverlay = {
            StellarSuggestionsBar(
                state = state,
                coinAmount = coinAmount,
                onAmountChange = { amount ->
                    coinAmount = amount
                    callbacks.onEnterAmount(amount)
                },
                onPercentageAmountUnique = { percentageAmountUnique = it },
            )
        }
    ) {
        StellarAddressSection(
            state = state,
            prefilledData = prefilledData,
            textPreprocessor = addressTextPreprocessor,
            navController = navController,
            onValueChange = callbacks.onEnterAddress,
        )
        StellarAmountSection(
            state = state,
            focusRequester = focusRequester,
            amountUnique = amountUnique,
            percentageAmountUnique = percentageAmountUnique,
            onAmountChange = { amount ->
                coinAmount = amount
                callbacks.onEnterAmount(amount)
            },
            onToggleInputType = callbacks.onToggleAmountInputType,
        )
        VSpacer(12.dp)
        HSMemoInput(maxLength = 120, onValueChange = callbacks.onEnterMemo)
        VSpacer(12.dp)
        StellarFeeAndRiskSections(
            state = state,
            navController = navController,
            addressCheckerControl = addressCheckerControl,
            onBalanceClick = callbacks.onToggleHideBalance,
            onRiskAcceptedChange = callbacks.onRiskAcceptedChange,
        )
        StellarProceedButtons(state, callbacks)
    }
}

@Composable
private fun BoxScope.StellarSuggestionsBar(
    state: SendStellarScreenState,
    coinAmount: BigDecimal?,
    onAmountChange: (BigDecimal?) -> Unit,
    onPercentageAmountUnique: (AmountUnique?) -> Unit,
) {
    SendSuggestionsBar(
        availableBalance = state.uiState.availableBalance ?: BigDecimal.ZERO,
        coinDecimal = state.coinMaxAllowedDecimals,
        coinAmount = coinAmount,
        onAmountChange = onAmountChange,
        onPercentageAmountUnique = onPercentageAmountUnique,
    )
}

@Composable
private fun StellarAddressSection(
    state: SendStellarScreenState,
    prefilledData: PrefilledData?,
    textPreprocessor: TextPreprocessor,
    navController: NavController,
    onValueChange: (Address?) -> Unit,
) {
    Column {
        if (state.uiState.isPoisonAddress) {
            PoisonWarningCell()
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.uiState.showAddressInput) {
            HSAddressInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = prefilledData?.address?.let { Address(it) },
                tokenQuery = state.wallet.token.tokenQuery,
                coinCode = state.wallet.coin.code,
                error = state.uiState.addressError,
                textPreprocessor = textPreprocessor,
                navController = navController,
                isPoisonAddress = state.uiState.isPoisonAddress,
                onValueChange = onValueChange,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StellarAmountSection(
    state: SendStellarScreenState,
    focusRequester: FocusRequester,
    amountUnique: AmountUnique?,
    percentageAmountUnique: AmountUnique?,
    onAmountChange: (BigDecimal?) -> Unit,
    onToggleInputType: () -> Unit,
) {
    HSAmountInput(
        modifier = Modifier.padding(horizontal = 16.dp),
        focusRequester = focusRequester,
        availableBalance = state.uiState.availableBalance ?: BigDecimal.ZERO,
        caution = state.uiState.amountCaution,
        coinCode = state.wallet.coin.code,
        coinDecimal = state.coinMaxAllowedDecimals,
        fiatDecimal = state.fiatMaxAllowedDecimals,
        onClickHint = onToggleInputType,
        onValueChange = onAmountChange,
        inputType = state.amountInputType,
        rate = state.coinRate,
        amountUnique = amountUnique,
        percentageAmountUnique = percentageAmountUnique,
    )
}

@Composable
private fun StellarFeeAndRiskSections(
    state: SendStellarScreenState,
    navController: NavController,
    addressCheckerControl: AddressCheckerControl,
    onBalanceClick: () -> Unit,
    onRiskAcceptedChange: (Boolean) -> Unit,
) {
    Column {
        FeeInfoSection(
            tokenIn = state.wallet.token,
            displayBalance = state.displayBalance,
            balanceHidden = state.balanceHidden,
            feeToken = state.feeToken,
            feeCoinBalance = state.feeCoinBalance,
            feePrimary = state.feePrimary,
            feeSecondary = state.feeSecondary,
            insufficientFeeBalance = state.insufficientFeeBalance,
            onBalanceClicked = onBalanceClick,
        )

        Spacer(modifier = Modifier.height(12.dp))
        SectionUniversalLawrence {
            SwitchWithText(
                text = stringResource(R.string.SettingsAddressChecker_RecipientCheck),
                checked = addressCheckerControl.uiState.addressCheckByBaseEnabled,
                onCheckedChange = addressCheckerControl::onCheckBaseAddressClick
            )
        }
        SmartContractCheckSection(
            token = state.wallet.token,
            navController = navController,
            addressCheckerControl = addressCheckerControl,
            modifier = Modifier.padding(top = 8.dp)
        )
        PoisonAddressRiskSection(
            isPoisonAddress = state.uiState.isPoisonAddress,
            riskAccepted = state.uiState.riskAccepted,
            onRiskAcceptedChange = onRiskAcceptedChange,
        )
    }
}

@Composable
private fun StellarProceedButtons(
    state: SendStellarScreenState,
    callbacks: SendStellarScreenCallbacks,
) {
    Column {
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            title = stringResource(R.string.Button_Next),
            onClick = { callbacks.onNextClick(state.uiState.proceedActionData(state.wallet)) },
            enabled = state.uiState.canBeSend
        )

        if (BuildConfig.SHOW_DEBUG_OFFLINE_SIGN_BUTTON) {
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = stringResource(R.string.offline_transaction_sign_title),
                onClick = callbacks.onDebugOfflineSignClick,
                enabled = state.offlineSignSupported && state.uiState.canBeSend,
            )
        }
    }
}

private fun SendStellarUiState.proceedActionData(wallet: Wallet) =
    ProceedActionData(
        address = address?.hex,
        wallet = wallet,
        type = SendConfirmationFragment.Type.Stellar,
    )
