package cash.p.terminal.modules.sendtokenselect

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.MainGraphDirections
import cash.p.terminal.R
import cash.p.terminal.entities.AddressUri
import cash.p.terminal.modules.balance.BalanceViewItem2
import cash.p.terminal.modules.offline.OfflineBlockedBottomSheet
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.send.SendFragment
import cash.p.terminal.modules.tokenselect.TokenSelectScreen
import cash.p.terminal.modules.tokenselect.TokenSelectViewModel
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.getInput
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

class SendTokenSelectFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val input = navController.getInput<Input>()

        val blockchainTypes = input?.blockchainTypes
        val tokenTypes = input?.tokenTypes
        val prefilledData = input?.prefilledData
        val view = LocalView.current
        val viewModel: TokenSelectViewModel =
            viewModel(factory = TokenSelectViewModel.FactoryForSend(blockchainTypes, tokenTypes))
        var blockedWallet by remember { mutableStateOf<Wallet?>(null) }

        val onClickItem: (BalanceViewItem2) -> Unit = { viewItem ->
            when {
                viewItem.sendAvailability == OperationAvailability.BlockedOffline ->
                    blockedWallet = viewItem.wallet

                viewItem.sendAvailability == OperationAvailability.Available ->
                    navigateToSend(viewItem.wallet, input, navController)

                viewItem.syncingProgress.progress != null ->
                    HudHelper.showWarningMessage(view, R.string.Hud_WaitForSynchronization)

                viewItem.errorMessage != null ->
                    HudHelper.showErrorMessage(view, viewItem.errorMessage)
            }
        }

        TokenSelectScreen(
            navController = navController,
            title = stringResource(R.string.Balance_Send),
            searchHintText = stringResource(R.string.Balance_SendHint_CoinName),
            onClickItem = onClickItem,
            onBalanceClick = { viewItem ->
                if (viewModel.balanceHidden) {
                    viewModel.onBalanceClick(viewItem)
                } else {
                    onClickItem(viewItem)
                }
            },
            uiState = viewModel.uiState,
            updateFilter = viewModel::updateFilter,
            emptyItemsText = stringResource(R.string.Balance_NoAssetsToSend)
        )

        blockedWallet?.let { wallet ->
            OfflineBlockedBottomSheet(
                wallet = wallet,
                onWentOnline = {
                    blockedWallet = null
                    navigateToSend(wallet, input, navController)
                },
                onDismiss = { blockedWallet = null },
            )
        }
    }

    private fun navigateToSend(wallet: Wallet, input: Input?, navController: NavController) {
        val sendTitle = Translator.getString(R.string.Send_Title, wallet.token.fullCoin.coin.code)
        navController.navigate(
            MainGraphDirections.actionGlobalToSendFragment(
                input?.toSendInput(wallet, sendTitle)
                    ?: SendFragment.Input(
                        wallet = wallet,
                        title = sendTitle,
                        sendEntryPointDestId = R.id.sendTokenSelectFragment,
                    )
            )
        )
    }

    @Parcelize
    data class Input(
        val blockchainTypes: List<BlockchainType>?,
        val tokenTypes: List<TokenType>?,
        val prefilledData: PrefilledData,
    ) : Parcelable
}

@Parcelize
data class PrefilledData(
    val address: String?,
    val amount: BigDecimal? = null,
    val memo: String? = null,
) : Parcelable {
    companion object {
        fun from(addressUri: AddressUri) = PrefilledData(
            address = addressUri.address,
            amount = addressUri.amount,
            memo = addressUri.value(AddressUri.Field.Memo),
        )
    }
}

internal fun SendTokenSelectFragment.Input.toSendInput(
    wallet: Wallet,
    title: String,
) = SendFragment.Input(
    wallet = wallet,
    title = title,
    sendEntryPointDestId = R.id.sendTokenSelectFragment,
    prefilledData = prefilledData,
)
