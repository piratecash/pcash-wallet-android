package cash.p.terminal.core.managers

import cash.p.terminal.core.adapters.nft.EvmNftAdapter
import cash.p.terminal.core.adapters.nft.INftAdapter
import cash.p.terminal.core.supportedNftTypes
import cash.p.terminal.entities.nft.NftKey
import cash.p.terminal.wallet.IWalletManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import java.util.concurrent.ConcurrentHashMap

class NftAdapterManager(
    private val walletManager: IWalletManager,
    private val evmBlockchainManager: EvmBlockchainManager
) {
    private val _adaptersUpdatedFlow = MutableStateFlow<Map<NftKey, INftAdapter>>(mapOf())
    private var adaptersMap = ConcurrentHashMap<NftKey, INftAdapter>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    val adaptersUpdatedFlow = _adaptersUpdatedFlow.asStateFlow()

    init {
        coroutineScope.launch {
            walletManager.activeWalletsUpdatedObservable.asFlow()
                .collect {
                    //disable NFT adapters for now
                    //initAdapters(it)
                }
        }
    }

    fun adapter(nftKey: NftKey): INftAdapter? {
        return adaptersMap[nftKey]
    }

    fun refresh() {
        coroutineScope.launch {
            adaptersMap.values.forEach { it.sync() }
        }
    }

    @Synchronized
    private fun initAdapters(wallets: List<cash.p.terminal.wallet.Wallet>) {
        val currentAdapters = adaptersMap.toMutableMap()
        adaptersMap.clear()

        val nftKeys = wallets.map { NftKey(it.account, it.token.blockchainType) }.distinct()

        for (nftKey in nftKeys) {
            if (nftKey.blockchainType.supportedNftTypes.isEmpty()) continue

            val adapter = currentAdapters.remove(nftKey)

            if (adapter != null) {
                adaptersMap[nftKey] = adapter
            } else if (evmBlockchainManager.getBlockchain(nftKey.blockchainType) != null) {
                val evmKitManager = evmBlockchainManager.getEvmKitManager(nftKey.blockchainType)
                val evmKitWrapper = evmKitManager.getEvmKitWrapper(nftKey.account, nftKey.blockchainType)

                val nftKit = evmKitWrapper.nftKit
                if (nftKit != null) {
                    adaptersMap[nftKey] = EvmNftAdapter(nftKey.blockchainType, nftKit, evmKitWrapper.evmKit.receiveAddress)
                } else {
                    evmKitManager.unlink(nftKey.account)
                }
            } else {
                // Init other blockchain adapter here (e.g. Solana)
            }
        }

        currentAdapters.forEach { (nftKey, _) ->
            evmBlockchainManager.getEvmKitManager(nftKey.blockchainType).unlink(nftKey.account)
        }

        _adaptersUpdatedFlow.update { adaptersMap.toMap() }
    }
}