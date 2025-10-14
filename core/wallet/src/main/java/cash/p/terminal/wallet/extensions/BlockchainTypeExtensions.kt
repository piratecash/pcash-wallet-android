package cash.p.terminal.wallet.extensions

import io.horizontalsystems.core.entities.BlockchainType

internal fun BlockchainType.isEvmLike(): Boolean = when (this) {
    BlockchainType.ArbitrumOne,
    BlockchainType.Avalanche,
    BlockchainType.Base,
    BlockchainType.BinanceSmartChain,
    BlockchainType.Ethereum,
    BlockchainType.Fantom,
    BlockchainType.Gnosis,
    BlockchainType.Optimism,
    BlockchainType.Polygon,
    BlockchainType.ZkSync -> true

    BlockchainType.Bitcoin,
    BlockchainType.BitcoinCash,
    BlockchainType.Cosanta,
    BlockchainType.Dash,
    BlockchainType.Dogecoin,
    BlockchainType.ECash,
    BlockchainType.Litecoin,
    BlockchainType.Monero,
    BlockchainType.PirateCash,
    BlockchainType.Solana,
    BlockchainType.Stellar,
    BlockchainType.Ton,
    BlockchainType.Tron,
    BlockchainType.Zcash,
    is BlockchainType.Unsupported -> false
}
