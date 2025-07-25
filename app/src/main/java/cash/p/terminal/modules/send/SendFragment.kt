package cash.p.terminal.modules.send

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.navGraphViewModels
import cash.p.terminal.R
import cash.p.terminal.core.BaseFragment
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountInputModeModule
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.send.bitcoin.SendBitcoinModule
import cash.p.terminal.modules.send.bitcoin.SendBitcoinNavHost
import cash.p.terminal.modules.send.bitcoin.SendBitcoinViewModel
import cash.p.terminal.modules.send.evm.SendEvmScreen
import cash.p.terminal.modules.send.monero.SendMoneroModule
import cash.p.terminal.modules.send.monero.SendMoneroScreen
import cash.p.terminal.modules.send.monero.SendMoneroViewModel
import cash.p.terminal.modules.send.solana.SendSolanaModule
import cash.p.terminal.modules.send.solana.SendSolanaScreen
import cash.p.terminal.modules.send.solana.SendSolanaViewModel
import cash.p.terminal.modules.send.stellar.SendStellarModule
import cash.p.terminal.modules.send.stellar.SendStellarScreen
import cash.p.terminal.modules.send.stellar.SendStellarViewModel
import cash.p.terminal.modules.send.ton.SendTonModule
import cash.p.terminal.modules.send.ton.SendTonScreen
import cash.p.terminal.modules.send.ton.SendTonViewModel
import cash.p.terminal.modules.send.tron.SendTronModule
import cash.p.terminal.modules.send.tron.SendTronScreen
import cash.p.terminal.modules.send.tron.SendTronViewModel
import cash.p.terminal.modules.send.zcash.SendZCashModule
import cash.p.terminal.modules.send.zcash.SendZCashScreen
import cash.p.terminal.modules.send.zcash.SendZCashViewModel
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.ui_compose.findNavController
import cash.p.terminal.ui_compose.requireInput
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

class SendFragment : BaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner)
            )
            try {
                val navController = findNavController()
                val input = navController.requireInput<Input>()
                val wallet = input.wallet
                val title = input.title
                val sendEntryPointDestId = input.sendEntryPointDestId
                val address = input.address
                val prefilledData = PrefilledData(address.hex, input.amount)
                val hideAddress = input.hideAddress
                val amount = input.amount

                val amountInputModeViewModel by navGraphViewModels<AmountInputModeViewModel>(R.id.sendXFragment) {
                    AmountInputModeModule.Factory(wallet.coin.uid)
                }

                when (wallet.token.blockchainType) {
                    BlockchainType.Bitcoin,
                    BlockchainType.BitcoinCash,
                    BlockchainType.ECash,
                    BlockchainType.Litecoin,
                    BlockchainType.Dogecoin,
                    BlockchainType.PirateCash,
                    BlockchainType.Cosanta,
                    BlockchainType.Dash -> {
                        val factory = SendBitcoinModule.Factory(wallet, address, hideAddress)
                        val sendBitcoinViewModel by navGraphViewModels<SendBitcoinViewModel>(R.id.sendXFragment) {
                            factory
                        }
                        setContent {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                            ) {
                                SendBitcoinNavHost(
                                    title = title,
                                    fragmentNavController = findNavController(),
                                    viewModel = sendBitcoinViewModel,
                                    amountInputModeViewModel = amountInputModeViewModel,
                                    sendEntryPointDestId = sendEntryPointDestId,
                                    prefilledData = prefilledData,
                                )
                            }
                        }
                    }

                    BlockchainType.Zcash -> {
                        val factory = SendZCashModule.Factory(wallet, address, hideAddress)
                        val sendZCashViewModel by navGraphViewModels<SendZCashViewModel>(R.id.sendXFragment) {
                            factory
                        }
                        setContent {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                            ) {
                                SendZCashScreen(
                                    title,
                                    findNavController(),
                                    sendZCashViewModel,
                                    amountInputModeViewModel,
                                    sendEntryPointDestId,
                                    prefilledData,
                                )
                            }
                        }
                    }

                    BlockchainType.Ethereum,
                    BlockchainType.BinanceSmartChain,
                    BlockchainType.Polygon,
                    BlockchainType.Avalanche,
                    BlockchainType.Optimism,
                    BlockchainType.Base,
                    BlockchainType.ZkSync,
                    BlockchainType.Gnosis,
                    BlockchainType.Fantom,
                    BlockchainType.ArbitrumOne -> {
                        setContent {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                            ) {
                                SendEvmScreen(
                                    title = title,
                                    navController = findNavController(),
                                    amountInputModeViewModel = amountInputModeViewModel,
                                    address = address,
                                    wallet = wallet,
                                    amount = amount,
                                    hideAddress = hideAddress,
                                    sendEntryPointDestId = sendEntryPointDestId
                                )
                            }
                        }
                    }

                    BlockchainType.Solana -> {
                        val factory = SendSolanaModule.Factory(wallet, address, hideAddress)
                        val sendSolanaViewModel by navGraphViewModels<SendSolanaViewModel>(R.id.sendXFragment) { factory }
                        setContent {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                            ) {
                                SendSolanaScreen(
                                    title,
                                    findNavController(),
                                    sendSolanaViewModel,
                                    amountInputModeViewModel,
                                    sendEntryPointDestId,
                                    prefilledData,
                                )
                            }
                        }
                    }

                    BlockchainType.Ton -> {
                        val factory = SendTonModule.Factory(wallet, address, hideAddress)
                        val sendTonViewModel by navGraphViewModels<SendTonViewModel>(R.id.sendXFragment) { factory }
                        setContent {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                            ) {
                                SendTonScreen(
                                    title,
                                    findNavController(),
                                    sendTonViewModel,
                                    amountInputModeViewModel,
                                    sendEntryPointDestId,
                                    prefilledData,
                                )
                            }
                        }
                    }

                    BlockchainType.Tron -> {
                        val factory = SendTronModule.Factory(wallet, address, hideAddress)
                        val sendTronViewModel by navGraphViewModels<SendTronViewModel>(R.id.sendXFragment) { factory }
                        setContent {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                            ) {
                                SendTronScreen(
                                    title,
                                    findNavController(),
                                    sendTronViewModel,
                                    amountInputModeViewModel,
                                    sendEntryPointDestId,
                                    prefilledData,
                                )
                            }
                        }
                    }

                    BlockchainType.Monero -> {
                        setContent {
                            val factory = SendMoneroModule.Factory(wallet, address, hideAddress)
                            val sendMoneroViewModel by navGraphViewModels<SendMoneroViewModel>(R.id.sendXFragment) { factory }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                            ) {
                                SendMoneroScreen(
                                    title = title,
                                    navController = findNavController(),
                                    viewModel = sendMoneroViewModel,
                                    amountInputModeViewModel = amountInputModeViewModel,
                                    sendEntryPointDestId = sendEntryPointDestId,
                                    prefilledData = prefilledData,
                                )
                            }
                        }
                    }

                    BlockchainType.Stellar -> {
                        val factory = SendStellarModule.Factory(wallet, address, hideAddress)
                        val sendStellarViewModel by navGraphViewModels<SendStellarViewModel>(R.id.sendXFragment) { factory }
                        setContent {
                            SendStellarScreen(
                                title,
                                findNavController(),
                                sendStellarViewModel,
                                amountInputModeViewModel,
                                sendEntryPointDestId,
                                amount,
                            )
                        }
                    }

                    else -> {
                        setContent {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                            ) {
                                Text(
                                    text = "Unsupported yet",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                findNavController().popBackStack()
            }
        }
    }

    @Parcelize
    data class Input(
        val wallet: Wallet,
        val title: String,
        val sendEntryPointDestId: Int = 0,
        val address: Address,
        val amount: BigDecimal? = null,
        val hideAddress: Boolean = false
    ) : Parcelable
}
