package cash.p.terminal.modules.settings.addresschecker.ui

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.ui.compose.components.Badge
import cash.p.terminal.ui.compose.components.SearchBar
import cash.p.terminal.ui_compose.components.HsDivider
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.badge
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.imageUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AddressCheckTokenSelectorScreen(
    selectedBlockchain: Blockchain?,
    onSelect: (Token) -> Unit,
    onBackPress: () -> Unit,
) {
    val viewModel =
        viewModel<AddressCheckTokenSelectorViewModel>(
            factory = AddressCheckTokenSelectorModule.Factory(
                selectedBlockchain
            )
        )
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            SearchBar(
                title = stringResource(R.string.SettingsAddressChecker_SelectCoin),
                menuItems = listOf(),
                onClose = onBackPress,
                onSearchTextChanged = { text ->
                    viewModel.updateFilter(text)
                }
            )
        },
        containerColor = ComposeAppTheme.colors.tyler
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            uiState.tokens.forEachIndexed { _, item ->
                TokenCell(
                    token = item,
                    onItemClick = {
                        onSelect.invoke(item)
                    },
                )
            }
            if (uiState.tokens.isNotEmpty()) {
                HsDivider()
            }
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun TokenCell(
    token: Token,
    onItemClick: (Token) -> Unit,
) {
    val imageSource =
        ImageSource.Remote(token.blockchain.type.imageUrl, R.drawable.ic_platform_placeholder_32)
    Column {
        HsDivider()
        RowUniversal(
            onClick = { onItemClick.invoke(token) },
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalPadding = 0.dp
        ) {
            Image(
                painter = imageSource.painter(),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 16.dp, top = 12.dp, bottom = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    body_leah(
                        text = token.coin.code,
                        maxLines = 1,
                    )
                    token.badge?.let { labelText ->
                        Badge(
                            text = labelText,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                subhead2_grey(
                    text = token.coin.name,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

class AddressCheckTokenSelectorViewModel(
    walletManager: IWalletManager,
    blockchain: Blockchain?,
) : ViewModelUiState<AddressCheckTokenSelectorUiState>() {

    private var tokens = emptyList<Token>()
    private var filtered = emptyList<Token>()

    init {
        viewModelScope.launch {
            tokens = walletManager.activeWallets
                .map { it.token }
                .filter { it.blockchain == blockchain }
                .distinctBy { it.type }
            filtered = tokens
            emitState()
        }
    }

    override fun createState() = AddressCheckTokenSelectorUiState(
        tokens = filtered
    )

    fun updateFilter(text: String) {
        filtered = if (text.isBlank()) {
            tokens
        } else {
            tokens.filter {
                it.coin.name.contains(text, ignoreCase = true)
                        || it.coin.code.contains(text, ignoreCase = true)
                        || it.blockchain.name.contains(text, ignoreCase = true)
            }
        }

        emitState()
    }

}

object AddressCheckTokenSelectorModule {
    class Factory(private val blockchain: Blockchain?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddressCheckTokenSelectorViewModel(App.walletManager, blockchain) as T
        }
    }
}

data class AddressCheckTokenSelectorUiState(
    val tokens: List<Token>
)