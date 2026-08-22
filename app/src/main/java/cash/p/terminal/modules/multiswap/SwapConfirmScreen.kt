package cash.p.terminal.modules.multiswap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.core.requiresTrezorPreparation
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.modules.confirm.ConfirmTransactionScreen
import cash.p.terminal.modules.evmfee.Cautions
import cash.p.terminal.modules.fee.FeeInfoSection
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.ui.SwapProviderField
import cash.p.terminal.modules.multiswap.exchanges.MultiSwapExchangesFragment
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.SendResultHud
import cash.p.terminal.modules.send.hasInsufficientFeeTokenBalance
import cash.p.terminal.modules.send.fee.NetworkFeeWarningOverlay
import cash.p.terminal.modules.send.fee.NetworkFeeWarningData
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui.compose.components.CoinImage
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsImageCircle
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.imageUrl
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.delay
import java.math.BigDecimal

data class SwapConfirmNavigation(
    val fragment: NavController,
    val swap: NavController,
)

data class SwapConfirmQuoteParams(
    val quote: SwapProviderQuote,
    val settings: Map<String, Any?>,
    val direction: SwapAmountDirection,
    val requestedAmountOut: BigDecimal?,
    val multiSwapLegInfo: MultiSwapLegInfo?,
)

data class SwapConfirmBalanceParams(
    val provider: IMultiSwapProvider?,
    val displayBalance: BigDecimal?,
    val balanceHidden: Boolean,
    val feeToken: Token?,
    val feeCoinBalance: BigDecimal?,
)

@Composable
fun SwapConfirmScreen(
    navigation: SwapConfirmNavigation,
    quoteParams: SwapConfirmQuoteParams,
    balanceParams: SwapConfirmBalanceParams,
    onToggleHideBalance: () -> Unit,
    onReapprove: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val viewModel = swapConfirmViewModel(navigation, quoteParams)
    val uiState = viewModel.uiState
    val hasInsufficientFeeBalance = hasInsufficientFeeTokenBalance(
        token = uiState.tokenIn,
        fee = uiState.networkFee?.primary?.value,
        feeTokenBalance = balanceParams.feeCoinBalance,
    ) || viewModel.isInsufficientFeeBalance(uiState.networkFee?.primary?.value)
    val hasFeeProblem = hasSwapConfirmFeeProblem(
        hasInsufficientFeeBalance = hasInsufficientFeeBalance,
        hasFeeCaution = uiState.feeCaution != null,
    )
    val actions = SwapConfirmActions(
        refresh = viewModel::refresh,
        reapprove = onReapprove,
        retryAdapter = viewModel::retryAdapterSync,
        send = viewModel::onClickSendWithWarningCheck,
        retryMoneroPreparation = viewModel::retryMoneroPreparation,
        toggleMevProtection = viewModel::toggleMevProtection,
    )
    val runtime = SwapConfirmRuntime(
        isSynced = viewModel.isSynced,
        hasAdapterError = viewModel.hasAdapterError,
        sendResult = viewModel.sendResult,
        inlineFeeWarningData = viewModel.inlineFeeWarningData,
    )

    SwapResultEffects(viewModel, navigation, quoteParams.multiSwapLegInfo)

    ConfirmTransactionScreen(
        onClickBack = navigation.swap::navigateUpSafely,
        onClickSettings = if (uiState.isAdvancedSettingsAvailable && onOpenSettings != null) {
            { onOpenSettings.invoke() }
        } else {
            null
        },
        onClickClose = null,
        buttonsSlot = {
            SwapConfirmButtons(uiState, runtime, actions, hasFeeProblem)
        }
    ) {
        SwapConfirmContent(
            uiState,
            navigation,
            balanceParams,
            runtime.inlineFeeWarningData,
            actions,
            hasFeeProblem,
            onToggleHideBalance,
        )
    }

    NetworkFeeWarningOverlay(
        feeWarningData = viewModel.feeWarningData,
        onConfirm = viewModel::onFeeWarningConfirmed,
        onCancel = viewModel::onFeeWarningCancelled,
    )
}

@Composable
private fun swapConfirmViewModel(
    navigation: SwapConfirmNavigation,
    params: SwapConfirmQuoteParams,
): SwapConfirmViewModel {
    val backStackEntry = remember { navigation.swap.currentBackStackEntry }
    return viewModel(
        viewModelStoreOwner = requireNotNull(backStackEntry),
        factory = SwapConfirmViewModel.provideFactory(
            quote = params.quote,
            settings = params.settings,
            navController = navigation.fragment,
            direction = params.direction,
            requestedAmountOut = params.requestedAmountOut,
            multiSwapLegInfo = params.multiSwapLegInfo,
        ),
    )
}

@Composable
private fun SwapResultEffects(
    viewModel: SwapConfirmViewModel,
    navigation: SwapConfirmNavigation,
    multiSwapLegInfo: MultiSwapLegInfo?,
) {
    val sendResult = viewModel.sendResult
    SendResultHud(
        sendResult = sendResult,
        sendingTextRes = R.string.Swap_Swapping,
        successTextRes = R.string.Hud_Text_Done,
    )

    // Handle navigation after success
    LaunchedEffect(sendResult) {
        if (sendResult !is SendResult.Sent && sendResult !is SendResult.SentButQueued) return@LaunchedEffect
        delay(1200)
        val multiSwapId = viewModel.completedMultiSwapId
        when {
            multiSwapId != null && multiSwapLegInfo is MultiSwapLegInfo.Leg1 -> {
                navigation.fragment.popBackStack(R.id.multiswap, inclusive = true)
                navigation.fragment.slideFromRight(
                    R.id.multiSwapExchanges,
                    MultiSwapExchangesFragment.ARG_PENDING_MULTI_SWAP_ID to multiSwapId,
                )
            }
            multiSwapLegInfo is MultiSwapLegInfo.Leg2 ->
                navigation.fragment.popBackStack(R.id.multiSwapExchanges, inclusive = true)
            else -> navigation.fragment.navigateUp()
        }
    }
}

@Composable
private fun ColumnScope.SwapConfirmButtons(
    uiState: SwapConfirmUiState,
    runtime: SwapConfirmRuntime,
    actions: SwapConfirmActions,
    hasFeeProblem: Boolean,
) {
    val hasErrorCaution = uiState.cautions.any { it.type == CautionViewItem.Type.Error }
    val moneroSpendReadiness = uiState.moneroSpendReadiness
    when {
        uiState.loading -> SwapLoadingButton()
        uiState.criticalError != null -> RefreshSwapButton(uiState.criticalError, actions.refresh)
        moneroSpendReadiness != null && moneroSpendReadiness != MoneroSpendReadiness.Ready ->
            MoneroSpendReadinessStatus(
                spendReadiness = moneroSpendReadiness,
                preparationInProgress = uiState.moneroPreparationInProgress,
                preparationError = uiState.moneroPreparationError,
                preparationRetryAvailable = uiState.moneroPreparationRetryAvailable,
                onRetry = actions.retryMoneroPreparation,
            )
        !uiState.validQuote -> InvalidQuoteButton(uiState, hasErrorCaution, actions)
        uiState.expired -> ExpiredQuoteButton(actions.refresh)
        else -> ReadySwapButton(uiState, runtime, actions, hasFeeProblem, hasErrorCaution)
    }
}

@Composable
private fun SwapLoadingButton() {
    ButtonPrimaryYellow(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.Alert_Loading),
        enabled = false,
        onClick = {},
    )
    VSpacer(height = 12.dp)
    subhead1_leah(text = stringResource(R.string.SwapConfirm_FetchingFinalQuote))
}

@Composable
private fun RefreshSwapButton(title: String, onRefresh: () -> Unit) {
    ButtonPrimaryDefault(modifier = Modifier.fillMaxWidth(), title = title, onClick = onRefresh)
    VSpacer(height = 12.dp)
}

@Composable
private fun InvalidQuoteButton(
    uiState: SwapConfirmUiState,
    hasErrorCaution: Boolean,
    actions: SwapConfirmActions,
) {
    val title = if (uiState.reapprovalRequired) R.string.swap_reapprove_action else R.string.Button_Refresh
    ButtonPrimaryDefault(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(title),
        onClick = if (uiState.reapprovalRequired) actions.reapprove else actions.refresh,
    )
    VSpacer(height = 12.dp)
    // A concrete estimation error is already shown by Cautions in the scrollable content; fall
    // back to the generic text only when there is none, so the real reason is not obscured.
    if (!hasErrorCaution) {
        subhead1_leah(text = stringResource(R.string.SwapConfirm_QuoteIsInvalid))
    }
}

@Composable
private fun ExpiredQuoteButton(onRefresh: () -> Unit) {
    RefreshSwapButton(stringResource(R.string.Button_Refresh), onRefresh)
    subhead1_leah(text = stringResource(R.string.SwapConfirm_QuoteExpired))
}

@Composable
private fun ColumnScope.MoneroSpendReadinessStatus(
    spendReadiness: MoneroSpendReadiness,
    preparationInProgress: Boolean,
    preparationError: Int?,
    preparationRetryAvailable: Boolean,
    onRetry: () -> Unit,
) {
    TextImportantWarning(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(
            if (spendReadiness.requiresTrezorPreparation()) {
                R.string.monero_prepare_trezor_description
            } else {
                R.string.send_confirmation_syncing_warning
            },
        ),
    )
    when {
        preparationInProgress -> {
            VSpacer(height = 8.dp)
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.monero_updating_with_trezor),
                enabled = false,
                loadingIndicator = true,
                onClick = {},
            )
        }

        preparationRetryAvailable -> {
            preparationError?.let { error ->
                TextImportantWarning(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(error),
                )
            }
            VSpacer(height = 8.dp)
            ButtonPrimaryDefault(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.Button_Retry),
                onClick = onRetry,
            )
        }
    }
    VSpacer(height = 12.dp)
}

@Composable
private fun ReadySwapButton(
    uiState: SwapConfirmUiState,
    runtime: SwapConfirmRuntime,
    actions: SwapConfirmActions,
    hasFeeProblem: Boolean,
    hasErrorCaution: Boolean,
) {
    Column {
        AdapterStatus(runtime, actions.retryAdapter)
        // Disable button during swap and navigation delay (allow retry only on Failed).
        val swapInProgress = runtime.sendResult?.let { it !is SendResult.Failed } == true
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.Swap),
            enabled = isSwapConfirmButtonEnabled(
                isSynced = runtime.isSynced,
                swapInProgress = swapInProgress,
                hasRequiredQuoteData = uiState.amountOut != null && uiState.networkFee != null,
                hasBlockingFeeState = hasFeeProblem,
                hasErrorCaution = hasErrorCaution,
            ),
            onClick = actions.send,
        )
        uiState.expiresIn?.let {
            VSpacer(height = 12.dp)
            subhead1_leah(text = stringResource(R.string.SwapConfirm_QuoteExpiresIn, it))
        }
    }
}

@Composable
private fun AdapterStatus(runtime: SwapConfirmRuntime, onRetry: () -> Unit) {
    Column {
        when {
            runtime.hasAdapterError -> {
                TextImportantWarning(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.send_confirmation_sync_error_warning),
                )
                VSpacer(height = 8.dp)
                ButtonPrimaryDefault(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.Button_Retry),
                    onClick = onRetry,
                )
                VSpacer(height = 12.dp)
            }
            !runtime.isSynced -> {
                TextImportantWarning(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.send_confirmation_syncing_warning),
                )
                VSpacer(height = 12.dp)
            }
        }
    }
}

@Composable
private fun SwapConfirmContent(
    uiState: SwapConfirmUiState,
    navigation: SwapConfirmNavigation,
    balanceParams: SwapConfirmBalanceParams,
    inlineFeeWarningData: NetworkFeeWarningData?,
    actions: SwapConfirmActions,
    hasFeeProblem: Boolean,
    onToggleHideBalance: () -> Unit,
) {
    Column {
        SwapAmountsSection(uiState)
        SwapQuoteSection(uiState, balanceParams.provider, navigation.fragment)
        SwapTransactionFields(uiState, navigation.fragment)
        SwapConfirmFeeInfo(
            uiState,
            balanceParams,
            inlineFeeWarningData,
            hasFeeProblem,
            onToggleHideBalance,
        )
        MevProtectionSection(uiState, actions.toggleMevProtection)
        if (uiState.cautions.isNotEmpty()) Cautions(cautions = uiState.cautions)
    }
}

@Composable
private fun SwapAmountsSection(uiState: SwapConfirmUiState) {
    SectionUniversalLawrence {
        TokenRow(
            token = uiState.tokenIn,
            amount = uiState.amountIn,
            fiatAmount = uiState.fiatAmountIn,
            currency = uiState.currency,
            borderTop = false,
            title = stringResource(R.string.Send_Confirmation_YouSend),
            amountColor = ComposeAppTheme.colors.leah,
        )
        TokenRow(
            token = uiState.tokenOut,
            amount = uiState.amountOut,
            fiatAmount = uiState.fiatAmountOut,
            currency = uiState.currency,
            title = stringResource(R.string.Swap_ToAmountTitle),
            amountColor = ComposeAppTheme.colors.remus,
        )
    }
}

@Composable
private fun SwapQuoteSection(
    uiState: SwapConfirmUiState,
    provider: IMultiSwapProvider?,
    navController: NavController,
) {
    val amountOut = uiState.amountOut ?: return
    VSpacer(height = 16.dp)
    SectionUniversalLawrence {
        PriceField(uiState.tokenIn, uiState.tokenOut, uiState.amountIn, amountOut)
        PriceImpactField(uiState.priceImpact, uiState.priceImpactLevel)
        uiState.amountOutMin?.let {
            val fiat = uiState.fiatAmountOutMin?.let { value ->
                CurrencyValue(uiState.currency, value).getFormattedFull()
            } ?: "---"
            SwapInfoRow(
                borderTop = true,
                title = stringResource(R.string.Swap_MinimumReceived),
                value = CoinValue(uiState.tokenOut, it).getFormattedFull(),
                subvalue = fiat,
            )
        }
        provider?.let { SwapProviderField(title = it.title, iconId = it.icon) }
        uiState.quoteFields.forEach { it.GetContent(navController, true) }
    }
}

@Composable
private fun SwapTransactionFields(uiState: SwapConfirmUiState, navController: NavController) {
    if (uiState.transactionFields.isEmpty()) return
    VSpacer(height = 16.dp)
    SectionUniversalLawrence {
        uiState.transactionFields.forEachIndexed { index, field ->
            field.GetContent(navController, index != 0)
        }
    }
}

@Composable
private fun SwapConfirmFeeInfo(
    uiState: SwapConfirmUiState,
    balance: SwapConfirmBalanceParams,
    inlineFeeWarningData: NetworkFeeWarningData?,
    hasFeeProblem: Boolean,
    onToggleHideBalance: () -> Unit,
) {
    VSpacer(height = 16.dp)
    FeeInfoSection(
        tokenIn = uiState.tokenIn,
        displayBalance = balance.displayBalance,
        balanceHidden = balance.balanceHidden,
        feeToken = balance.feeToken,
        feeCoinBalance = balance.feeCoinBalance,
        feePrimary = uiState.networkFee?.primary?.getFormattedPlain() ?: "---",
        feeSecondary = uiState.networkFee?.secondary?.getFormattedPlain() ?: "---",
        insufficientFeeBalance = hasFeeProblem,
        onBalanceClicked = onToggleHideBalance,
        feeWarningData = inlineFeeWarningData,
    )
}

@Composable
private fun MevProtectionSection(
    uiState: SwapConfirmUiState,
    onToggleMevProtection: (Boolean) -> Unit,
) {
    if (!uiState.mevProtectionAvailable) return
    VSpacer(16.dp)
    SectionUniversalLawrence {
        CellUniversal {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(R.drawable.ic_shield_24),
                contentDescription = null,
                tint = ComposeAppTheme.colors.jacob,
            )
            HSpacer(width = 16.dp)
            body_leah(text = stringResource(R.string.mev_protection))
            HFillSpacer(minWidth = 8.dp)
            HsSwitch(
                checked = uiState.mevProtectionEnabled,
                onCheckedChange = onToggleMevProtection,
            )
        }
    }
}

private data class SwapConfirmActions(
    val refresh: () -> Unit,
    val reapprove: () -> Unit,
    val retryAdapter: () -> Unit,
    val send: () -> Unit,
    val retryMoneroPreparation: () -> Unit,
    val toggleMevProtection: (Boolean) -> Unit,
)

private data class SwapConfirmRuntime(
    val isSynced: Boolean,
    val hasAdapterError: Boolean,
    val sendResult: SendResult?,
    val inlineFeeWarningData: NetworkFeeWarningData?,
)

internal fun hasSwapConfirmFeeProblem(
    hasInsufficientFeeBalance: Boolean,
    hasFeeCaution: Boolean,
): Boolean {
    return hasInsufficientFeeBalance || hasFeeCaution
}

internal fun isSwapConfirmButtonEnabled(
    isSynced: Boolean,
    swapInProgress: Boolean,
    hasRequiredQuoteData: Boolean,
    hasBlockingFeeState: Boolean,
    hasErrorCaution: Boolean,
): Boolean {
    return isSynced &&
        !swapInProgress &&
        hasRequiredQuoteData &&
        !hasBlockingFeeState &&
        !hasErrorCaution
}

@Composable
private fun SwapInfoRow(
    borderTop: Boolean,
    title: String,
    value: String,
    subvalue: String? = null
) {
    CellUniversal(borderTop = borderTop) {
        subhead2_grey(text = title)
        HFillSpacer(minWidth = 16.dp)
        Column(horizontalAlignment = Alignment.End) {
            subhead2_leah(text = value)
            subvalue?.let {
                VSpacer(height = 1.dp)
                caption_grey(text = it)
            }
        }
    }
}

@Composable
fun TokenRow(
    token: Token,
    amount: BigDecimal?,
    fiatAmount: BigDecimal?,
    currency: Currency,
    borderTop: Boolean = true,
    title: String,
    amountColor: Color,
) = TokenRowPure(
    fiatAmount,
    borderTop,
    currency,
    title,
    amountColor,
    token.coin.imageUrl,
    token.coin.alternativeImageUrl,
    token.iconPlaceholder,
    token.badge,
    amount?.let { CoinValue(token, it).getFormattedFull() }
)

@Composable
fun TokenRowPure(
    fiatAmount: BigDecimal?,
    borderTop: Boolean = true,
    currency: Currency,
    title: String,
    amountColor: Color,
    imageUrl: String?,
    alternativeImageUrl: String?,
    imagePlaceholder: Int?,
    badge: String?,
    amountFormatted: String?,
) {
    CellUniversal(borderTop = borderTop) {
        HsImageCircle(
            modifier = Modifier.size(32.dp),
            url = imageUrl,
            alternativeUrl = alternativeImageUrl,
            placeholder = imagePlaceholder
        )
        HSpacer(width = 16.dp)
        Column {
            subhead2_leah(text = title)
            VSpacer(height = 1.dp)
            caption_grey(text = badge ?: stringResource(id = R.string.CoinPlatforms_Native))
        }
        HFillSpacer(minWidth = 16.dp)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountFormatted ?: "---",
                style = ComposeAppTheme.typography.subhead1,
                color = amountColor,
            )
            fiatAmount?.let {
                VSpacer(height = 1.dp)
                caption_grey(text = CurrencyValue(currency, fiatAmount).getFormattedFull())
            }
        }
    }
}

@Composable
fun TokenRowUnlimited(
    token: Token,
    borderTop: Boolean = true,
    title: String,
    amountColor: Color,
) {
    CellUniversal(borderTop = borderTop) {
        CoinImage(
            token = token,
            modifier = Modifier.size(32.dp)
        )
        HSpacer(width = 16.dp)
        Column {
            subhead2_leah(text = title)
            VSpacer(height = 1.dp)
            caption_grey(text = token.badge ?: stringResource(id = R.string.CoinPlatforms_Native))
        }
        HFillSpacer(minWidth = 16.dp)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "∞ ${token.coin.code}",
                style = ComposeAppTheme.typography.subhead1,
                color = amountColor,
            )
            VSpacer(height = 1.dp)
            caption_grey(text = stringResource(id = R.string.Transaction_Unlimited))
        }
    }
}
