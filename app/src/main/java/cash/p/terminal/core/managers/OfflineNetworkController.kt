package cash.p.terminal.core.managers

import cash.p.terminal.core.adapters.BitcoinBaseAdapter
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType

/**
 * Single place that knows, per blockchain family, how to pause/resume a wallet's underlying
 * network kit and how to read back whether it is currently offline. [OfflineModeUseCase] is the
 * only caller: it drives one member at a time and never runs two ops for the same member concurrently.
 */
class OfflineNetworkController(
    private val adapterManager: IAdapterManager,
    private val evmBlockchainManager: EvmBlockchainManager,
    private val solanaKitManager: SolanaKitManager,
    private val tronKitManager: TronKitManager,
    private val tonKitManager: TonKitManager,
    private val stellarKitManager: StellarKitManager,
    private val moneroKitManager: MoneroKitManager,
) {
    suspend fun pause(member: Wallet) {
        adapterManager.getAdapterForWalletOld(member)?.pauseNetwork()
        val account = member.account
        when (val blockchainType = member.token.blockchainType) {
            BlockchainType.Solana -> solanaKitManager.pauseNetwork(account)
            BlockchainType.Tron -> tronKitManager.pauseNetwork(account)
            BlockchainType.Ton -> tonKitManager.pauseNetwork(account)
            BlockchainType.Stellar -> stellarKitManager.pauseNetwork(account)
            BlockchainType.Monero -> moneroKitManager.pauseNetwork(account)
            in EVM_BLOCKCHAIN_TYPES -> evmBlockchainManager.getEvmKitManager(blockchainType).pauseNetwork(account)
            else -> Unit
        }
    }

    suspend fun resume(member: Wallet) {
        adapterManager.getAdapterForWalletOld(member)?.resumeNetwork()
        val account = member.account
        when (val blockchainType = member.token.blockchainType) {
            BlockchainType.Solana -> solanaKitManager.resumeNetwork(account)
            BlockchainType.Tron -> tronKitManager.resumeNetwork(account)
            BlockchainType.Ton -> tonKitManager.resumeNetwork(account)
            BlockchainType.Stellar -> stellarKitManager.resumeNetwork(account)
            BlockchainType.Monero -> moneroKitManager.resumeNetwork(account)
            in EVM_BLOCKCHAIN_TYPES -> evmBlockchainManager.getEvmKitManager(blockchainType).resumeNetwork(account)
            else -> Unit
        }
    }

    /** Authoritative "is this member's network paused" read, per family. */
    fun isOffline(member: Wallet): Boolean {
        val account = member.account
        return when (val blockchainType = member.token.blockchainType) {
            BlockchainType.Solana ->
                isDisconnected(
                    solanaKitManager.currentAccount, account, solanaKitManager.solanaKitWrapper?.networkStarted,
                )

            BlockchainType.Tron ->
                isDisconnected(tronKitManager.currentAccount, account, tronKitManager.tronKitWrapper?.networkStarted)

            BlockchainType.Ton ->
                isDisconnected(tonKitManager.currentAccount, account, tonKitManager.tonKitWrapper?.networkStarted)

            BlockchainType.Stellar ->
                isDisconnected(
                    stellarKitManager.currentAccount, account, stellarKitManager.stellarKitWrapper?.networkStarted,
                )

            BlockchainType.Monero ->
                isDisconnected(
                    moneroKitManager.currentAccount, account, moneroKitManager.moneroKitWrapper?.isNetworkOnline,
                )

            in EVM_BLOCKCHAIN_TYPES -> {
                val kitManager = evmBlockchainManager.getEvmKitManager(blockchainType)
                isDisconnected(kitManager.currentAccount, account, kitManager.evmKitWrapper?.evmKit?.isStarted)
            }

            BlockchainType.Zcash ->
                (adapterManager.getAdapterForWalletOld(member) as? ZcashAdapter)?.isNetworkPaused ?: true

            else -> (adapterManager.getAdapterForWalletOld(member) as? BitcoinBaseAdapter)?.kit?.isNetworkPaused ?: true
        }
    }

    private fun isDisconnected(kitAccount: Account?, account: Account, started: Boolean?): Boolean =
        kitAccount != account || started != true

    private companion object {
        val EVM_BLOCKCHAIN_TYPES = setOf(
            BlockchainType.Ethereum,
            BlockchainType.BinanceSmartChain,
            BlockchainType.Polygon,
            BlockchainType.Avalanche,
            BlockchainType.Optimism,
            BlockchainType.ArbitrumOne,
            BlockchainType.Gnosis,
            BlockchainType.Fantom,
            BlockchainType.Base,
            BlockchainType.ZkSync,
        )
    }
}
