package cash.p.terminal.modules.backuplocal

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.core.composablePage
import cash.p.terminal.wallet.Account
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsScreen
import cash.p.terminal.modules.backuplocal.password.BackupType
import cash.p.terminal.modules.backuplocal.password.LocalBackupPasswordScreen
import cash.p.terminal.modules.backuplocal.terms.LocalBackupTermsScreen
import cash.p.terminal.ui_compose.getInput

class BackupLocalFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val account = navController.getInput<Account>()
        if (account != null) {
            SingleWalletBackupNavHost(navController, account.id)
        } else {
            FullBackupNavHost(fragmentNavController = navController)
        }
    }
}

@Composable
private fun FullBackupNavHost(fragmentNavController: NavController) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "select_backup_items",
    ) {
        composable("select_backup_items") {
            SelectBackupItemsScreen(
                onNextClick = { accountIdsList ->
                    val accountIds = if (accountIdsList.isNotEmpty()) "?accountIds=" + accountIdsList.joinToString(separator = ",") else ""
                    navController.navigate("terms_page$accountIds")
                },
                onBackClick = {
                    fragmentNavController.popBackStack()
                }
            )
        }

        composablePage(
            route = "terms_page?accountIds={accountIds}",
            arguments = listOf(navArgument("accountIds") { nullable = true })
        ) { backStackEntry ->
            val accountIds = backStackEntry.arguments?.getString("accountIds")
            LocalBackupTermsScreen(
                onTermsAccepted = {
                    navController.navigate("password_page${if (accountIds != null) "?accountIds=$accountIds" else ""}")
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composablePage(
            route = "password_page?accountIds={accountIds}",
            arguments = listOf(navArgument("accountIds") { nullable = true })
        ) { backStackEntry ->
            val accountIds = backStackEntry.arguments?.getString("accountIds")?.split(",") ?: listOf()
            LocalBackupPasswordScreen(
                backupType = BackupType.FullBackup(accountIds),
                onBackClick = {
                    navController.popBackStack()
                },
                onFinish = {
                    fragmentNavController.popBackStack()
                }
            )
        }
    }
}

@Composable
private fun SingleWalletBackupNavHost(fragmentNavController: NavController, accountId: String) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "terms_page",
    ) {
        composable("terms_page") {
            LocalBackupTermsScreen(
                onTermsAccepted = {
                    navController.navigate("password_page")
                },
                onBackClick = {
                    fragmentNavController.popBackStack()
                }
            )
        }
        composablePage("password_page") {
            LocalBackupPasswordScreen(
                backupType = BackupType.SingleWalletBackup(accountId),
                onBackClick = {
                    navController.popBackStack()
                },
                onFinish = {
                    fragmentNavController.popBackStack()
                }
            )
        }
    }
}
