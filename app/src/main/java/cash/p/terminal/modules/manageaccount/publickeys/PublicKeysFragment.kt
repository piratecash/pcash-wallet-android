package cash.p.terminal.modules.manageaccount.publickeys

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.modules.manageaccount.evmaddress.PublicViewKeyFragment
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyFragment
import cash.p.terminal.modules.manageaccount.ui.KeyActionItem
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.getInput
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account

class PublicKeysFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val account = navController.getInput<cash.p.terminal.wallet.Account>()

        if (account == null) {
            Toast.makeText(App.instance, "Account parameter is missing", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
            return
        }
        ManageAccountScreen(navController, account)
    }

}

@Composable
fun ManageAccountScreen(navController: NavController, account: Account) {
    val viewModel = viewModel<PublicKeysViewModel>(factory = PublicKeysModule.Factory(account))

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.PublicKeys_Title),
                navigationIcon = {
                    HsBackButton(onClick = { navController.popBackStack() })
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            viewModel.viewState.evmAddress?.let { evmAddress ->
                KeyActionItem(
                    title = stringResource(id = R.string.PublicKeys_EvmAddress),
                    description = stringResource(R.string.PublicKeys_EvmAddress_Description)
                ) {
                    navController.slideFromRight(
                        R.id.publicViewKeyFragment,
                        PublicViewKeyFragment.Input(
                            titleResId = R.string.PublicKeys_EvmAddress,
                            viewKey = evmAddress,
                            showInfo = true
                        )
                    )
                }
            }
            viewModel.viewState.extendedPublicKey?.let { publicKey ->
                KeyActionItem(
                    title = stringResource(id = R.string.PublicKeys_AccountExtendedPublicKey),
                    description = stringResource(id = R.string.PublicKeys_AccountExtendedPublicKeyDescription),
                ) {
                    navController.slideFromRight(
                        R.id.showExtendedKeyFragment,
                        ShowExtendedKeyFragment.Input(
                            publicKey.hdKey,
                            publicKey.accountPublicKey
                        )
                    )
                }
            }
            viewModel.viewState.zcashUfvk?.let { publicKey ->
                KeyActionItem(
                    title = stringResource(id = R.string.publicKeys_zec_ufvk),
                    description = stringResource(id = R.string.publicKeys_zec_ufvk_descritpion),
                ) {
                    navController.slideFromRight(
                        R.id.publicViewKeyFragment,
                        PublicViewKeyFragment.Input(
                            titleResId = R.string.publicKeys_zec_ufvk,
                            viewKey = publicKey,
                            showInfo = false
                        )
                    )
                }
            }
        }
    }
}
