package cash.p.terminal.modules.multiswap

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.modules.paycore.PayCoreAssets
import cash.p.terminal.ui.compose.components.Badge
import cash.p.terminal.ui.compose.components.MultitextM1
import cash.p.terminal.ui.compose.components.SearchBar
import cash.p.terminal.ui_compose.components.B2
import cash.p.terminal.ui_compose.components.D1
import cash.p.terminal.ui_compose.components.HeaderStick
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.imageUrl
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SelectSwapCoinDialogScreen(
    title: String,
    coinBalanceItems: List<CoinBalanceItem>,
    loading: Boolean,
    onSearchTextChanged: (String) -> Unit,
    onClose: () -> Unit,
    onClickItem: (CoinBalanceItem) -> Unit,
    fiatItems: List<CoinBalanceItem> = emptyList(),
    hasFiatSection: Boolean = false,
) {
    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.tyler)) {
        SearchBar(
            title = title,
            searchHintText = stringResource(R.string.ManageCoins_Search),
            onClose = onClose,
            onSearchTextChanged = onSearchTextChanged
        )

        if (loading && coinBalanceItems.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .padding(top = 4.dp, end = 8.dp),
                    color = ComposeAppTheme.colors.grey,
                    strokeWidth = 4.dp
                )
            }
        }

        LazyColumn {
            if (hasFiatSection && fiatItems.isNotEmpty()) {
                item {
                    HeaderStick(
                        borderTop = false,
                        text = stringResource(R.string.paycore_section_currency)
                    )
                }
                items(fiatItems) { coinItem ->
                    CoinBalanceRow(coinItem, onClickItem)
                }
                item {
                    HeaderStick(
                        borderTop = false,
                        text = stringResource(R.string.paycore_section_crypto)
                    )
                }
            }
            items(coinBalanceItems) { coinItem ->
                CoinBalanceRow(coinItem, onClickItem)
            }
            item {
                VSpacer(height = 32.dp)
            }
        }
    }
}

@Composable
private fun CoinBalanceRow(
    coinItem: CoinBalanceItem,
    onClickItem: (CoinBalanceItem) -> Unit
) {
    val isFiat = PayCoreAssets.isFiat(coinItem.token)
    SectionUniversalItem(borderTop = true) {
        RowUniversal(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = {
                onClickItem.invoke(coinItem)
            }
        ) {
            HsImage(
                url = coinItem.token.coin.imageUrl,
                alternativeUrl = coinItem.token.coin.alternativeImageUrl,
                placeholder = coinItem.token.iconPlaceholder,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            MultitextM1(
                title = {
                    Row {
                        val text = if (isFiat) {
                            coinItem.token.coin.name
                        } else {
                            coinItem.token.coin.code
                        }
                        B2(text = text)
                        if (!isFiat) {
                            coinItem.token.badge?.let {
                                Badge(text = it)
                            }
                        }
                    }
                },
                subtitle = {
                    if (!isFiat) {
                        D1(
                            text = coinItem.token.coin.name,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
            MultitextM1(
                title = {
                    coinItem.balance?.let {
                        App.numberFormatter.formatCoinShort(
                            it,
                            coinItem.token.coin.code,
                            8
                        )
                    }?.let {
                        B2(text = it)
                    }
                },
                subtitle = {
                    coinItem.fiatBalanceValue?.let { fiatBalanceValue ->
                        App.numberFormatter.formatFiatShort(
                            fiatBalanceValue.value,
                            fiatBalanceValue.currency.symbol,
                            2
                        )
                    }?.let {
                        D1(
                            modifier = Modifier.align(Alignment.End),
                            text = it
                        )
                    }
                }
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun CoinBalanceRowPreview() {
    val rubItem = CoinBalanceItem(
        token = PayCoreAssets.rubToken,
        balance = null,
        fiatBalanceValue = null
    )
    val usdtItem = CoinBalanceItem(
        token = Token(
            coin = Coin(
                uid = "tether",
                name = "Tether",
                code = "USDT",
                marketCapRank = null,
                coinGeckoId = null,
                image = null
            ),
            blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
            type = TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7"),
            decimals = 6
        ),
        balance = null,
        fiatBalanceValue = null
    )
    ComposeAppTheme {
        Column(modifier = Modifier.background(ComposeAppTheme.colors.tyler)) {
            CoinBalanceRow(rubItem) {}
            CoinBalanceRow(usdtItem) {}
        }
    }
}
