package cash.p.terminal.modules.multiswap

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun SwapSelectProviderScreen(
    onClickClose: () -> Unit,
    quotes: List<QuoteViewItem>,
    currentQuote: SwapProviderQuote?,
    onSelectQuote: (SwapProviderQuote) -> Unit
) {
    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(R.string.Swap_Providers),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.Button_Done),
                        onClick = onClickClose
                    )
                ),
            )
        },
        containerColor = ComposeAppTheme.colors.tyler,
    ) {
        LazyColumn(
            modifier = Modifier.padding(it),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
                VSpacer(height = 12.dp)
            }
            itemsIndexed(quotes) { i, viewItem ->
                val borderColor = if (viewItem.quote == currentQuote) {
                    ComposeAppTheme.colors.yellow50
                } else {
                    ComposeAppTheme.colors.steel20
                }

                ProviderItem(
                    borderColor = borderColor,
                    onSelectQuote = onSelectQuote,
                    viewItem = viewItem,
                    modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp)
                )
            }

            item {
                VSpacer(height = 32.dp)
            }

        }
    }
}

@Composable
private fun ProviderItem(
    borderColor: Color,
    onSelectQuote: (SwapProviderQuote) -> Unit,
    viewItem: QuoteViewItem,
    modifier: Modifier
) {
    RowUniversal(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        onClick = { onSelectQuote.invoke(viewItem.quote) }
    ) {
        val provider = viewItem.quote.provider
        Image(
            modifier = Modifier.size(32.dp),
            painter = painterResource(provider.icon),
            contentDescription = null
        )
        HSpacer(width = 16.dp)
        Column {
            subhead2_leah(
                text = provider.title,
                textAlign = TextAlign.End
            )
        }
        HFillSpacer(minWidth = 8.dp)
        Column(horizontalAlignment = Alignment.End) {
            subhead2_leah(
                text = viewItem.tokenAmount,
                textAlign = TextAlign.End
            )
            viewItem.fiatAmount?.let { fiatAmount ->
                VSpacer(4.dp)
                subhead2_grey(text = fiatAmount, textAlign = TextAlign.End)
            }
        }
    }
}

@Preview
@Composable
private fun SwapSelectProviderScreenPreview() {
    val previewProvider = object : IMultiSwapProvider {
        override val id = "preview"
        override val title = "Uniswap V3"
        override val icon = R.drawable.uniswap_v3
        override val priority = 0
        override val walletUseCase get() = throw NotImplementedError()
        override val mevProtectionAvailable = false
        override suspend fun supports(token: cash.p.terminal.wallet.Token) = true
        override suspend fun fetchQuote(
            tokenIn: cash.p.terminal.wallet.Token,
            tokenOut: cash.p.terminal.wallet.Token,
            amountIn: java.math.BigDecimal,
            settings: Map<String, Any?>
        ): ISwapQuote = throw NotImplementedError()
        override suspend fun fetchFinalQuote(
            tokenIn: cash.p.terminal.wallet.Token,
            tokenOut: cash.p.terminal.wallet.Token,
            amountIn: java.math.BigDecimal,
            swapSettings: Map<String, Any?>,
            sendTransactionSettings: cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings?,
            swapQuote: ISwapQuote
        ): ISwapFinalQuote = throw NotImplementedError()
    }

    val previewQuote = object : ISwapQuote {
        override val amountOut = java.math.BigDecimal("456.78")
        override val priceImpact: java.math.BigDecimal? = null
        override val fields = emptyList<cash.p.terminal.modules.multiswap.ui.DataField>()
        override val settings = emptyList<cash.p.terminal.modules.multiswap.settings.ISwapSetting>()
        override val tokenIn: cash.p.terminal.wallet.Token get() = throw NotImplementedError()
        override val tokenOut: cash.p.terminal.wallet.Token get() = throw NotImplementedError()
        override val amountIn = java.math.BigDecimal("0.1234")
        override val actionRequired: cash.p.terminal.modules.multiswap.action.ISwapProviderAction? = null
        override val cautions = emptyList<cash.p.terminal.core.HSCaution>()
    }

    val quote1 = SwapProviderQuote(provider = previewProvider, swapQuote = previewQuote)

    ComposeAppTheme(darkTheme = false) {
        SwapSelectProviderScreen(
            onClickClose = {},
            quotes = listOf(
                QuoteViewItem(quote = quote1, tokenAmount = "456.78 DAI", fiatAmount = "$456.78"),
                QuoteViewItem(quote = quote1, tokenAmount = "455.12 DAI", fiatAmount = "$455.12"),
            ),
            currentQuote = quote1,
            onSelectQuote = {}
        )
    }
}
