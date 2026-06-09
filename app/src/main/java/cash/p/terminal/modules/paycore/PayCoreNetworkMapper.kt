package cash.p.terminal.modules.paycore

import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType

object PayCoreNetworkMapper {
    fun Token.toTicker(): PayCoreTicker? {
        if (PayCoreAssets.isRub(this)) return PayCoreTicker.RUB
        return fromBlockchainType(this.blockchainType)
    }

    fun fromBlockchainType(blockchainType: BlockchainType): PayCoreTicker? {
        return when (blockchainType) {
            BlockchainType.Tron -> PayCoreTicker.USDT
            BlockchainType.Ethereum -> PayCoreTicker.USDT_ERC20
            BlockchainType.Solana -> PayCoreTicker.USDT_SPL
            else -> null
        }
    }

    fun payCoreNetworkTypeFromBlockchainTypeUid(blockchainTypeUid: String): PayCoreTicker?
            = fromBlockchainType(BlockchainType.fromUid(blockchainTypeUid))

    private const val TETHER_COIN_UID = "tether"

    fun isUsdtOnSupportedNetwork(token: Token): Boolean {
        if (token.coin.uid != TETHER_COIN_UID) return false
        return fromBlockchainType(token.blockchainType) != null
    }
}
