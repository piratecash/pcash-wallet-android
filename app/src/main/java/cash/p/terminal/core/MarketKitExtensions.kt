package cash.p.terminal.core

import androidx.compose.ui.graphics.Color
import cash.p.terminal.R
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.entities.FeePriceScale
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Derivation
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.accountTypeDerivation
import cash.p.terminal.wallet.bitcoinCashCoinType
import cash.p.terminal.wallet.customCoinUid
import cash.p.terminal.wallet.entities.BitcoinCashCoinType
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.models.CoinPrice
import cash.p.terminal.wallet.models.HsPointTimePeriod
import cash.p.terminal.wallet.models.TopPlatform
import cash.p.terminal.wallet.protocolType
import cash.p.terminal.wallet.zCashCoinType
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.ExtendedKeyCoinType
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.nftkit.models.NftType
import java.math.BigDecimal

val Token.isCustom: Boolean
    get() = coin.uid == tokenQuery.customCoinUid

val Token.isSupported: Boolean
    get() = tokenQuery.isSupported

val Token.iconPlaceholder: Int
    get() = when (type) {
        is TokenType.Eip20 -> blockchainType.tokenIconPlaceholder
        else -> R.drawable.coin_placeholder
    }

val Token.swappable: Boolean
    get() = when (blockchainType) {
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.Avalanche,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.ArbitrumOne,
        BlockchainType.Bitcoin,
        BlockchainType.BitcoinCash,
        BlockchainType.Litecoin -> true

        else -> false
    }

val TokenQuery.isSupported: Boolean
    get() = when (blockchainType) {
        BlockchainType.Bitcoin,
        BlockchainType.Litecoin -> {
            tokenType is TokenType.Derived
        }

        BlockchainType.BitcoinCash -> {
            tokenType is TokenType.AddressTyped
        }

        BlockchainType.PirateCash,
        BlockchainType.Cosanta,
        BlockchainType.ECash,
        BlockchainType.Dogecoin,
        BlockchainType.Monero,
        BlockchainType.Dash -> {
            tokenType is TokenType.Native
        }

        BlockchainType.Zcash -> {
            tokenType is TokenType.AddressSpecTyped
        }

        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.ArbitrumOne,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.Avalanche -> {
            tokenType is TokenType.Native || tokenType is TokenType.Eip20
        }

        BlockchainType.Solana -> {
            tokenType is TokenType.Native || tokenType is TokenType.Spl
        }

        BlockchainType.Tron -> {
            tokenType is TokenType.Native || tokenType is TokenType.Eip20
        }

        BlockchainType.Ton -> {
            tokenType is TokenType.Native || tokenType is TokenType.Jetton
        }

        BlockchainType.Stellar -> {
            tokenType is TokenType.Native || tokenType is TokenType.Asset
        }

        is BlockchainType.Unsupported -> false
    }

val Blockchain.description: String
    get() = when (type) {
        BlockchainType.Bitcoin -> "BTC (BIP44, BIP49, BIP84, BIP86)"
        BlockchainType.BitcoinCash -> "BCH (Legacy, CashAddress)"
        BlockchainType.ECash -> "XEC"
        BlockchainType.Zcash -> "ZEC"
        BlockchainType.Litecoin -> "LTC (BIP44, BIP49, BIP84, BIP86)"
        BlockchainType.Dash -> "DASH"
        BlockchainType.Ethereum -> "ETH, ERC20 tokens"
        BlockchainType.BinanceSmartChain -> "BNB, BEP20 tokens"
        BlockchainType.Polygon -> "MATIC, ERC20 tokens"
        BlockchainType.Avalanche -> "AVAX, ERC20 tokens"
        BlockchainType.Optimism -> "L2 chain"
        BlockchainType.Base -> "L2 chain"
        BlockchainType.ZkSync -> "L2 chain"
        BlockchainType.ArbitrumOne -> "L2 chain"
        BlockchainType.Solana -> "SOL, SPL tokens"
        BlockchainType.Gnosis -> "xDAI, ERC20 tokens"
        BlockchainType.Fantom -> "FTM, ERC20 tokens"
        BlockchainType.Tron -> "TRX, TRC20 tokens"
        BlockchainType.Ton -> "TON"
        BlockchainType.Stellar -> "XLM, Stellar assets"
        else -> ""
    }

fun Blockchain.eip20TokenUrl(address: String) = eip3091url?.replace("\$ref", address)

fun Blockchain.jettonUrl(address: String) = "https://tonviewer.com/$address"
fun Blockchain.assetUrl(code: String, issuer: String) = "https://stellarchain.io/assets/$code-$issuer"

val BlockchainType.restoreSettingTypes: List<RestoreSettingType>
    get() = when (this) {
        BlockchainType.Zcash -> listOf(RestoreSettingType.BirthdayHeight)
        else -> listOf()
    }

private val blockchainOrderMap: Map<BlockchainType, Int> by lazy {
    val map = mutableMapOf<BlockchainType, Int>()
    listOf(
        BlockchainType.Bitcoin,
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Tron,
        BlockchainType.Ton,
        BlockchainType.Solana,
        BlockchainType.Polygon,
        BlockchainType.Stellar,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.Avalanche,
        BlockchainType.Zcash,
        BlockchainType.BitcoinCash,
        BlockchainType.ECash,
        BlockchainType.Litecoin,
        BlockchainType.Dash,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.ArbitrumOne,
        BlockchainType.Optimism,
    ).forEachIndexed { index, blockchainType ->
        map[blockchainType] = index
    }
    map
}

val BlockchainType.order: Int
    get() = blockchainOrderMap[this] ?: Int.MAX_VALUE

val BlockchainType.tokenIconPlaceholder: Int
    get() = when (this) {
        BlockchainType.Ethereum -> R.drawable.erc20
        BlockchainType.BinanceSmartChain -> R.drawable.bep20
        BlockchainType.Avalanche -> R.drawable.avalanche_erc20
        BlockchainType.Polygon -> R.drawable.polygon_erc20
        BlockchainType.Optimism -> R.drawable.optimism_erc20
        BlockchainType.Base -> R.drawable.base_erc20
        BlockchainType.ZkSync -> R.drawable.zksync_erc20
        BlockchainType.ArbitrumOne -> R.drawable.arbitrum_erc20
        BlockchainType.Gnosis -> R.drawable.gnosis_erc20
        BlockchainType.Fantom -> R.drawable.fantom_erc20
        BlockchainType.Tron -> R.drawable.tron_trc20
        BlockchainType.Ton -> R.drawable.the_open_network_jetton
        BlockchainType.Stellar -> R.drawable.stellar_asset
        else -> R.drawable.coin_placeholder
    }

val BlockchainType.supportedNftTypes: List<NftType>
    get() = when (this) {
        BlockchainType.Ethereum -> listOf(NftType.Eip721, NftType.Eip1155)
//        BlockchainType.BinanceSmartChain -> listOf(NftType.Eip721)
//        BlockchainType.Polygon -> listOf(NftType.Eip721, NftType.Eip1155)
//        BlockchainType.Avalanche -> listOf(NftType.Eip721)
//        BlockchainType.ArbitrumOne -> listOf(NftType.Eip721)
        else -> listOf()
    }

val BlockchainType.brandColor: Color?
    get() = when (this) {
        BlockchainType.Ethereum -> Color(0xFF6B7196)
        BlockchainType.BinanceSmartChain -> Color(0xFFF3BA2F)
        BlockchainType.Polygon -> Color(0xFF8247E5)
        BlockchainType.Avalanche -> Color(0xFFD74F49)
        BlockchainType.Optimism -> Color(0xFFEB3431)
        BlockchainType.Base -> Color(0xFF2759F6)
        BlockchainType.ZkSync -> Color(0xFF8D8FF0)
        BlockchainType.ArbitrumOne -> Color(0xFF96BEDC)
        else -> null
    }

val BlockchainType.feePriceScale: FeePriceScale
    get() = when (this) {
        BlockchainType.Avalanche -> FeePriceScale.Navax
        else -> FeePriceScale.Gwei
    }

fun BlockchainType.supports(accountType: AccountType): Boolean {
    return when (accountType) {
        is AccountType.ZCashUfvKey ->
            this == BlockchainType.Zcash

        is AccountType.HardwareCard,
        is AccountType.MnemonicMonero,
        is AccountType.Mnemonic -> true

        is AccountType.HdExtendedKey -> {
            val coinTypes = accountType.hdExtendedKey.coinTypes
            when (this) {
                BlockchainType.Bitcoin -> coinTypes.contains(ExtendedKeyCoinType.Bitcoin)
                BlockchainType.Litecoin -> coinTypes.contains(ExtendedKeyCoinType.Litecoin)
                BlockchainType.BitcoinCash,
                BlockchainType.Dash,
                BlockchainType.ECash -> coinTypes.contains(ExtendedKeyCoinType.Bitcoin) && accountType.hdExtendedKey.purposes.contains(
                    HDWallet.Purpose.BIP44
                )

                else -> false
            }
        }

        is AccountType.BitcoinAddress -> {
            this === accountType.blockchainType
        }

        is AccountType.EvmAddress ->
            this == BlockchainType.Ethereum
                    || this == BlockchainType.BinanceSmartChain
                    || this == BlockchainType.Polygon
                    || this == BlockchainType.Avalanche
                    || this == BlockchainType.Optimism
                    || this == BlockchainType.Base
                    || this == BlockchainType.ZkSync
                    || this == BlockchainType.ArbitrumOne
                    || this == BlockchainType.Gnosis
                    || this == BlockchainType.Fantom

        is AccountType.EvmPrivateKey -> {
            this == BlockchainType.Ethereum
                    || this == BlockchainType.BinanceSmartChain
                    || this == BlockchainType.Polygon
                    || this == BlockchainType.Avalanche
                    || this == BlockchainType.Optimism
                    || this == BlockchainType.Base
                    || this == BlockchainType.ZkSync
                    || this == BlockchainType.ArbitrumOne
                    || this == BlockchainType.Gnosis
                    || this == BlockchainType.Fantom
        }

        is AccountType.SolanaAddress ->
            this == BlockchainType.Solana

        is AccountType.TronAddress ->
            this == BlockchainType.Tron

        is AccountType.TonAddress ->
            this == BlockchainType.Ton

        is AccountType.StellarAddress ->
            this == BlockchainType.Stellar

        is AccountType.StellarSecretKey ->
            this == BlockchainType.Stellar
    }
}

val TokenType.order: Int
    get() {
        return when (this) {
            TokenType.Native -> 0
            is TokenType.Derived -> derivation.accountTypeDerivation.order
            is TokenType.AddressTyped -> type.bitcoinCashCoinType.ordinal
            else -> Int.MAX_VALUE
        }
    }


val TokenType.derivation: TokenType.Derivation?
    get() = when (this) {
        is TokenType.Derived -> this.derivation
        else -> null
    }

val TokenType.bitcoinCashCoinType: TokenType.AddressType?
    get() = when (this) {
        is TokenType.AddressTyped -> this.type
        else -> null
    }

val TopPlatform.imageUrl
    get() = "https://cdn.blocksdecoded.com/blockchain-icons/32px/${blockchain.uid}@3x.png"

val FullCoin.typeLabel: String?
    get() = tokens.singleOrNull()?.protocolType

val FullCoin.supportedTokens
    get() = tokens
        .filter { it.isSupported }
        .sortedWith(compareBy({ it.type.order }, { it.blockchain.type.order }))

val FullCoin.iconPlaceholder: Int
    get() = if (tokens.size == 1) {
        tokens.first().iconPlaceholder
    } else {
        // TODO Add static images for PirateCash and Cosanta
        var pirate: String = "piratecash"
        var cosa: String = "cosanta"
        when (coin.uid) {
            pirate -> {
                R.drawable.ic_piratecash
            }

            cosa -> {
                R.drawable.ic_cosanta
            }

            else -> {
                R.drawable.coin_placeholder
            }
        }
    }

fun Token.supports(accountType: AccountType): Boolean {
    return when (accountType) {
        is AccountType.BitcoinAddress -> {
            tokenQuery.tokenType == accountType.tokenType
        }

        is AccountType.HdExtendedKey -> {
            when (blockchainType) {
                BlockchainType.Bitcoin,
                BlockchainType.Dogecoin,
                BlockchainType.Litecoin -> {
                    val type = type
                    if (type is TokenType.Derived) {
                        if (!accountType.hdExtendedKey.purposes.contains(type.derivation.purpose)) {
                            false
                        } else if (blockchainType == BlockchainType.Bitcoin) {
                            accountType.hdExtendedKey.coinTypes.contains(ExtendedKeyCoinType.Bitcoin)
                        } else {
                            accountType.hdExtendedKey.coinTypes.contains(ExtendedKeyCoinType.Litecoin)
                        }
                    } else {
                        false
                    }
                }

                BlockchainType.BitcoinCash,
                BlockchainType.ECash,
                BlockchainType.Dash -> {
                    accountType.hdExtendedKey.purposes.contains(HDWallet.Purpose.BIP44)
                }

                else -> false
            }
        }

        else -> true
    }
}

fun FullCoin.eligibleTokens(accountType: AccountType): List<Token> {
    return supportedTokens
        .filter { it.supports(accountType) && it.blockchainType.supports(accountType) }
}

val HsPointTimePeriod.title: Int
    get() = when (this) {
        HsPointTimePeriod.Hour1 -> R.string.Coin_Analytics_Period_1h
        HsPointTimePeriod.Day1 -> R.string.Coin_Analytics_Period_1d
        HsPointTimePeriod.Week1 -> R.string.Coin_Analytics_Period_1w
        HsPointTimePeriod.Month1 -> R.string.Coin_Analytics_Period_1m
        HsPointTimePeriod.Minute30 -> R.string.Coin_Analytics_Period_30m
        HsPointTimePeriod.Hour4 -> R.string.Coin_Analytics_Period_4h
        HsPointTimePeriod.Hour8 -> R.string.Coin_Analytics_Period_8h
    }

val TokenType.Derivation.purpose: HDWallet.Purpose
    get() = when (this) {
        TokenType.Derivation.Bip44 -> HDWallet.Purpose.BIP44
        TokenType.Derivation.Bip49 -> HDWallet.Purpose.BIP49
        TokenType.Derivation.Bip84 -> HDWallet.Purpose.BIP84
        TokenType.Derivation.Bip86 -> HDWallet.Purpose.BIP86
    }

val TokenType.AddressType.kitCoinType: MainNetBitcoinCash.CoinType
    get() = when (this) {
        TokenType.AddressType.Type0 -> MainNetBitcoinCash.CoinType.Type0
        TokenType.AddressType.Type145 -> MainNetBitcoinCash.CoinType.Type145
    }

val BlockchainType.nativeTokenQueries: List<TokenQuery>
    get() = when (this) {
        BlockchainType.Bitcoin,
        BlockchainType.Litecoin -> {
            TokenType.Derivation.values().map {
                TokenQuery(this, TokenType.Derived(it))
            }
        }

        BlockchainType.BitcoinCash -> {
            TokenType.AddressType.values().map {
                TokenQuery(this, TokenType.AddressTyped(it))
            }
        }

        BlockchainType.Zcash -> {
            TokenType.AddressSpecType.values().map {
                TokenQuery(this, TokenType.AddressSpecTyped(it))
            }
        }

        else -> {
            listOf(TokenQuery(this, TokenType.Native))
        }
    }

val BlockchainType.defaultTokenQuery: TokenQuery
    get() = when (this) {
        BlockchainType.Bitcoin,
        BlockchainType.Litecoin -> {
            TokenQuery(this, TokenType.Derived(TokenType.Derivation.Bip84))
        }

        BlockchainType.BitcoinCash -> {
            TokenQuery(this, TokenType.AddressTyped(TokenType.AddressType.Type145))
        }

        else -> {
            TokenQuery(this, TokenType.Native)
        }
    }

val TokenType.title: String
    get() = when (this) {
        is TokenType.Derived -> derivation.accountTypeDerivation.rawName
        is TokenType.AddressTyped -> type.bitcoinCashCoinType.title
        is TokenType.AddressSpecTyped -> type.name
        else -> ""
    }

val TokenType.description: String
    get() = when (this) {
        is TokenType.Derived -> derivation.accountTypeDerivation.addressType + derivation.accountTypeDerivation.recommended
        is TokenType.AddressTyped -> Translator.getString(type.bitcoinCashCoinType.description)
        is TokenType.AddressSpecTyped -> Translator.getString(type.zCashCoinType.description)
        else -> ""
    }

val TokenType.isDefault
    get() = when (this) {
        is TokenType.Derived -> derivation.accountTypeDerivation == Derivation.default
        is TokenType.AddressTyped -> type.bitcoinCashCoinType == BitcoinCashCoinType.default
        else -> false
    }

val TokenType.isNative: Boolean
    get() = this is TokenType.Native ||
            this is TokenType.Derived ||
            this is TokenType.AddressTyped ||
            this is TokenType.AddressSpecTyped

val BlockchainType.Companion.supported: List<BlockchainType>
    get() = listOf(
        BlockchainType.Bitcoin,
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.Avalanche,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.ArbitrumOne,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.Zcash,
        BlockchainType.Dash,
        BlockchainType.BitcoinCash,
        BlockchainType.Litecoin,
        BlockchainType.Solana,
        BlockchainType.ECash,
        BlockchainType.Tron,
        BlockchainType.Ton,
        BlockchainType.Stellar,
    )

val CoinPrice.diff: BigDecimal?
    get() = when (App.priceManager.priceChangeInterval) {
        PriceChangeInterval.LAST_24H -> diff24h
        PriceChangeInterval.FROM_UTC_MIDNIGHT -> diff1d
    }