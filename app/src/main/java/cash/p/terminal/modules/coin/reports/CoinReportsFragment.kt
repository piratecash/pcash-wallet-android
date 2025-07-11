package cash.p.terminal.modules.coin.reports

import android.os.Parcelable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.requireInput
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui.compose.components.CellNews
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize

class CoinReportsFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val input = navController.requireInput<Input>()

        CoinReportsScreen(
            viewModel = viewModel(factory = CoinReportsModule.Factory(input.coinUid)),
            onClickNavigation = {
                navController.popBackStack()
            },
            onClickReportUrl = {
                LinkHelper.openLinkInAppBrowser(requireContext(), it)
            }
        )
    }

    @Parcelize
    data class Input(val coinUid: String) : Parcelable
}

@Composable
private fun CoinReportsScreen(
    viewModel: CoinReportsViewModel,
    onClickNavigation: () -> Unit,
    onClickReportUrl: (url: String) -> Unit
) {
    val viewState by viewModel.viewStateLiveData.observeAsState()
    val isRefreshing by viewModel.isRefreshingLiveData.observeAsState(false)
    val reportViewItems by viewModel.reportViewItemsLiveData.observeAsState()

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.CoinPage_Reports),
                navigationIcon = {
                    HsBackButton(onClick = onClickNavigation)
                }
            )
        }
    ) { padding ->
        HSSwipeRefresh(
            refreshing = isRefreshing,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            onRefresh = viewModel::refresh,
            content = {
                Crossfade(viewState, label = "") { viewState ->
                    when (viewState) {
                        ViewState.Loading -> {
                            Loading()
                        }

                        is ViewState.Error -> {
                            ListErrorView(
                                stringResource(R.string.SyncError),
                                viewModel::onErrorClick
                            )
                        }

                        ViewState.Success -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                reportViewItems?.let {
                                    items(it) { report ->
                                        Spacer(modifier = Modifier.height(12.dp))
                                        CellNews(
                                            source = report.author,
                                            title = report.title,
                                            body = report.body,
                                            date = report.date,
                                        ) {
                                            onClickReportUrl(report.url)
                                        }
                                    }
                                    item {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                        }

                        null -> {}
                    }
                }
            }
        )
    }
}
