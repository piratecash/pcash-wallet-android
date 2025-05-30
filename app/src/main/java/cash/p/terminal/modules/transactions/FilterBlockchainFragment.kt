package cash.p.terminal.modules.transactions

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.navGraphViewModels
import coil.compose.rememberAsyncImagePainter
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellMultilineClear
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.imageUrl

class FilterBlockchainFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val viewModel: TransactionsViewModel? = try {
            navGraphViewModels<TransactionsViewModel>(R.id.mainFragment) { TransactionsModule.Factory() }.value
        } catch (e: IllegalStateException) {
            Toast.makeText(App.instance, "ViewModel is Null", Toast.LENGTH_SHORT).show()
            null
        }

        if (viewModel == null) {
            navController.popBackStack(R.id.filterBlockchainFragment, true)
            return
        }

        FilterBlockchainScreen(navController, viewModel)
    }
}


@Composable
fun FilterBlockchainScreen(navController: NavController, viewModel: TransactionsViewModel) {
    val filterBlockchains by viewModel.filterBlockchainsLiveData.observeAsState()

    Surface(color = ComposeAppTheme.colors.tyler) {
        Column {
            AppBar(
                title = stringResource(R.string.Transactions_Filter_ChooseBlockchain),
                navigationIcon = {
                    HsBackButton(onClick = navController::popBackStack)
                }
            )
            filterBlockchains?.let { blockchains ->
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(blockchains) { filterItem ->
                        BlockchainCell(viewModel, filterItem, navController)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockchainCell(
    viewModel: TransactionsViewModel,
    filterItem: Filter<Blockchain?>,
    navController: NavController
) {
    CellMultilineClear(borderTop = true) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    viewModel.onEnterFilterBlockchain(filterItem)
                    navController.popBackStack()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val blockchain = filterItem.item
            if (blockchain != null) {
                Image(
                    painter = rememberAsyncImagePainterWithFallback(
                        model = blockchain.type.imageUrl,
                        error = painterResource(R.drawable.ic_platform_placeholder_32)
                    ),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(32.dp),
                    contentDescription = null
                )
                body_leah(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .weight(1f),
                    text = blockchain.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.icon_24_circle_coin),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp),
                    contentDescription = null
                )
                body_leah(text = stringResource(R.string.Transactions_Filter_AllBlockchains))
            }
            if (filterItem.selected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.icon_20_check_1),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.jacob
                )
            }
        }
    }
}
