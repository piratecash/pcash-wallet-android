package cash.p.terminal.modules.paycore.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.multiswap.PriceField
import cash.p.terminal.modules.multiswap.TokenRowPure
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.modules.paycore.PAYCORE_COMPLETE_BACK_URL
import cash.p.terminal.modules.paycore.PayCoreTicker
import cash.p.terminal.modules.paycore.formatPayCoreServiceFee
import cash.p.terminal.modules.paycore.webview.PayCoreWebViewScreen
import cash.p.terminal.ui.compose.components.CardsSwapInfo
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.imageUrl
import io.horizontalsystems.core.entities.Currency
import java.math.BigDecimal

data class PayCorePaymentDisplayParams(
    val amountIn: BigDecimal,
    val amountOut: BigDecimal,
    val serviceFee: BigDecimal,
    val networkType: PayCoreTicker,
    val tokenIn: Token,
    val tokenOut: Token,
    val currency: Currency,
)

@Composable
fun PayCorePaymentScreen(
    uiState: PayCorePaymentUiState,
    displayParams: PayCorePaymentDisplayParams,
    onConfirm: () -> Unit,
    onOpenWebView: () -> Unit,
    onCompleteWebView: () -> Unit,
    onCloseWebView: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paymentUrl = uiState.paymentUrl
    val paymentId = uiState.paymentId
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenWebView by rememberUpdatedState(onOpenWebView)
    val currentOnCloseWebView by rememberUpdatedState(onCloseWebView)

    if (uiState.showWebView && paymentUrl != null && paymentId != null && !uiState.completed) {
        LaunchedEffect(paymentId, paymentUrl) {
            currentOnOpenWebView()
        }
        PayCoreWebViewScreen(
            url = paymentUrl,
            title = stringResource(R.string.paycore_verification_title),
            onClose = currentOnCloseWebView,
            onInterceptBackUrl = onCompleteWebView,
            backUrlPrefix = PAYCORE_COMPLETE_BACK_URL
        )
        return
    }

    LaunchedEffect(uiState.completed) {
        if (uiState.completed) currentOnClose()
    }

    Scaffold(
        modifier = modifier,
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.Swap_Confirm_Title),
                navigationIcon = { HsBackButton(onClick = onClose) }
            )
        }
    ) { paddingValues ->
        PayCorePaymentContent(
            uiState = uiState,
            displayParams = displayParams,
            paddingValues = paddingValues,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun PayCorePaymentContent(
    uiState: PayCorePaymentUiState,
    displayParams: PayCorePaymentDisplayParams,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
    ) {
        VSpacer(height = 12.dp)
        SwapInfoCards(displayParams = displayParams)
        uiState.error?.let { error ->
            VSpacer(height = 8.dp)
            TextImportantError(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                text = error
            )
        }
        VSpacer(height = 24.dp)
        ButtonPrimaryYellow(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            title = if (uiState.loading) {
                stringResource(R.string.Alert_Loading)
            } else {
                stringResource(R.string.multi_swap_continue)
            },
            enabled = !uiState.loading,
            loadingIndicator = uiState.loading,
            onClick = onConfirm
        )
        VSpacer(height = 32.dp)
    }
}

@Composable
private fun SwapInfoCards(displayParams: PayCorePaymentDisplayParams) {
    CardsSwapInfo {
        TokenRowPure(
            fiatAmount = null,
            borderTop = false,
            currency = displayParams.currency,
            title = stringResource(R.string.Swap_YouPay),
            amountColor = ComposeAppTheme.colors.lucian,
            imageUrl = displayParams.tokenIn.coin.imageUrl,
            alternativeImageUrl = displayParams.tokenIn.coin.alternativeImageUrl,
            imagePlaceholder = null,
            badge = displayParams.tokenIn.badge,
            amountFormatted = "${displayParams.amountIn.toPlainString()} ${displayParams.tokenIn.coin.code}"
        )
        TokenRowPure(
            fiatAmount = null,
            borderTop = true,
            currency = displayParams.currency,
            title = stringResource(R.string.Swap_YouGet),
            amountColor = ComposeAppTheme.colors.remus,
            imageUrl = displayParams.tokenOut.coin.imageUrl,
            alternativeImageUrl = displayParams.tokenOut.coin.alternativeImageUrl,
            imagePlaceholder = null,
            badge = displayParams.tokenOut.badge,
            amountFormatted = "${displayParams.amountOut.toPlainString()} ${displayParams.tokenOut.coin.code}"
        )
        ServiceRow()
        PriceField(
            tokenIn = displayParams.tokenIn,
            tokenOut = displayParams.tokenOut,
            amountIn = displayParams.amountIn,
            amountOut = displayParams.amountOut,
            borderTop = true
        )
        ServiceFeeRow(serviceFee = displayParams.serviceFee)
    }
}

@Composable
private fun ServiceRow() {
    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(R.string.paycore_service)) },
        value = { subhead2_leah(text = "PayCore") }
    )
}

@Composable
private fun ServiceFeeRow(serviceFee: BigDecimal) {
    if (serviceFee <= BigDecimal.ZERO) return

    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(R.string.paycore_service_fee)) },
        value = { subhead2_leah(text = formatPayCoreServiceFee(serviceFee)) }
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PayCorePaymentScreenPreview() {
    // PriceField uses App.numberFormatter (Koin) which is unavailable in Preview.
    // Preview only the static parts: token rows, service info, button.
    ComposeAppTheme {
        Column(modifier = Modifier.background(ComposeAppTheme.colors.tyler)) {
            TokenRowPure(
                fiatAmount = null,
                currency = Currency("USD", "$", 2, 0),
                title = stringResource(R.string.Send_Confirmation_YouSend),
                amountColor = ComposeAppTheme.colors.lucian,
                imageUrl = "https://p.cash/storage/coins/rub/image.png",
                alternativeImageUrl = null,
                imagePlaceholder = null,
                badge = null,
                amountFormatted = "1000 RUB"
            )
            TokenRowPure(
                fiatAmount = null,
                borderTop = true,
                currency = Currency("USD", "$", 2, 0),
                title = stringResource(R.string.Swap_ToAmountTitle),
                amountColor = ComposeAppTheme.colors.remus,
                imageUrl = null,
                alternativeImageUrl = null,
                imagePlaceholder = null,
                badge = "TRC20",
                amountFormatted = "10.5 USDT"
            )
            ServiceRow()
            ServiceFeeRow(serviceFee = BigDecimal("2"))
        }
    }
}
