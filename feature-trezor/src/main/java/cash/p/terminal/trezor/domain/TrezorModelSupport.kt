package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.TrezorFeatures
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
                    add(BlockchainType.Tron)
                }
                TrezorModel.Safe3,
                TrezorModel.Safe5,
                TrezorModel.Safe7 -> {
                    add(BlockchainType.Solana)
                    add(BlockchainType.Tron)
                }
            }
        }
    }

    fun isSupported(model: TrezorModel?, blockchainType: BlockchainType): Boolean =
        blockchainType in getSupportedBlockchains(model)

    fun getDefaultTokenQueries(model: TrezorModel?): List<TokenQuery> {
        val supported = getSupportedBlockchains(model)
        return TokenQuery.defaultTokenQueries.filter { it.blockchainType in supported }
    }

    /**
     * Drops token queries the connected device cannot derive on its current firmware. Model support
     * advertises Tron for every Safe/Model T, but Tron signing landed only in core firmware 2.11.0;
     * on older firmware a TronGetAddress is rejected and fails the whole derivation batch, so Tron
     * must be removed unless the device reports [TrezorFeatures.supportsTron].
     */
    fun filterByFirmwareCapabilities(
        tokenQueries: List<TokenQuery>,
        features: TrezorFeatures
    ): List<TokenQuery> =
        if (features.supportsTron) tokenQueries
        else tokenQueries.filterNot { it.blockchainType == BlockchainType.Tron }
}
