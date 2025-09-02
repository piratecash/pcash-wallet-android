package cash.p.terminal.modules.walletconnect.handler

import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Chain

fun Chain.toBlockchainType(): BlockchainType {
    return when(this) {
        Chain.Ethereum -> BlockchainType.Ethereum
        Chain.BinanceSmartChain -> BlockchainType.BinanceSmartChain
        Chain.Polygon -> BlockchainType.Polygon
        Chain.Avalanche -> BlockchainType.Avalanche
        Chain.Optimism -> BlockchainType.Optimism
        Chain.ArbitrumOne -> BlockchainType.ArbitrumOne
        Chain.Gnosis -> BlockchainType.Gnosis
        Chain.Base -> BlockchainType.Base
        Chain.ZkSync -> BlockchainType.ZkSync
        Chain.Fantom -> BlockchainType.Fantom
        Chain.EthereumGoerli -> BlockchainType.Unsupported("ethereum-goerli")
    }
}