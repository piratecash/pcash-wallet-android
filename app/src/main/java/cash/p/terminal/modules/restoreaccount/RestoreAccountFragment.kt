package cash.p.terminal.modules.restoreaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.composablePopup
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureScreen
import cash.p.terminal.modules.moneroconfigure.MoneroConfigureViewModel
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletScreen
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletViewModel
import cash.p.terminal.modules.restoreaccount.restoreblockchains.ManageWalletsScreen
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuModule
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestorePhrase
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestorePhraseNonStandard
import cash.p.terminal.modules.zcashconfigure.ZcashConfigureScreen
import cash.p.terminal.strings.helpers.Translator.getString
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.getInput
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.inject

class RestoreAccountFragment : BaseComposeFragment(screenshotEnabled = false) {

    companion object {
        const val ROUTE_DUPLICATE = "duplicate_wallet"
        const val ROUTE_RESTORE_PHRASE = "restore_phrase"
    }

    @Composable
    override fun GetContent(navController: NavController) {
        val input = navController.getInput<ManageAccountsModule.Input>()
        val popUpToInclusiveId = input?.popOffOnSuccess ?: R.id.restoreAccountFragment
        val inclusive = input?.popOffInclusive ?: false
        val defaultRoute = input?.defaultRoute ?: ROUTE_RESTORE_PHRASE

        RestoreAccountNavHost(
            fragmentNavController = navController,
            popUpToInclusiveId = popUpToInclusiveId,
            inclusive = inclusive,
            defaultRoute = defaultRoute,
            accountId = input?.accountId.orEmpty()
        )
    }

}

@Composable
private fun RestoreAccountNavHost(
    fragmentNavController: NavController,
    popUpToInclusiveId: Int,
    inclusive: Boolean,
    defaultRoute: String,
    accountId: String
) {
    val navController = rememberNavController()
    val restoreMenuViewModel: RestoreMenuViewModel =
        viewModel(factory = RestoreMenuModule.Factory())
    val mainViewModel: RestoreViewModel = viewModel()
    NavHost(
        navController = navController,
        startDestination = defaultRoute,
    ) {
        composable(RestoreAccountFragment.ROUTE_RESTORE_PHRASE) {
            RestorePhrase(
                advanced = false,
                restoreMenuViewModel = restoreMenuViewModel,
                mainViewModel = mainViewModel,
                openRestoreAdvanced = { navController.navigate("restore_phrase_advanced") },
                openSelectCoins = { navController.navigate("restore_select_coins") },
                openNonStandardRestore = { navController.navigate("restore_phrase_nonstandard") },
                onBackClick = { fragmentNavController.popBackStack() },
                onFinish = { fragmentNavController.popBackStack(popUpToInclusiveId, inclusive) }
            )
        }
        composablePage("restore_phrase_advanced") {
            AdvancedRestoreScreen(
                restoreMenuViewModel = restoreMenuViewModel,
                mainViewModel = mainViewModel,
                openSelectCoinsScreen = { navController.navigate("restore_select_coins") },
                openNonStandardRestore = {
                    navController.navigate("restore_phrase_nonstandard")
                },
                onBackClick = { navController.popBackStack() },
                onFinish = { fragmentNavController.popBackStack(popUpToInclusiveId, inclusive) }
            )
        }
        composablePage(RestoreAccountFragment.ROUTE_DUPLICATE) {
            val accountManager: IAccountManager by inject(IAccountManager::class.java)
            val accountToCopy = remember { accountManager.account(accountId) }
            if (accountToCopy == null) {
                val view = LocalView.current
                LaunchedEffect(Unit) {
                    HudHelper.showErrorMessage(view, getString(R.string.error_no_active_account))
                    fragmentNavController.popBackStack()
                }
                return@composablePage
            }

            val viewModel: DuplicateWalletViewModel = koinViewModel(
                parameters = { parametersOf(accountToCopy) }
            )
            DuplicateWalletScreen(
                uiState = viewModel.uiState,
                onEnterName = viewModel::onEnterName,
                onTogglePassphrase = viewModel::onTogglePassphrase,
                onChangePassphrase = viewModel::onChangePassphrase,
                onChangePassphraseConfirmation = viewModel::onChangePassphraseConfirmation,
                onCreate = viewModel::createAccount,
                onBackClick = { fragmentNavController.popBackStack() },
                onFinish = { fragmentNavController.popBackStack(popUpToInclusiveId, inclusive) }
            )
        }
        composablePage("restore_select_coins") {
            ManageWalletsScreen(
                mainViewModel = mainViewModel,
                openConfigure = {
                    if (it.blockchainType == BlockchainType.Zcash) {
                        navController.navigate("zcash_configure")
                    } else if (it.blockchainType == BlockchainType.Monero) {
                        navController.navigate("monero_configure")
                    }
                },
                onBackClick = { navController.popBackStack() }
            ) { fragmentNavController.popBackStack(popUpToInclusiveId, inclusive) }
        }
        composablePage("restore_phrase_nonstandard") {
            RestorePhraseNonStandard(
                mainViewModel = mainViewModel,
                openSelectCoinsScreen = { navController.navigate("restore_select_coins") },
                onBackClick = { navController.popBackStack() }
            )
        }
        composablePopup("zcash_configure") {
            ZcashConfigureScreen(
                onCloseWithResult = { config ->
                    mainViewModel.setZCashConfig(config)
                    navController.popBackStack()
                },
                onCloseClick = {
                    mainViewModel.cancelZCashConfig = true
                    navController.popBackStack()
                }
            )
        }
        composablePopup("monero_configure") {
            val viewModel: MoneroConfigureViewModel = koinViewModel()
            MoneroConfigureScreen(
                onCloseWithResult = {
                    mainViewModel.setMoneroConfig(it)
                    navController.popBackStack()
                },
                onCloseClick = {
                    mainViewModel.cancelMoneroConfig = true
                    navController.popBackStack()
                },
                onRestoreNew = viewModel::onRestoreNew,
                onSetBirthdayHeight = viewModel::setBirthdayHeight,
                onDoneClick = viewModel::onDoneClick,
                uiState = viewModel.uiState,
            )
        }
    }
}
