package cash.p.terminal.modules.managewallets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.modules.enablecoin.restoresettings.IRestoreSettingsUi
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureFragment
import cash.p.terminal.modules.restoreaccount.restoreblockchains.CoinViewItem
import cash.p.terminal.modules.zcashconfigure.ZcashConfigure
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.navigation.slideFromBottomForResult
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui.compose.components.SearchBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefaults
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ManageWalletsScreen(
    navController: NavController,
    manageWalletsCallback: ManageWalletsCallback,
    onBackPressed: () -> Unit,
    requestScan: () -> Unit,
    restoreSettingsViewModel: IRestoreSettingsUi
) {
    val coinItems by manageWalletsCallback.viewItemsLiveData.observeAsState()
    val context = LocalView.current

    val blockchainType = restoreSettingsViewModel.openTokenConfigure?.blockchainType
    if (blockchainType != null) {
        restoreSettingsViewModel.tokenConfigureOpened()

        if (blockchainType == BlockchainType.Zcash) {
            navController.slideFromBottomForResult<ZcashConfigure.Result>(
                R.id.zcashConfigure
            ) {
                if (it.config != null) {
                    restoreSettingsViewModel.onEnter(it.config)
                } else {
                    restoreSettingsViewModel.onCancelEnterBirthdayHeight()
                }
            }
        } else if (blockchainType == BlockchainType.Monero) {
            navController.slideFromBottomForResult<MoneroConfigureFragment.Result>(
                resId = R.id.moneroConfigure
            ) {
                if (it.config != null) {
                    restoreSettingsViewModel.onEnter(it.config)
                } else {
                    restoreSettingsViewModel.onCancelEnterBirthdayHeight()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.background(color = ComposeAppTheme.colors.tyler)
        ) {
            SearchBar(
                title = stringResource(R.string.ManageCoins_title),
                searchHintText = stringResource(R.string.ManageCoins_Search),
                menuItems = if (manageWalletsCallback.addTokenEnabled) {
                    listOf(
                        MenuItem(
                            title = TranslatableString.ResString(R.string.ManageCoins_AddToken),
                            icon = R.drawable.ic_add_yellow,
                            onClick = {
                                navController.slideFromRight(R.id.addTokenFragment)
                            }
                        ))
                } else {
                    listOf()
                },
                onClose = onBackPressed,
                onSearchTextChanged = { text ->
                    manageWalletsCallback.updateFilter(text)
                }
            )

            coinItems?.let {
                if (it.isEmpty()) {
                    ListEmptyView(
                        text = stringResource(R.string.ManageCoins_NoResults),
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
                        items(it) { viewItem ->
                            CoinCell(
                                viewItem = viewItem,
                                onItemClick = {
                                    if (viewItem.enabled) {
                                        manageWalletsCallback.disable(viewItem.item)
                                    } else {
                                        manageWalletsCallback.enable(viewItem.item)
                                    }
                                },
                                onInfoClick = {
                                    navController.slideFromBottom(
                                        R.id.configuredTokenInfo,
                                        viewItem.item
                                    )
                                }
                            )
                        }
                        item {
                            VSpacer(height = 32.dp)
                        }
                        if (manageWalletsCallback.showScanToAddButton) {
                            item {
                                VSpacer(height = ButtonPrimaryDefaults.MinHeight + 32.dp)
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = manageWalletsCallback.showScanToAddButton,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            ScanToAddBlock(requestScan)
        }
        LaunchedEffect(manageWalletsCallback.errorMsg) {
            if (manageWalletsCallback.errorMsg != null) {
                HudHelper.showErrorMessage(context, manageWalletsCallback.errorMsg!!)
            }
        }

        LaunchedEffect(manageWalletsCallback.closeScreen) {
            if (manageWalletsCallback.closeScreen) {
                navController.popBackStack()
            }
        }
    }
}


@Composable
private fun ScanToAddBlock(requestScan: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = ComposeAppTheme.colors.tyler,
    ) {
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + with(LocalDensity.current) {
                        NavigationBarDefaults.windowInsets
                            .getBottom(LocalDensity.current)
                            .toDp()
                    },
                    top = 16.dp
                ),
            title = stringResource(R.string.scan_card_to_add),
            onClick = requestScan
        )
    }

}

@Composable
private fun CoinCell(
    viewItem: CoinViewItem<cash.p.terminal.wallet.Token>,
    onItemClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Column {
        RowUniversal(
            onClick = onItemClick,
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalPadding = 0.dp
        ) {
            Image(
                painter = viewItem.imageSource.painter(),
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
                        text = viewItem.title,
                        maxLines = 1,
                    )
                    viewItem.label?.let { labelText ->
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ComposeAppTheme.colors.jeremy)
                        ) {
                            Text(
                                modifier = Modifier.padding(
                                    start = 4.dp,
                                    end = 4.dp,
                                    bottom = 1.dp
                                ),
                                text = labelText,
                                color = ComposeAppTheme.colors.bran,
                                style = ComposeAppTheme.typography.microSB,
                                maxLines = 1,
                            )
                        }
                    }
                }
                subhead2_grey(
                    text = viewItem.subtitle,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            if (viewItem.hasInfo) {
                HsIconButton(onClick = onInfoClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info_20),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.grey
                    )
                }
            }
            HsSwitch(
                modifier = Modifier.padding(0.dp),
                checked = viewItem.enabled,
                onCheckedChange = { onItemClick.invoke() },
            )
        }
        Divider(
            thickness = 1.dp,
            color = ComposeAppTheme.colors.steel10,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ManageWalletsScreenPreview() {
    ComposeAppTheme {
        val items = listOf(
            CoinViewItem(
                item = Token(
                    coin = Coin("Bitcoin", "Bitcoin", "BTC"),
                    blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
                    type = TokenType.Native,
                    decimals = 8
                ),
                imageSource = ImageSource.Local(R.drawable.ic_placeholder),
                title = "BTC",
                subtitle = "Bitcoin",
                enabled = true
            ),
            CoinViewItem(
                item = Token(
                    coin = Coin("Ethereum","Ethereum", "ETH"),
                    blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
                    type = TokenType.Native,
                    decimals = 18
                ),
                imageSource = ImageSource.Local(R.drawable.ic_placeholder),
                title = "ETH",
                subtitle = "Ethereum",
                enabled = false,
                hasInfo = true
            ),
            CoinViewItem(
                item = Token(
                    coin = Coin("Solana","Solana", "SOL"),
                    blockchain = Blockchain(BlockchainType.Solana, "Solana", null),
                    type = TokenType.Native,
                    decimals = 9
                ),
                imageSource = ImageSource.Local(R.drawable.ic_placeholder),
                title = "SOL",
                subtitle = "Solana",
                enabled = true
            ),
            CoinViewItem(
                item = Token(
                    coin = Coin("Tether","Tether", "USDT"),
                    blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
                    type = TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7"),
                    decimals = 6
                ),
                imageSource = ImageSource.Local(R.drawable.ic_placeholder),
                title = "USDT",
                subtitle = "Tether USD",
                enabled = false
            ),
            CoinViewItem(
                item = Token(
                    coin = Coin("BNB","BNB", "BNB"),
                    blockchain = Blockchain(BlockchainType.BinanceSmartChain, "BNB Chain", null),
                    type = TokenType.Native,
                    decimals = 18
                ),
                imageSource = ImageSource.Local(R.drawable.ic_placeholder),
                title = "BNB",
                subtitle = "BNB Smart Chain",
                enabled = true
            ),
            CoinViewItem(
                item = Token(
                    coin = Coin("Dogecoin","Dogecoin", "DOGE"),
                    blockchain = Blockchain(BlockchainType.Dogecoin, "Dogecoin", null),
                    type = TokenType.Native,
                    decimals = 8
                ),
                imageSource = ImageSource.Local(R.drawable.ic_placeholder),
                title = "DOGE",
                subtitle = "Dogecoin",
                enabled = false
            ),
            CoinViewItem(
                item = Token(
                    coin = Coin("Avalanche","Avalanche", "AVAX"),
                    blockchain = Blockchain(BlockchainType.Avalanche, "Avalanche", null),
                    type = TokenType.Native,
                    decimals = 18
                ),
                imageSource = ImageSource.Local(R.drawable.ic_placeholder),
                title = "AVAX",
                subtitle = "Avalanche",
                enabled = true
            ),
            CoinViewItem(
                item = Token(
                    coin = Coin("Tron","Tron", "TRX"),
                    blockchain = Blockchain(BlockchainType.Tron, "Tron", null),
                    type = TokenType.Native,
                    decimals = 6
                ),
                imageSource = ImageSource.Local(R.drawable.ic_placeholder),
                title = "TRX",
                subtitle = "Tron",
                enabled = false
            )
        )

        ManageWalletsScreen(
            navController = rememberNavController(),
            manageWalletsCallback = object : ManageWalletsCallback {
                override val viewItemsLiveData = MutableLiveData(items)
                override val addTokenEnabled = true
                override val showScanToAddButton = false
                override val errorMsg: String? = null
                override val closeScreen: Boolean = false
                override fun updateFilter(text: String) = Unit
                override fun enable(token: Token) = Unit
                override fun disable(token: Token) = Unit
            },
            onBackPressed = {},
            requestScan = {},
            restoreSettingsViewModel = object : IRestoreSettingsUi {
                override val openTokenConfigure: Token? = null
                override fun tokenConfigureOpened() = Unit
                override fun onEnter(config: TokenConfig) = Unit
                override fun onCancelEnterBirthdayHeight() = Unit
            }
        )
    }
}
