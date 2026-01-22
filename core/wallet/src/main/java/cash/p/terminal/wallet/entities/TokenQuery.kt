package cash.p.terminal.wallet.entities

import cash.p.terminal.wallet.BuildConfig
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.extensions.isEvmLike
import java.util.Locale
import java.util.Objects

data class TokenQuery(
    val blockchainType: BlockchainType,
    val tokenType: TokenType
) {

    val id: String
        get() = listOf(blockchainType.uid, tokenType.id).joinToString("|")

    override fun equals(other: Any?): Boolean =
        other is TokenQuery && other.blockchainType == blockchainType && other.tokenType == tokenType

    override fun hashCode(): Int =
        Objects.hash(blockchainType, tokenType)

    companion object {

        fun fromId(id: String): TokenQuery? {
            val chunks = id.split("|")
            if (chunks.size != 2) return null

            val tokenType = TokenType.fromId(chunks[1]) ?: return null

            return TokenQuery(
                BlockchainType.fromUid(chunks[0]),
                tokenType
            )
        }

        val PirateCashBnb = eip20(BlockchainType.BinanceSmartChain, BuildConfig.PIRATE_CONTRACT)

        val CosantaBnb = eip20(BlockchainType.BinanceSmartChain, BuildConfig.COSANTA_CONTRACT)

        val PirateJetton = TokenQuery(BlockchainType.Ton, TokenType.Jetton(BuildConfig.PIRATE_JETTON_ADDRESS))

        fun eip20(blockchainType: BlockchainType, address: String): TokenQuery {
            val normalized = if (blockchainType.isEvmLike()) {
                address.lowercase(Locale.US)
            } else {
                address
            }

            return TokenQuery(blockchainType, TokenType.Eip20(normalized))
        }
    }
}
