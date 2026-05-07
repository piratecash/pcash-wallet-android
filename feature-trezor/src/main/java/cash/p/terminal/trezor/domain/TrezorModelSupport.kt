package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.BlockchainType

object TrezorModelSupport {

    private val universalBlockchains = setOf(
        BlockchainType.Bitcoin,
        BlockchainType.Litecoin,
        BlockchainType.BitcoinCash,
        BlockchainType.Dogecoin,
        // TODO Zcash SDK (v2.4.4) rejects transparent-only UFVKs (ZIP 316 Revision 1 not yet supported)
        // BlockchainType.Zcash,
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.ArbitrumOne,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.Stellar,
        // Tron: deep link API does not support tronGetAddress yet
    )

    fun getSupportedBlockchains(model: TrezorModel?): Set<BlockchainType> {
        if (model == null) return universalBlockchains

        return buildSet {
            addAll(universalBlockchains)
            when (model) {
                TrezorModel.One -> add(BlockchainType.Dash)
                TrezorModel.ModelT -> {
                    add(BlockchainType.Dash)
                    add(BlockchainType.Solana)
                }
                TrezorModel.Safe3,
                TrezorModel.Safe5,
                TrezorModel.Safe7 -> add(BlockchainType.Solana)
            }
        }
    }

    fun isSupported(model: TrezorModel?, blockchainType: BlockchainType): Boolean =
        blockchainType in getSupportedBlockchains(model)

    fun getDefaultTokenQueries(model: TrezorModel?): List<TokenQuery> {
        val supported = getSupportedBlockchains(model)
        return TokenQuery.defaultTokenQueries.filter { it.blockchainType in supported }
    }
}
