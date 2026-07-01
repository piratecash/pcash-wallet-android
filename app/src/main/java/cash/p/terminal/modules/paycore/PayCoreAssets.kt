package cash.p.terminal.modules.paycore

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType

object PayCoreAssets {
    const val RUB_COIN_UID = "rub"
    const val FIAT = "fiat"

    private val rubCoin = Coin(
        uid = RUB_COIN_UID,
        name = "Russian Ruble",
        code = "RUB",
        marketCapRank = null,
        coinGeckoId = null,
        image = null
    )

    val rubToken = Token(
        coin = rubCoin,
        blockchain = Blockchain(BlockchainType.Unsupported(FIAT), "Fiat", null),
        type = TokenType.Unsupported(FIAT, "rub"),
        decimals = 2
    )

    fun isFiat(token: Token): Boolean = token.blockchainType.uid == FIAT
    fun isRub(token: Token): Boolean = token.coin.uid == RUB_COIN_UID
    fun isRub(coinUid: String): Boolean = coinUid == RUB_COIN_UID
}
