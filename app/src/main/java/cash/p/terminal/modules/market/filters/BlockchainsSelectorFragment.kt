package cash.p.terminal.modules.market.filters

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.navGraphViewModels
import cash.p.terminal.R
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import io.horizontalsystems.chartview.cell.CellBlockchainChecked
import io.horizontalsystems.chartview.cell.CellUniversal
import io.horizontalsystems.chartview.cell.SectionUniversalLawrence
import cash.p.terminal.ui_compose.findNavController
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class BlockchainsSelectorFragment : BaseComposeFragment() {

    private val viewModel by navGraphViewModels<MarketFiltersViewModel>(R.id.marketAdvancedSearchFragment) {
        MarketFiltersModule.Factory()
    }

    @Composable
    override fun GetContent(navController: NavController) {
        FilterByBlockchainsScreen(
            viewModel,
            navController,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().popBackStack()
                }
            })
    }

}

@Composable
private fun FilterByBlockchainsScreen(
    viewModel: MarketFiltersViewModel,
    navController: NavController
) {
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(R.string.Market_Filter_Blockchains),
                navigationIcon = {
                    HsBackButton(onClick = { navController.popBackStack() })
                }
            )
        },
        containerColor = ComposeAppTheme.colors.tyler,
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(height = 12.dp)

            SectionUniversalLawrence {
                AnyCell(
                    checked = uiState.selectedBlockchains.isEmpty(),
                    onClick = { viewModel.anyBlockchains() }
                )
                uiState.blockchainOptions.forEach { item ->
                    CellBlockchainChecked(
                        blockchain = item.blockchain,
                        checked = item.checked
                    ) {
                        if (item.checked) {
                            viewModel.onBlockchainUncheck(item.blockchain)
                        } else {
                            viewModel.onBlockchainCheck(item.blockchain)
                        }
                    }
                }
            }

            VSpacer(height = 32.dp)
        }
    }
}

@Composable
private fun AnyCell(
    checked: Boolean,
    onClick: () -> Unit
) {
    CellUniversal(
        borderTop = false,
        onClick = onClick
    ) {
        body_grey(
            modifier = Modifier
                .padding(end = 16.dp)
                .weight(1f),
            text = stringResource(R.string.Any),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(R.drawable.ic_checkmark_20),
            tint = ComposeAppTheme.colors.jacob,
            contentDescription = null,
            modifier = Modifier.alpha(if (checked) 1f else 0f)
        )
    }
}
