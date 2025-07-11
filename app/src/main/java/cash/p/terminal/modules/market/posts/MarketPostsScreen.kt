package cash.p.terminal.modules.market.posts

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui.compose.components.CellNews
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui.helpers.LinkHelper

@Composable
fun MarketPostsScreen(viewModel: MarketPostsViewModel = viewModel(factory = MarketPostsModule.Factory())) {
    val items by viewModel.itemsLiveData.observeAsState(listOf())
    val isRefreshing by viewModel.isRefreshingLiveData.observeAsState(false)
    val viewState by viewModel.viewStateLiveData.observeAsState()
    val context = LocalContext.current

    HSSwipeRefresh(
        refreshing = isRefreshing,
        onRefresh = {
            viewModel.refresh()
        }
    ) {
        Crossfade(viewState) { viewState ->
            when (viewState) {
                ViewState.Loading -> {
                    Loading()
                }
                is ViewState.Error -> {
                    ListErrorView(stringResource(R.string.SyncError), viewModel::onErrorClick)
                }
                ViewState.Success -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items) { postItem ->
                            Spacer(modifier = Modifier.height(12.dp))
                            CellNews(
                                source = postItem.source,
                                title = postItem.title,
                                body = postItem.body,
                                date = postItem.timeAgo,
                            ) {
                                LinkHelper.openLinkInAppBrowser(context, postItem.url)
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
                null -> {}
            }
        }
    }
}

@Preview
@Composable
fun PreviewMarketPostView() {
    val postItem = MarketPostsModule.PostViewItem(
        "Tidal",
        "3iQ's The Ether Fund begins \$CAD trading on TSX after Bitcoin The Ether Fund begins after Bitcoin",
        "Traders in East Asia are ready to take on more built by Wipro to streamline its liquefied.",
        "1h ago",
        "https://www.binance.org/news"
    )
    cash.p.terminal.ui_compose.theme.ComposeAppTheme {
        CellNews(
            source = postItem.source,
            title = postItem.title,
            body = postItem.body,
            date = postItem.timeAgo
        ) {}
    }
}
