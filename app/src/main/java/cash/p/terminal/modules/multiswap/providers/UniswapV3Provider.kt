package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.R
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.uniswapkit.models.DexType

object UniswapV3Provider : BaseUniswapV3Provider(DexType.Uniswap) {
    override val id = "uniswap_v3"
    override val title = "Uniswap V3"
    override val url = "https://uniswap.org/"
    override val icon = R.drawable.uniswap_v3
    override val priority = 0

    override suspend fun supports(token: Token) = when (token) {
        BlockchainType.Ethereum,
        BlockchainType.ArbitrumOne,
//            BlockchainType.Optimism,
        BlockchainType.Polygon,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        -> true
        else -> false
    }
}
