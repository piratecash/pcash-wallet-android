package cash.p.terminal.entities

import cash.p.terminal.R
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.wallet.imageUrl
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.entities.Coin
import java.math.BigDecimal
import java.math.BigInteger

sealed interface TransactionValue {
    val fullName: String
    val coinUid: String
    val coinCode: String
    val coin: Coin?
    val badge: String?
    val coinIconUrl: String?
    val alternativeCoinIconUrl: String?
    val coinIconPlaceholder: Int?
    val decimalValue: BigDecimal?
    val decimals: Int?
    val zeroValue: Boolean
    val isMaxValue: Boolean
    val abs: TransactionValue
    val formattedString: String

    val nftUid: NftUid?
        get() = null

    data class JettonValue(
        val name: String,
        val symbol: String,
        override val decimals: Int,
        val value: BigDecimal,
        val image: String?
    ) : TransactionValue {
        override val fullName = name
        override val coinUid = symbol
        override val coinCode = symbol
        override val coin = null
        override val badge = "JETTON"
        override val coinIconUrl = image
        override val alternativeCoinIconUrl = null
        override val coinIconPlaceholder = R.drawable.the_open_network_jetton
        override val decimalValue = value
        override val zeroValue: Boolean
            get() = value.compareTo(BigDecimal.ZERO) == 0
        override val isMaxValue: Boolean
            get() = value.isMaxValue(decimals)
        override val abs: TransactionValue
            get() = copy(value = value.abs())
        override val formattedString: String
            get() = "n/a"

    }

    data class CoinValue(val token: Token, val value: BigDecimal) : TransactionValue {
        override val coin: Coin = token.coin
        override val badge: String? = token.badge
        override val coinIconUrl = token.coin.imageUrl
        override val alternativeCoinIconUrl = token.coin.alternativeImageUrl
        override val coinIconPlaceholder = token.fullCoin.iconPlaceholder
        override val coinUid: String = coin.uid
        override val fullName: String = coin.name
        override val coinCode: String = coin.code
        override val decimalValue: BigDecimal = value
        override val decimals: Int = token.decimals
        override val zeroValue: Boolean
            get() = value.compareTo(BigDecimal.ZERO) == 0
        override val isMaxValue: Boolean
            get() = value.isMaxValue(token.decimals)
        override val abs: TransactionValue
            get() = copy(value = value.abs())
        override val formattedString: String
            get() = "n/a"

    }

    data class RawValue(val value: BigInteger) : TransactionValue {
        override val coinUid: String = ""
        override val coin: Coin? = null
        override val badge: String? = null
        override val coinIconUrl = null
        override val alternativeCoinIconUrl = null
        override val coinIconPlaceholder = null
        override val fullName: String = ""
        override val coinCode: String = ""
        override val decimalValue: BigDecimal? = null
        override val decimals: Int? = null
        override val zeroValue: Boolean
            get() = value.compareTo(BigInteger.ZERO) == 0
        override val isMaxValue: Boolean = false
        override val abs: TransactionValue
            get() = copy(value = value.abs())
        override val formattedString: String
            get() = "n/a"

    }

    data class TokenValue(
        val tokenName: String,
        val tokenCode: String,
        val tokenDecimals: Int,
        val value: BigDecimal,
        override val coinIconPlaceholder: Int? = null
    ) : TransactionValue {
        override val coinUid: String = ""
        override val coin: Coin? = null
        override val badge: String? = null
        override val coinIconUrl = null
        override val alternativeCoinIconUrl = null
        override val fullName: String
            get() = tokenName
        override val coinCode: String
            get() = tokenCode
        override val decimalValue: BigDecimal = value
        override val decimals: Int = tokenDecimals
        override val zeroValue: Boolean
            get() = value.compareTo(BigDecimal.ZERO) == 0
        override val isMaxValue: Boolean
            get() = value.isMaxValue(tokenDecimals)
        override val abs: TransactionValue
            get() = copy(value = value.abs())
        override val formattedString: String
            get() = "n/a"

    }

    data class NftValue(
        override val nftUid: NftUid,
        val value: BigDecimal,
        val tokenName: String?,
        val tokenSymbol: String?
    ) : TransactionValue {
        override val coinUid: String = ""
        override val coin: Coin? = null
        override val badge: String? = null
        override val coinIconUrl = null
        override val alternativeCoinIconUrl = null
        override val coinIconPlaceholder: Int? = null
        override val fullName: String
            get() = "${tokenName ?: ""} #${nftUid.tokenId}"
        override val coinCode: String
            get() = tokenSymbol ?: "NFT"
        override val decimalValue: BigDecimal = value
        override val decimals: Int? = null
        override val zeroValue: Boolean
            get() = value.compareTo(BigDecimal.ZERO) == 0
        override val isMaxValue = false
        override val abs: TransactionValue
            get() = copy(value = value.abs())
        override val formattedString: String
            get() = "n/a"

    }
}
