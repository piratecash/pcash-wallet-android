package cash.p.terminal.modules.createaccount

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.core.composablePage
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsScreen
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsViewModel
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellSingleLineLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.getInput
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CreateAccountFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val input = navController.getInput<Input>()
        val popUpToInclusiveId = input?.popOffOnSuccess ?: R.id.createAccountFragment
        val inclusive = input?.popOffInclusive != false
        val preselectMonero = input?.preselectMonero == true
        CreateAccountNavHost(navController, popUpToInclusiveId, inclusive, preselectMonero)
    }

    @Parcelize
    data class Input(
        val popOffOnSuccess: Int,
        val popOffInclusive: Boolean,
        val preselectMonero: Boolean = false
    ) : Parcelable

}

@Composable
private fun CreateAccountNavHost(
    fragmentNavController: NavController,
    popUpToInclusiveId: Int,
    inclusive: Boolean,
    preselectMonero: Boolean
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (!preselectMonero) "create_account_intro" else "create_account_advanced",
    ) {
        composable("create_account_intro") {
            val viewModel: CreateAdvancedAccountViewModel = koinViewModel()
            CreateAccountIntroScreen(
                viewModel = viewModel,
                openCreateAdvancedScreen = { navController.navigate("create_account_advanced") },
                onBackClick = fragmentNavController::popBackStack,
                onFinish = { fragmentNavController.popBackStack(popUpToInclusiveId, inclusive) },
            )
        }
        composablePage("create_account_advanced") { backStackEntry ->
            val viewModel: CreateAdvancedAccountViewModel = koinViewModel()

            val passphraseTermsAgreed by backStackEntry.savedStateHandle
                .getStateFlow("passphrase_terms_agreed", false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(passphraseTermsAgreed) {
                if (passphraseTermsAgreed) {
                    viewModel.setPassphraseEnabledState(true)
                    backStackEntry.savedStateHandle["passphrase_terms_agreed"] = false
                }
            }

            CreateAccountAdvancedScreen(
                viewModel = viewModel,
                preselectMonero = preselectMonero,
                passphraseTermsAccepted = viewModel.passphraseTermsAgreed,
                onBackClick = {
                    if (!navController.popBackStack()) {
                        fragmentNavController.popBackStack()
                    }
                },
                onFinish = { fragmentNavController.popBackStack(popUpToInclusiveId, inclusive) },
                onOpenTerms = {
                    navController.navigate("passphrase_terms")
                }
            )
        }
        composablePage("passphrase_terms") {
            val context = LocalContext.current
            val termTitles = context.resources.getStringArray(R.array.passphrase_terms_checkboxes)
            val viewModel = koinViewModel<PassphraseTermsViewModel> { parametersOf(termTitles) }

            PassphraseTermsScreen(
                uiState = viewModel.uiState,
                onCheckboxToggle = viewModel::toggleCheckbox,
                onAgreeClick = {
                    viewModel.agree()
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("passphrase_terms_agreed", true)
                    navController.navigateUp()
                },
                onBackClick = navController::navigateUp
            )
        }
    }
}

@Composable
private fun CreateAccountIntroScreen(
    viewModel: CreateAdvancedAccountViewModel,
    openCreateAdvancedScreen: () -> Unit,
    onBackClick: () -> Unit,
    onFinish: () -> Unit
) {
    val view = LocalView.current

    LaunchedEffect(viewModel.success) {
        viewModel.success?.let { accountType ->
            HudHelper.showSuccessMessage(
                contenView = view,
                resId = R.string.Hud_Text_Created,
                icon = R.drawable.icon_add_to_wallet_24,
                iconTint = R.color.white
            )
            delay(300)

            onFinish.invoke()
            viewModel.onSuccessMessageShown()
        }
    }

    LaunchedEffect(viewModel.error) {
        viewModel.error?.let { message ->
            HudHelper.showErrorMessage(contentView = view, text = message)
            viewModel.onErrorShown()
        }
    }

    Surface(color = ComposeAppTheme.colors.tyler) {
        Column(Modifier.fillMaxSize()) {
            AppBar(
                title = stringResource(R.string.ManageAccounts_CreateNewWallet),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.Button_Create),
                        onClick = viewModel::createMnemonicAccount
                    )
                ),
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                },
                backgroundColor = Color.Transparent
            )
            Spacer(Modifier.height(12.dp))

            HeaderText(stringResource(id = R.string.ManageAccount_Name))
            FormsInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = viewModel.accountName,
                pasteEnabled = false,
                hint = viewModel.defaultAccountName,
                onValueChange = viewModel::onChangeAccountName
            )

            Spacer(Modifier.height(32.dp))

            CellSingleLineLawrenceSection {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            openCreateAdvancedScreen.invoke()
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    body_leah(text = stringResource(R.string.Button_Advanced))
                    Spacer(modifier = Modifier.weight(1f))
                    Image(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(id = R.drawable.ic_arrow_right),
                        contentDescription = null,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
