package cash.p.terminal.modules.settings.donate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.MainGraphDirections
import cash.p.terminal.R
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.offline.OfflineBlockedBottomSheet
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.send.SendFragment
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.modules.tokenselect.TokenSelectScreen
import cash.p.terminal.modules.tokenselect.TokenSelectViewModel
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet

class DonateTokenSelectFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val viewModel: TokenSelectViewModel =
            viewModel(factory = TokenSelectViewModel.FactoryForSend())
        var blockedWallet by remember { mutableStateOf<Wallet?>(null) }

        TokenSelectScreen(
            navController = navController,
            title = stringResource(R.string.Settings_DonateWith),
            onClickItem = { viewItem ->
                if (viewItem.sendAvailability == OperationAvailability.BlockedOffline) {
                    blockedWallet = viewItem.wallet
                } else {
                    navigateToDonate(viewItem.wallet, navController)
                }
            },
            onBalanceClick = viewModel::onBalanceClick,
            uiState = viewModel.uiState,
            updateFilter = viewModel::updateFilter,
            emptyItemsText = stringResource(R.string.Balance_NoAssetsToSend)
        ) { DonateHeader(navController) }

        blockedWallet?.let { wallet ->
            OfflineBlockedBottomSheet(
                wallet = wallet,
                onWentOnline = {
                    blockedWallet = null
                    navigateToDonate(wallet, navController)
                },
                onDismiss = { blockedWallet = null },
            )
        }
    }

    private fun navigateToDonate(wallet: Wallet, navController: NavController) {
        val donateAddress = AppConfigProvider.donateAddresses[wallet.token.blockchainType] ?: return
        val sendTitle =
            Translator.getString(R.string.Settings_DonateToken, wallet.token.fullCoin.coin.code)
        navController.navigate(
            MainGraphDirections.actionGlobalToSendFragment(
                SendFragment.Input(
                    wallet = wallet,
                    title = sendTitle,
                    sendEntryPointDestId = R.id.donateTokenSelectFragment,
                    prefilledData = PrefilledData(donateAddress),
                    hideAddress = true
                )
            )
        )
    }
}

@Composable
private fun DonateHeader(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VSpacer(24.dp)
        headline2_leah(
            text = stringResource(R.string.Settings_Donate_Info),
            textAlign = TextAlign.Center
        )
        VSpacer(24.dp)
        Icon(
            painter = painterResource(id = R.drawable.ic_heart_filled_24),
            tint = ComposeAppTheme.colors.jacob,
            contentDescription = null,
        )
    }

    GetAddressCell {
        navController.slideFromRight(R.id.donateAddressesFragment)
    }
}

@Composable
private fun GetAddressCell(
    onClick: () -> Unit
) {
    VSpacer(24.dp)
    ButtonPrimaryDefault(
        title = stringResource(R.string.Settings_Donate_GetAddress),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        onClick = onClick
    )
    VSpacer(24.dp)
    subhead2_grey(
        text = stringResource(R.string.Settings_Donate_OrSelectCoinToDonate),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    VSpacer(24.dp)
}

@Preview
@Composable
private fun DonateHeaderPreview() {
    ComposeAppTheme {
        DonateHeader(navController = rememberNavController())
    }
}
