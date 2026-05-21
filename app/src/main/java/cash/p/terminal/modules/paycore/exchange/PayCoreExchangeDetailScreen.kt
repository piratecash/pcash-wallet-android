package cash.p.terminal.modules.paycore.exchange

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.modules.paycore.selectbank.PayCoreSelectBankActionSection
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.components.subhead2_remus
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun PayCoreExchangeDetailScreen(
    uiState: PayCoreExchangeDetailUiState?,
    onBack: () -> Unit,
    onSelectBankClick: () -> Unit = {},
    selectBankLoading: Boolean = false,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.Swap),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        }
    ) { paddingValues ->
        val state = uiState ?: return@Scaffold

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(height = 12.dp)

            SectionUniversalLawrence {
                AmountRow(
                    title = stringResource(R.string.Swap_YouPay),
                    amount = state.amountSentFormatted,
                    isNegative = true,
                    borderTop = false,
                )
                AmountRow(
                    title = stringResource(R.string.Swap_YouGet),
                    amount = state.amountGotFormatted,
                    isNegative = false,
                    borderTop = true,
                )
                ServiceRow()
                PriceRow(
                    priceFormatted = state.priceFormatted,
                    priceInvFormatted = state.priceInvFormatted,
                )
                DateRow(dateFormatted = state.dateFormatted)
                StatusRow(status = state.status)
                TransactionIdRow(transactionId = state.transactionId)
            }

            if (state.status == TransactionStatusEnum.CREATED_OR_WAIT_USER) {
                VSpacer(height = 16.dp)
                PayCoreSelectBankActionSection(
                    onClick = onSelectBankClick,
                    showSpinner = selectBankLoading,
                )
            }

            VSpacer(height = 16.dp)

            SectionUniversalLawrence {
                SupportRow(url = state.supportUrl)
            }

            VSpacer(height = 32.dp)
        }
    }
}

@Composable
private fun AmountRow(
    title: String,
    amount: String,
    isNegative: Boolean,
    borderTop: Boolean,
) {
    QuoteInfoRow(
        borderTop = borderTop,
        title = { subhead2_grey(text = title) },
        value = {
            if (isNegative) {
                subhead2_lucian(text = amount, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                subhead2_remus(text = amount, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}

@Composable
private fun ServiceRow() {
    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(R.string.paycore_service)) },
        value = { subhead2_leah(text = SwapProvider.PAYCORE.title) }
    )
}

@Composable
private fun PriceRow(
    priceFormatted: String,
    priceInvFormatted: String,
) {
    var showRegular by remember { mutableStateOf(true) }
    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(R.string.Swap_Price)) },
        value = {
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showRegular = !showRegular }
                )
            ) {
                subhead2_leah(
                    text = if (showRegular) priceFormatted else priceInvFormatted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
                HSpacer(width = 8.dp)
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_swap3_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey,
                )
            }
        }
    )
}

@Composable
private fun DateRow(dateFormatted: String) {
    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(R.string.TransactionInfo_Date)) },
        value = { subhead2_leah(text = dateFormatted) }
    )
}

@Composable
private fun StatusRow(status: TransactionStatusEnum) {
    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(R.string.TransactionInfo_Status)) },
        value = {
            when (status) {
                TransactionStatusEnum.FINISHED -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_checkmark_20),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.remus,
                    )
                    HSpacer(width = 4.dp)
                    subhead2_remus(text = stringResource(R.string.multi_swap_completed))
                }
                TransactionStatusEnum.FAILED -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.lucian,
                    )
                    HSpacer(width = 4.dp)
                    subhead2_lucian(text = stringResource(R.string.Transactions_Failed))
                }
                TransactionStatusEnum.REFUNDED -> {
                    subhead2_lucian(text = stringResource(R.string.transaction_swap_status_refunded))
                }
                else -> {
                    subhead2_leah(text = status.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    )
}

@Composable
private fun TransactionIdRow(transactionId: String) {
    val view = LocalView.current
    CellUniversal(borderTop = true) {
        subhead2_grey(text = stringResource(R.string.TransactionInfo_Id))
        HSpacer(width = 16.dp)
        subhead2_leah(
            modifier = Modifier.weight(1f),
            text = transactionId,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
        HSpacer(width = 8.dp)
        ButtonSecondaryCircle(
            icon = R.drawable.ic_copy_20,
            contentDescription = stringResource(R.string.Button_Copy),
            onClick = {
                TextHelper.copyText(transactionId)
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Copied)
            }
        )
    }
}

@Composable
private fun SupportRow(url: String) {
    val context = LocalContext.current
    CellUniversal(
        borderTop = false,
        onClick = { LinkHelper.openLinkInAppBrowser(context, url) },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_support_24),
            contentDescription = null,
            tint = ComposeAppTheme.colors.jacob,
        )
        HSpacer(width = 16.dp)
        body_leah(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.paycore_support),
        )
        HSpacer(width = 8.dp)
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ComposeAppTheme.colors.grey,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PayCoreExchangeDetailScreenPreview() {
    ComposeAppTheme {
        PayCoreExchangeDetailScreen(
            uiState = PayCoreExchangeDetailUiState(
                amountSentFormatted = "10,000 RUB",
                amountGotFormatted = "105.5 USDT",
                coinImageUrlIn = "https://p.cash/storage/coins/rub/image.png",
                coinImageUrlOut = null,
                priceFormatted = "1 RUB = 0.01055 USDT",
                priceInvFormatted = "1 USDT = 94.78 RUB",
                dateFormatted = "Mar 30, 2026, 14:30",
                status = TransactionStatusEnum.EXCHANGING,
                transactionId = "abc-123-def-456",
                supportUrl = AppConfigProvider.payCoreSupportUrl,
            ),
            onBack = {},
        )
    }
}
