package cash.p.terminal.modules.depositcex

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.providers.CexAsset
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.ui.compose.components.Badge
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui.compose.components.SearchBar
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SelectCoinScreen(
    onClose: () -> Unit,
    itemIsSuspended: (DepositCexModule.CexCoinViewItem) -> Boolean,
    onSelectAsset: (CexAsset) -> Unit,
    withBalance: Boolean
) {
    val viewModel = viewModel<SelectCexAssetViewModel>(factory = SelectCexAssetViewModel.Factory(withBalance))

    val uiState = viewModel.uiState

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            SearchBar(
                title = stringResource(R.string.Cex_ChooseCoin),
                searchHintText = stringResource(R.string.Cex_SelectCoin_Search),
                onClose = onClose,
                onSearchTextChanged = {
                    viewModel.onEnterQuery(it)
                }
            )
        }
    ) {
        Crossfade(targetState = uiState.loading, label = "") { loading ->
            Column(modifier = Modifier.padding(it)) {
                if (loading) {
                    Loading()
                } else {
                    uiState.items?.let { viewItems ->
                        if (viewItems.isEmpty()) {
                            ListEmptyView(
                                text = stringResource(R.string.EmptyResults),
                                icon = R.drawable.ic_not_found
                            )
                        } else {
                            LazyColumn {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Divider(
                                        thickness = 1.dp,
                                        color = ComposeAppTheme.colors.steel10,
                                    )
                                }
                                items(viewItems) { viewItem: DepositCexModule.CexCoinViewItem ->
                                    CoinCell(
                                        viewItem = viewItem,
                                        suspended = itemIsSuspended.invoke(viewItem),
                                        onItemClick = {
                                            onSelectAsset.invoke(viewItem.cexAsset)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoinCell(
    viewItem: DepositCexModule.CexCoinViewItem,
    suspended: Boolean,
    onItemClick: () -> Unit,
) {
    Column {
        RowUniversal(
            onClick = if (suspended) null else onItemClick,
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalPadding = 0.dp
        ) {
            HsImage(
                url = viewItem.coinIconUrl,
                alternativeUrl = viewItem.alternativeCoinUrl,
                placeholder = viewItem.coinIconPlaceholder,
                modifier = Modifier
                    .padding(end = 16.dp, top = 12.dp, bottom = 12.dp)
                    .size(32.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    body_leah(
                        text = viewItem.title,
                        maxLines = 1,
                    )
                }
                subhead2_grey(
                    text = viewItem.subtitle,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            if (suspended) {
                HSpacer(width = 16.dp)
                Badge(text = stringResource(R.string.Suspended))
            }
        }
        Divider(
            thickness = 1.dp,
            color = ComposeAppTheme.colors.steel10,
        )
    }
}