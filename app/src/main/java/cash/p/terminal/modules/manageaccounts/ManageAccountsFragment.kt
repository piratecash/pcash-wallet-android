package cash.p.terminal.modules.manageaccounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.navigateWithTermsAccepted
import cash.p.terminal.modules.backupalert.BackupAlert
import cash.p.terminal.modules.createaccount.CreateAccountFragment
import cash.p.terminal.modules.manageaccount.ManageAccountFragment
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule.AccountViewItem
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule.ActionViewItem
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.components.HsRadioButton
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.PremiumHeader
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionHeaderWithIcon
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_jacob
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian

import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class ManageAccountsFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        withInput<ManageAccountsModule.Mode>(navController) { input ->
            ManageAccountsScreen(navController, input)
        }
    }
}

@Composable
fun ManageAccountsScreen(navController: NavController, mode: ManageAccountsModule.Mode) {
    BackupAlert(navController)

    val viewModel: ManageAccountsViewModel = koinViewModel { parametersOf(mode) }

    val finish = viewModel.finish

    if (finish) {
        navController.popBackStack()
    }

    Column(
        modifier = Modifier
            .background(color = ComposeAppTheme.colors.tyler)
            .navigationBarsPadding()
    ) {
        AppBar(
            title = stringResource(R.string.ManageAccounts_Title),
            navigationIcon = { HsBackButton(onClick = { navController.popBackStackSafely() }) }
        )

        LazyColumn(modifier = Modifier.background(color = ComposeAppTheme.colors.tyler)) {
            item {
                WalletSection(
                    accounts = viewModel.premiumAccountsState,
                    onSelect = viewModel::onSelect,
                    navController = navController,
                    frameColor = ComposeAppTheme.colors.jacob,
                    header = {
                        PremiumHeader(text = stringResource(R.string.manage_accounts_premium_active))
                    }
                )
                WalletSection(
                    accounts = viewModel.regularAccountsState,
                    onSelect = viewModel::onSelect,
                    navController = navController,
                    header = {
                        SectionHeaderWithIcon(
                            iconRes = R.drawable.ic_switch_wallet_24,
                            text = stringResource(R.string.manage_accounts_section_other)
                        )
                    }
                )
                WalletSection(
                    accounts = viewModel.watchAccountsState,
                    onSelect = viewModel::onSelect,
                    navController = navController,
                    header = {
                        SectionHeaderWithIcon(
                            iconRes = R.drawable.icon_binocule_20,
                            text = stringResource(R.string.manage_accounts_section_watch)
                        )
                    }
                )
                WalletSection(
                    accounts = viewModel.hardwareAccountsState,
                    onSelect = viewModel::onSelect,
                    navController = navController,
                    header = {
                        SectionHeaderWithIcon(
                            iconRes = R.drawable.ic_card,
                            text = stringResource(R.string.manage_accounts_section_hardware)
                        )
                    }
                )

                val args = when (mode) {
                    ManageAccountsModule.Mode.Manage -> ManageAccountsModule.Input(
                        R.id.manageAccountsFragment,
                        false
                    )

                    ManageAccountsModule.Mode.Switcher -> ManageAccountsModule.Input(
                        R.id.manageAccountsFragment,
                        true
                    )
                }

                val actions = buildList {
                    add(
                        ActionViewItem(
                            R.drawable.ic_plus,
                            R.string.ManageAccounts_CreateNewWallet
                        ) {
                            navController.navigateWithTermsAccepted {
                                navController.slideFromRight(
                                    R.id.createAccountFragment,
                                    CreateAccountFragment.Input(
                                        popOffOnSuccess = args.popOffOnSuccess,
                                        popOffInclusive = args.popOffInclusive
                                    )
                                )
                            }
                        })
                    add(
                        ActionViewItem(
                            R.drawable.ic_download_20,
                            R.string.ManageAccounts_ImportWallet
                        ) {
                            navController.slideFromRight(R.id.importWalletFragment, args)
                        })
                    add(
                        ActionViewItem(
                            R.drawable.icon_binocule_20,
                            R.string.ManageAccounts_WatchAddress
                        ) {
                            navController.slideFromRight(R.id.watchAddressFragment, args)
                        })
                    add(
                        ActionViewItem(
                            icon = R.drawable.ic_card,
                            title = R.string.hardware_wallet,
                        ) {
                            navController.slideFromRight(R.id.hardwareWalletFragment, args)
                        }
                    )
                }

                CellUniversalLawrenceSection(actions) {
                    RowUniversal(
                        onClick = it.callback
                    ) {
                        Icon(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            painter = painterResource(id = it.icon),
                            contentDescription = null,
                            tint = if (it.enabled) {
                                ComposeAppTheme.colors.jacob
                            } else {
                                ComposeAppTheme.colors.grey
                            }
                        )
                        if (it.enabled) {
                            body_jacob(text = stringResource(id = it.title))
                        } else {
                            body_grey(text = stringResource(id = it.title))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun WalletSection(
    accounts: List<AccountViewItem>?,
    onSelect: (AccountViewItem) -> Unit,
    navController: NavController,
    header: @Composable () -> Unit,
    frameColor: Color? = null,
) {
    if (!accounts.isNullOrEmpty()) {
        header()
        AccountsSection(accounts, onSelect, navController, frameColor)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AccountsSection(
    accounts: List<AccountViewItem>,
    onSelect: (AccountViewItem) -> Unit,
    navController: NavController,
    frameColor: Color?,
) {
    if (frameColor != null) {
        CellUniversalLawrenceSection(items = accounts, frameColor = frameColor) { accountViewItem ->
            AccountRow(accountViewItem, onSelect, navController)
        }
    } else {
        CellUniversalLawrenceSection(items = accounts) { accountViewItem ->
            AccountRow(accountViewItem, onSelect, navController)
        }
    }
}

@Composable
private fun AccountRow(
    accountViewItem: AccountViewItem,
    onSelect: (AccountViewItem) -> Unit,
    navController: NavController,
) {
    RowUniversal(
        onClick = { onSelect(accountViewItem) }
    ) {
        HsRadioButton(
            modifier = Modifier.padding(horizontal = 4.dp),
            selected = accountViewItem.selected,
            onClick = { onSelect(accountViewItem) }
        )
        Column(modifier = Modifier.weight(1f)) {
            body_leah(text = accountViewItem.title)
            AccountSubtitle(accountViewItem)
        }
        PremiumBadge(
            premiumType = accountViewItem.premiumType,
            modifier = Modifier.padding(start = 8.dp)
        )
        AccountTypeIcon(accountViewItem)
        AccountMoreButton(accountViewItem, navController)
    }
}

@Composable
private fun AccountSubtitle(accountViewItem: AccountViewItem) {
    when {
        accountViewItem.backupRequired ->
            subhead2_lucian(text = stringResource(id = R.string.ManageAccount_BackupRequired_Title))

        accountViewItem.migrationRequired ->
            subhead2_lucian(text = stringResource(id = R.string.ManageAccount_MigrationRequired_Title))

        else -> subhead2_grey(
            text = accountViewItem.subtitle,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
    }
}

@Composable
private fun AccountTypeIcon(accountViewItem: AccountViewItem) {
    val iconRes = when {
        accountViewItem.isWatchAccount -> R.drawable.icon_binocule_20
        accountViewItem.showNfcIcon -> R.drawable.ic_card
        else -> return
    }
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = ComposeAppTheme.colors.grey
    )
}

@Composable
private fun AccountMoreButton(
    accountViewItem: AccountViewItem,
    navController: NavController,
) {
    val (icon, iconTint) = if (accountViewItem.showAlertIcon) {
        R.drawable.icon_warning_2_20 to ComposeAppTheme.colors.lucian
    } else {
        R.drawable.ic_more2_20 to ComposeAppTheme.colors.leah
    }
    ButtonSecondaryCircle(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = icon,
        tint = iconTint
    ) {
        navController.slideFromRight(
            R.id.manageAccountFragment,
            ManageAccountFragment.Input(accountViewItem.accountId)
        )
    }
}
