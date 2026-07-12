package cash.p.terminal.modules.blockchainstatus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cash.p.terminal.core.isBtcLike
import cash.p.terminal.core.isEvm
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.compose.koinInject

/**
 * Builds a [BlockchainStatusProvider] for any supported [Blockchain].
 * Shared by [BlockchainStatusFragment] and the nested "status" routes of the
 * per-chain settings screens (Btc/Evm/Solana) to avoid duplicating provider construction.
 */
@Composable
internal fun rememberBlockchainStatusProvider(blockchain: Blockchain): BlockchainStatusProvider {
    // Only the manager(s) needed for this blockchain type are resolved, so unrelated Koin
    // singletons are not forced into creation. EVM and BTC-like chains are grouped via the
    // shared isEvm / isBtcLike predicates; all branches are mutually exclusive.
    val type = blockchain.type
    return when {
        type.isEvm -> {
            val evmBlockchainManager = koinInject<EvmBlockchainManager>()
            remember(blockchain, evmBlockchainManager) {
                EvmBlockchainStatusProvider(blockchain, evmBlockchainManager)
            }
        }

        type.isBtcLike -> {
            val btcBlockchainManager = koinInject<BtcBlockchainManager>()
            val walletManager = koinInject<IWalletManager>()
            val adapterManager = koinInject<IAdapterManager>()
            remember(blockchain, btcBlockchainManager, walletManager, adapterManager) {
                BtcBlockchainStatusProvider(blockchain, btcBlockchainManager, walletManager, adapterManager)
            }
        }

        type == BlockchainType.Solana -> {
            val solanaKitManager = koinInject<SolanaKitManager>()
            remember(solanaKitManager) { SolanaBlockchainStatusProvider(solanaKitManager) }
        }

        type == BlockchainType.Tron -> {
            val tronKitManager = koinInject<TronKitManager>()
            remember(tronKitManager) { TronBlockchainStatusProvider(tronKitManager) }
        }

        type == BlockchainType.Ton -> {
            val tonKitManager = koinInject<TonKitManager>()
            remember(tonKitManager) { TonBlockchainStatusProvider(tonKitManager) }
        }

        type == BlockchainType.Monero -> {
            val moneroKitManager = koinInject<MoneroKitManager>()
            remember(moneroKitManager) { MoneroBlockchainStatusProvider(moneroKitManager) }
        }

        type == BlockchainType.Stellar -> {
            val stellarKitManager = koinInject<StellarKitManager>()
            remember(stellarKitManager) { StellarBlockchainStatusProvider(stellarKitManager) }
        }

        type == BlockchainType.Zcash -> {
            val walletManager = koinInject<IWalletManager>()
            val adapterManager = koinInject<IAdapterManager>()
            remember(walletManager, adapterManager) {
                ZcashBlockchainStatusProvider(walletManager, adapterManager)
            }
        }

        else -> error("Unsupported blockchain type for status: ${type.uid}")
    }
}
