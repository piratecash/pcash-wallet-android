package cash.p.terminal.entities

import android.os.Parcelable
import io.horizontalsystems.bitcoincore.core.purpose
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.entities.TokenType
import kotlinx.parcelize.Parcelize

@Parcelize
open class Address(
    val hex: String,
    val domain: String? = null,
    val blockchainType: BlockchainType? = null,
) : Parcelable {
    val title: String
        get() = domain ?: hex
}

class BitcoinAddress(
    hex: String,
    domain: String?,
    blockchainType: BlockchainType?,
    val scriptType: ScriptType
) : Address(hex, domain, blockchainType)

private val ScriptType.derivation: TokenType.Derivation
    get() = when (this.purpose!!) {
        HDWallet.Purpose.BIP44 -> TokenType.Derivation.Bip44
        HDWallet.Purpose.BIP49 -> TokenType.Derivation.Bip49
        HDWallet.Purpose.BIP84 -> TokenType.Derivation.Bip84
        HDWallet.Purpose.BIP86 -> TokenType.Derivation.Bip86
    }

val BitcoinAddress.tokenType: TokenType
    get() = when (this.blockchainType) {
        BlockchainType.Bitcoin -> TokenType.Derived(this.scriptType.derivation)
        BlockchainType.BitcoinCash -> TokenType.AddressTyped(TokenType.AddressType.Type145)
        BlockchainType.Litecoin -> TokenType.Derived(this.scriptType.derivation)
        BlockchainType.Dogecoin -> TokenType.Derived(this.scriptType.derivation)

        BlockchainType.Cosanta,
        BlockchainType.PirateCash,
        BlockchainType.ECash,
        BlockchainType.Monero,
        BlockchainType.Dash -> TokenType.Native

        BlockchainType.Zcash,
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.Avalanche,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.ArbitrumOne,
        BlockchainType.Solana,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.Tron,
        BlockchainType.Ton,
        BlockchainType.Stellar,
        is BlockchainType.Unsupported,
        null -> TokenType.Unsupported("", "")
    }
