package cash.p.terminal.modules.send

import android.os.Bundle
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
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
import cash.p.terminal.ui_compose.findNavController
import kotlinx.parcelize.Parcelize

class SendConfirmationFragment : BaseComposeFragment() {
    private val args: SendConfirmationFragmentArgs by navArgs()
    private var navGraphAvailable = true

    val amountInputModeViewModel by navGraphViewModels<AmountInputModeViewModel>(R.id.sendXFragment)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After process death the sendXFragment nav graph may no longer be on the
        // back stack, making navGraphViewModels unresolvable. The send state is
        // unrecoverable, so navigate back.
        navGraphAvailable = try {
            findNavController().getBackStackEntry(R.id.sendXFragment)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    @Composable
    override fun GetContent(navController: NavController) {
        if (!navGraphAvailable) {
            navController.navigateUp()
            return
        }

        val sendEntryPointDestId = args.sendEntryPointDestId

        when (args.type) {
            Type.Bitcoin -> {
                val sendBitcoinViewModel by navGraphViewModels<SendBitcoinViewModel>(R.id.sendXFragment)

                SendBitcoinConfirmationScreen(
                    navController,
                    sendBitcoinViewModel,
                    amountInputModeViewModel,
                    sendEntryPointDestId
                )
            }

            Type.ZCash -> {
                val sendZCashViewModel by navGraphViewModels<SendZCashViewModel>(R.id.sendXFragment)

                SendZCashConfirmationScreen(
                    navController,
                    sendZCashViewModel,
                    amountInputModeViewModel,
                    sendEntryPointDestId
                )
            }

            Type.Evm -> {
                val sendEvmViewModel by navGraphViewModels<SendEvmViewModel>(R.id.sendXFragment)

                SendEvmConfirmationScreen(
                    navController,
                    sendEvmViewModel,
                    amountInputModeViewModel,
                    sendEntryPointDestId
                )
            }

            Type.Tron -> {
                val sendTronViewModel by navGraphViewModels<SendTronViewModel>(R.id.sendXFragment)
                SendTronConfirmationScreen(
                    navController,
                    sendTronViewModel,
                    amountInputModeViewModel,
                    sendEntryPointDestId
                )
            }

            Type.Solana -> {
                val sendSolanaViewModel by navGraphViewModels<SendSolanaViewModel>(R.id.sendXFragment)

                SendSolanaConfirmationScreen(
                    navController,
                    sendSolanaViewModel,
                    amountInputModeViewModel,
                    sendEntryPointDestId
                )
            }

            Type.Ton -> {
                val sendTonViewModel by navGraphViewModels<SendTonViewModel>(R.id.sendXFragment)

                SendTonConfirmationScreen(
                    navController,
                    sendTonViewModel,
                    amountInputModeViewModel,
                    sendEntryPointDestId
                )
            }

            Type.Monero -> {
                val sendMoneroViewModel by navGraphViewModels<SendMoneroViewModel>(R.id.sendXFragment)

                SendMoneroConfirmationScreen(
                    navController = navController,
                    sendViewModel = sendMoneroViewModel,
                    amountInputModeViewModel = amountInputModeViewModel,
                    sendEntryPointDestId = sendEntryPointDestId
                )
            }

            Type.Stellar -> {
                val sendStellarViewModel by navGraphViewModels<SendStellarViewModel>(R.id.sendXFragment)

                SendStellarConfirmationScreen(
                    navController,
                    sendStellarViewModel,
                    amountInputModeViewModel,
                    sendEntryPointDestId
                )
            }
        }
    }

    @Parcelize
    enum class Type : Parcelable {
        Bitcoin, ZCash, Evm, Solana, Tron, Ton, Monero, Stellar
    }
}
