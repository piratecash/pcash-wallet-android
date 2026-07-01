package cash.p.terminal.modules.send

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import cash.p.terminal.R
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.send.bitcoin.SendBitcoinConfirmationScreen
import cash.p.terminal.modules.send.bitcoin.SendBitcoinViewModel
import cash.p.terminal.modules.send.evm.SendEvmConfirmationScreen
import cash.p.terminal.modules.send.evm.SendEvmViewModel
import cash.p.terminal.modules.send.monero.SendMoneroConfirmationScreen
import cash.p.terminal.modules.send.monero.SendMoneroViewModel
import cash.p.terminal.modules.send.solana.SendSolanaConfirmationScreen
import cash.p.terminal.modules.send.solana.SendSolanaViewModel
import cash.p.terminal.modules.send.stellar.SendStellarConfirmationScreen
import cash.p.terminal.modules.send.stellar.SendStellarViewModel
import cash.p.terminal.modules.send.ton.SendTonConfirmationScreen
import cash.p.terminal.modules.send.ton.SendTonViewModel
import cash.p.terminal.modules.send.tron.SendTronConfirmationScreen
import cash.p.terminal.modules.send.tron.SendTronViewModel
import cash.p.terminal.modules.send.zcash.SendZCashConfirmationScreen
import cash.p.terminal.modules.send.zcash.SendZCashViewModel
import cash.p.terminal.ui_compose.BaseComposeFragment
import kotlinx.parcelize.Parcelize

class SendConfirmationFragment : BaseComposeFragment() {
    private val args: SendConfirmationFragmentArgs by navArgs()

    @Composable
    override fun GetContent(navController: NavController) {
        val graphEntry = remember(navController.currentBackStackEntry) {
            try {
                navController.getBackStackEntry(R.id.sendXFragment)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        val sendEntryPointDestId = args.sendEntryPointDestId

        when (args.type) {
            Type.Bitcoin -> ConfirmationOrRecover(
                navController,
                graphEntry?.existingViewModelOrNull<SendBitcoinViewModel>()
            ) {
                SendBitcoinConfirmationScreen(navController, it, sendEntryPointDestId)
            }

            Type.ZCash -> ConfirmationOrRecover(
                navController,
                graphEntry?.existingViewModelOrNull<SendZCashViewModel>()
            ) {
                SendZCashConfirmationScreen(navController, it, sendEntryPointDestId)
            }

            Type.Evm -> ConfirmationOrRecover(
                navController,
                graphEntry?.existingViewModelOrNull<SendEvmViewModel>()
            ) {
                SendEvmConfirmationScreen(navController, it, sendEntryPointDestId)
            }

            Type.Tron -> {
                val sendTronViewModel = graphEntry?.existingViewModelOrNull<SendTronViewModel>()
                val amountInputModeViewModel = graphEntry?.existingViewModelOrNull<AmountInputModeViewModel>()
                val viewModels = if (sendTronViewModel != null && amountInputModeViewModel != null) {
                    sendTronViewModel to amountInputModeViewModel
                } else {
                    null
                }
                ConfirmationOrRecover(navController, viewModels) { (tron, amountMode) ->
                    SendTronConfirmationScreen(navController, tron, amountMode, sendEntryPointDestId)
                }
            }

            Type.Solana -> ConfirmationOrRecover(
                navController,
                graphEntry?.existingViewModelOrNull<SendSolanaViewModel>()
            ) {
                SendSolanaConfirmationScreen(navController, it, sendEntryPointDestId)
            }

            Type.Ton -> ConfirmationOrRecover(
                navController,
                graphEntry?.existingViewModelOrNull<SendTonViewModel>()
            ) {
                SendTonConfirmationScreen(navController, it, sendEntryPointDestId)
            }

            Type.Monero -> ConfirmationOrRecover(
                navController,
                graphEntry?.existingViewModelOrNull<SendMoneroViewModel>()
            ) {
                SendMoneroConfirmationScreen(
                    navController = navController,
                    sendViewModel = it,
                    sendEntryPointDestId = sendEntryPointDestId
                )
            }

            Type.Stellar -> ConfirmationOrRecover(
                navController,
                graphEntry?.existingViewModelOrNull<SendStellarViewModel>()
            ) {
                SendStellarConfirmationScreen(navController, it, sendEntryPointDestId)
            }
        }
    }

    // Renders the confirmation screen when the flow ViewModel is still alive, or triggers
    // recovery when it isn't (e.g. process death left an empty ViewModelStore).
    @Composable
    private fun <T : Any> ConfirmationOrRecover(
        navController: NavController,
        viewModel: T?,
        content: @Composable (T) -> Unit
    ) {
        if (viewModel != null) {
            content(viewModel)
        } else {
            RecoverToSendInputEffect(navController)
        }
    }

    private inline fun <reified T : ViewModel> ViewModelStoreOwner.existingViewModelOrNull(): T? =
        try {
            ViewModelProvider(this)[T::class.java]
        } catch (_: RuntimeException) {
            // Default factory can't instantiate a parametrized ViewModel: the store is
            // empty (e.g. process death restored this screen), so the VM was never created.
            null
        }

    // Recovery-only: the confirmation screen has no valid state after process death,
    // so send the user back to the send input step (sendXFragment == SendFragment),
    // dropping any security-check step in between.
    @Composable
    private fun RecoverToSendInputEffect(navController: NavController) {
        LaunchedEffect(Unit) {
            if (!navController.popBackStack(R.id.sendXFragment, false)) {
                navController.navigateUp()
            }
        }
    }

    @Parcelize
    enum class Type : Parcelable {
        Bitcoin, ZCash, Evm, Solana, Tron, Ton, Monero, Stellar
    }
}
