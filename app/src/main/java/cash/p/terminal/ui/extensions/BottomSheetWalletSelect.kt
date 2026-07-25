package cash.p.terminal.ui.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.manageaccounts.PremiumBadge
import cash.p.terminal.modules.manageaccounts.groupByPremium
import cash.p.terminal.modules.manageaccounts.resolvedPremiumType
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.PremiumHeader
import cash.p.terminal.ui_compose.components.SectionHeaderWithIcon
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsRadioButton
import cash.p.terminal.ui_compose.components.RowUniversal
import kotlinx.coroutines.launch

/**
 * Material3 ModalBottomSheet for wallet selection.
 * Self-contained - manages its own sheet state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSwitchBottomSheet(
    wallets: List<Account>,
    watchingAddresses: List<Account>,
    selectedAccount: Account?,
    onSelectListener: (Account) -> Unit,
    onDismiss: () -> Unit,
    premiumTypes: Map<String, PremiumType>? = null,
    title: String = stringResource(R.string.ManageAccount_SwitchWallet_Title)
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WalletSwitchContent(
            wallets = wallets,
            watchingAddresses = watchingAddresses,
            selectedAccount = selectedAccount,
            premiumTypes = premiumTypes,
            onSelectListener = { account ->
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onSelectListener(account)
                    onDismiss()
                }
            },
            onCloseClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onDismiss()
                }
            },
            title = title
        )
    }
}

/**
 * @param premiumTypes premium type per account id, or `null` to render the legacy flat layout (Wallets +
 * Watch addresses, no premium grouping/badges) for callers that do not resolve premium — otherwise a
 * premium wallet would be silently mislabeled under "Other Wallets".
 */
@Composable
private fun WalletSwitchContent(
    wallets: List<Account>,
    watchingAddresses: List<Account>,
    selectedAccount: Account?,
    premiumTypes: Map<String, PremiumType>?,
    onSelectListener: (Account) -> Unit,
    onCloseClick: () -> Unit,
    title: String
) {
    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.icon_24_lock),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
        title = title,
        onCloseClick = onCloseClick,
        // Keep the sheet header below the system status bar when fully expanded.
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Spacer(Modifier.height(12.dp))

        if (premiumTypes == null) {
            LegacyWalletSwitchBody(wallets, watchingAddresses, selectedAccount, onSelectListener)
        } else {
            PremiumWalletSwitchBody(
                wallets = wallets,
                watchingAddresses = watchingAddresses,
                selectedAccount = selectedAccount,
                premiumTypes = premiumTypes,
                onSelectListener = onSelectListener,
            )
        }
    }
}

@Composable
private fun ColumnScope.PremiumWalletSwitchBody(
    wallets: List<Account>,
    watchingAddresses: List<Account>,
    selectedAccount: Account?,
    premiumTypes: Map<String, PremiumType>,
    onSelectListener: (Account) -> Unit,
) {
    val groups = (wallets + watchingAddresses).groupByPremium(premiumTypes)

    WalletSwitchSection(
        header = { PremiumHeader(text = stringResource(R.string.manage_accounts_premium_active)) },
        accounts = groups.premium,
        selectedAccount = selectedAccount,
        premiumTypes = premiumTypes,
        onSelectListener = onSelectListener,
        frameColor = ComposeAppTheme.colors.jacob,
    )
    WalletSwitchSection(
        header = {
            SectionHeaderWithIcon(
                iconRes = R.drawable.ic_switch_wallet_24,
                text = stringResource(R.string.manage_accounts_section_other)
            )
        },
        accounts = groups.other,
        selectedAccount = selectedAccount,
        premiumTypes = premiumTypes,
        onSelectListener = onSelectListener,
    )
    WalletSwitchSection(
        header = {
            SectionHeaderWithIcon(
                iconRes = R.drawable.icon_binocule_20,
                text = stringResource(R.string.manage_accounts_section_watch)
            )
        },
        accounts = groups.watch,
        selectedAccount = selectedAccount,
        premiumTypes = premiumTypes,
        onSelectListener = onSelectListener,
    )
    WalletSwitchSection(
        header = {
            SectionHeaderWithIcon(
                iconRes = R.drawable.ic_card,
                text = stringResource(R.string.manage_accounts_section_hardware)
            )
        },
        accounts = groups.hardware,
        selectedAccount = selectedAccount,
        premiumTypes = premiumTypes,
        onSelectListener = onSelectListener,
    )

    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ColumnScope.LegacyWalletSwitchBody(
    wallets: List<Account>,
    watchingAddresses: List<Account>,
    selectedAccount: Account?,
    onSelectListener: (Account) -> Unit,
) {
    val comparator = compareBy<Account> { it.name.lowercase() }

    if (wallets.isNotEmpty()) {
        HeaderText(text = stringResource(R.string.ManageAccount_Wallets))
        Section(
            items = wallets.sortedWith(comparator),
            selectedItem = selectedAccount,
            premiumTypes = emptyMap(),
            frameColor = ComposeAppTheme.colors.steel20,
            onSelectListener = onSelectListener,
        )
    }

    if (watchingAddresses.isNotEmpty()) {
        if (wallets.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
        }
        HeaderText(text = stringResource(R.string.ManageAccount_WatchAddresses))
        Section(
            items = watchingAddresses.sortedWith(comparator),
            selectedItem = selectedAccount,
            premiumTypes = emptyMap(),
            frameColor = ComposeAppTheme.colors.steel20,
            onSelectListener = onSelectListener,
        )
    }

    Spacer(Modifier.height(44.dp))
}

@Composable
private fun WalletSwitchSection(
    header: @Composable () -> Unit,
    accounts: List<Account>,
    selectedAccount: Account?,
    premiumTypes: Map<String, PremiumType>,
    onSelectListener: (Account) -> Unit,
    frameColor: Color = ComposeAppTheme.colors.steel20,
) {
    if (accounts.isEmpty()) return
    header()
    Section(
        items = accounts,
        selectedItem = selectedAccount,
        premiumTypes = premiumTypes,
        frameColor = frameColor,
        onSelectListener = onSelectListener,
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Section(
    items: List<Account>,
    selectedItem: Account?,
    premiumTypes: Map<String, PremiumType>,
    frameColor: Color,
    onSelectListener: (Account) -> Unit,
) {
    CellUniversalLawrenceSection(items = items, frameColor = frameColor) { item ->
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = {
                onSelectListener.invoke(item)
            },
        ) {
            HsRadioButton(
                selected = item == selectedItem,
                onClick = {
                    onSelectListener.invoke(item)
                }
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                body_leah(text = item.name)
                subhead2_grey(text = item.type.detailedDescription)
            }
            PremiumBadge(
                premiumType = item.resolvedPremiumType(premiumTypes),
                modifier = Modifier.padding(start = 8.dp)
            )
            if (item.isWatchAccount) {
                Icon(
                    modifier = Modifier.padding(start = 16.dp),
                    painter = painterResource(id = R.drawable.ic_eye_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey
                )
            }
        }
    }
}

@Preview
@Composable
private fun WalletSwitchContentPreview() {
    val wallets = listOf(
        Account(
            id = "1",
            name = "Wallet 1",
            type = AccountType.Mnemonic(words = listOf(), passphrase = ""),
            origin = AccountOrigin.Created,
            level = 0
        ),
        Account(
            id = "2",
            name = "Wallet 2",
            type = AccountType.Mnemonic(words = listOf(), passphrase = ""),
            origin = AccountOrigin.Restored,
            level = 0
        )
    )
    val watchingAddresses = listOf(
        Account(
            id = "3",
            name = "Watch Address",
            type = AccountType.EvmAddress(address = "0x1234567890"),
            origin = AccountOrigin.Restored,
            level = 0
        )
    )

    ComposeAppTheme {
        WalletSwitchContent(
            wallets = wallets,
            watchingAddresses = watchingAddresses,
            selectedAccount = wallets.first(),
            premiumTypes = mapOf("1" to PremiumType.PIRATE),
            onSelectListener = {},
            onCloseClick = {},
            title = "Switch Wallet"
        )
    }
}
