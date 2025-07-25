package cash.p.terminal.modules.manageaccounts


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.hasNFC
import cash.p.terminal.core.navigateWithTermsAccepted
import cash.p.terminal.modules.backupalert.BackupAlert
import cash.p.terminal.modules.createaccount.CreateAccountFragment
import cash.p.terminal.modules.manageaccount.ManageAccountFragment
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule.AccountViewItem
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule.ActionViewItem
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui.compose.components.HsRadioButton
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_jacob
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.requireInput
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class ManageAccountsFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val input = try {
            navController.requireInput<ManageAccountsModule.Mode>()
        } catch (e: NullPointerException) {
            navController.popBackStack()
            return
        }
        ManageAccountsScreen(navController, input)
    }
}

@Composable
fun ManageAccountsScreen(navController: NavController, mode: ManageAccountsModule.Mode) {
    BackupAlert(navController)
    val context = LocalContext.current

    val viewModel = viewModel<ManageAccountsViewModel>(factory = ManageAccountsModule.Factory(mode))

    val finish = viewModel.finish

    if (finish) {
        navController.popBackStack()
    }

    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.tyler)) {
        AppBar(
            title = stringResource(R.string.ManageAccounts_Title),
            navigationIcon = { HsBackButton(onClick = { navController.popBackStack() }) }
        )

        LazyColumn(modifier = Modifier.background(color = ComposeAppTheme.colors.tyler)) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                AccountSection(
                    accounts = viewModel.regularAccountsState,
                    viewModel = viewModel,
                    navController = navController
                )
                AccountSection(
                    accounts = viewModel.watchAccountsState,
                    viewModel = viewModel,
                    navController = navController
                )
                AccountSection(
                    accounts = viewModel.hardwareAccountsState,
                    viewModel = viewModel,
                    navController = navController
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
                                navController.slideFromRight(R.id.createAccountFragment,
                                    CreateAccountFragment.Input(
                                        popOffOnSuccess = args.popOffOnSuccess,
                                        popOffInclusive = args.popOffInclusive
                                    ))
                            }
                        })
                    add(
                        ActionViewItem(
                            R.drawable.ic_plus,
                            R.string.new_monero_wallet
                        ) {
                            navController.navigateWithTermsAccepted {
                                navController.slideFromRight(R.id.createAccountFragment,
                                    CreateAccountFragment.Input(
                                        popOffOnSuccess = args.popOffOnSuccess,
                                        popOffInclusive = args.popOffInclusive,
                                        preselectMonero = true
                                    ))
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
                            title = if (context.hasNFC()) {
                                R.string.hardware_wallet
                            } else {
                                R.string.hardware_wallet_not_detected
                            },
                            enabled = context.hasNFC(),
                        ) {
                            if (context.hasNFC()) {
                                navController.slideFromRight(R.id.hardwareWalletFragment, args)
                            }
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
private fun AccountSection(
    accounts: List<AccountViewItem>?,
    viewModel: ManageAccountsViewModel,
    navController: NavController
) {
    accounts?.also {
        if (it.isNotEmpty()) {
            AccountsSection(it, viewModel, navController)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AccountsSection(
    accounts: List<AccountViewItem>,
    viewModel: ManageAccountsViewModel,
    navController: NavController
) {
    CellUniversalLawrenceSection(items = accounts) { accountViewItem ->
        RowUniversal(
            onClick = {
                viewModel.onSelect(accountViewItem)
            }
        ) {
            HsRadioButton(
                modifier = Modifier.padding(horizontal = 4.dp),
                selected = accountViewItem.selected,
                onClick = {
                    viewModel.onSelect(accountViewItem)
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                body_leah(text = accountViewItem.title)
                if (accountViewItem.backupRequired) {
                    subhead2_lucian(text = stringResource(id = R.string.ManageAccount_BackupRequired_Title))
                } else if (accountViewItem.migrationRequired) {
                    subhead2_lucian(text = stringResource(id = R.string.ManageAccount_MigrationRequired_Title))
                } else {
                    subhead2_grey(
                        text = accountViewItem.subtitle,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            }
            if (accountViewItem.isWatchAccount) {
                Icon(
                    painter = painterResource(id = R.drawable.icon_binocule_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey
                )
            } else if (accountViewItem.isHardwareWallet) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_card),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey
                )
            }

            val icon: Int
            val iconTint: Color
            if (accountViewItem.showAlertIcon) {
                icon = R.drawable.icon_warning_2_20
                iconTint = ComposeAppTheme.colors.lucian
            } else {
                icon = R.drawable.ic_more2_20
                iconTint = ComposeAppTheme.colors.leah
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
    }
}
