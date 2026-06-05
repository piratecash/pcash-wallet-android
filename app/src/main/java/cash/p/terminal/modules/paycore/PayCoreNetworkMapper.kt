package cash.p.terminal.modules.paycore

import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType

object PayCoreNetworkMapper {
    fun toNetworkType(token: Token): String? {
        if (PayCoreAssets.isRub(token)) return PayCoreNetworkType.RUB
        return fromBlockchainType(token.blockchainType)
    }

    fun fromBlockchainType(blockchainType: BlockchainType): String? {
        return when (blockchainType) {
            BlockchainType.Tron -> PayCoreNetworkType.TRC20
            BlockchainType.Ethereum -> PayCoreNetworkType.ERC20
            BlockchainType.Solana -> PayCoreNetworkType.SPL
            else -> null
        }
    }

    fun fromBlockchainTypeUid(blockchainTypeUid: String): String? {
        return fromBlockchainType(BlockchainType.fromUid(blockchainTypeUid))
    }

    fun fromCurrencies(currencyFrom: String, currencyTo: String): String? {
        return when {
            currencyFrom != PayCoreNetworkType.RUB -> normalizeNetworkType(currencyFrom)
            currencyTo != PayCoreNetworkType.RUB -> normalizeNetworkType(currencyTo)
            else -> null
        }
    }

    private const val TETHER_COIN_UID = "tether"

    internal fun normalizeNetworkType(value: String): String? {
        return when (value.uppercase()) {
            PayCoreNetworkType.TRC20 -> PayCoreNetworkType.TRC20
            PayCoreNetworkType.ERC20 -> PayCoreNetworkType.ERC20
            PayCoreNetworkType.SPL -> PayCoreNetworkType.SPL
            PayCoreNetworkType.RUB -> PayCoreNetworkType.RUB
            else -> null
        }
    }

    fun isUsdtOnSupportedNetwork(token: Token): Boolean {
        if (token.coin.uid != TETHER_COIN_UID) return false
        return fromBlockchainType(token.blockchainType) != null
    }
}
