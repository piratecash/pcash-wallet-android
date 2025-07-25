package cash.p.terminal.wallet

import android.os.Parcelable
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.hdwalletkit.WordList
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.math.BigInteger
import java.text.Normalizer

@Parcelize
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val origin: AccountOrigin,
    val level: Int,
    val isBackedUp: Boolean = false,
    val isFileBackedUp: Boolean = false,
) : Parcelable {

    @IgnoredOnParcel
    val accountSupportsBackup: Boolean
        get() = !isHardwareWalletAccount

    @IgnoredOnParcel
    val isHardwareWalletAccount: Boolean
        get() = type is AccountType.HardwareCard

    @IgnoredOnParcel
    val hasAnyBackup = isBackedUp || isFileBackedUp

    @IgnoredOnParcel
    val isWatchAccount: Boolean
        get() = type.isWatchAccountType

    @IgnoredOnParcel
    val nonStandard: Boolean by lazy {
        if (type is AccountType.Mnemonic) {
            val words = type.words.joinToString(separator = " ")
            val passphrase = type.passphrase
            val normalizedWords = words.normalizeNFKD()
            val normalizedPassphrase = passphrase.normalizeNFKD()

            when {
                words != normalizedWords -> true
                passphrase != normalizedPassphrase -> true
                else -> try {
                    Mnemonic().validateStrict(type.words)
                    false
                } catch (exception: Exception) {
                    true
                }
            }
        } else {
            false
        }
    }

    @IgnoredOnParcel
    val nonRecommended: Boolean by lazy {
        if (type is AccountType.Mnemonic) {
            val englishWords = WordList.wordList(Language.English).validWords(type.words)
            val standardPassphrase = PassphraseValidator().containsValidCharacters(type.passphrase)
            !englishWords || !standardPassphrase
        } else {
            false
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is Account) {
            return id == other.id
        }

        return false
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

@Parcelize
sealed class AccountType : Parcelable {
    @Parcelize
    data class EvmAddress(val address: String) : AccountType()

    @Parcelize
    data class SolanaAddress(val address: String) : AccountType()

    @Parcelize
    data class TronAddress(val address: String) : AccountType()

    @Parcelize
    data class TonAddress(val address: String) : AccountType()

    @Parcelize
    data class StellarAddress(val address: String) : AccountType()

    @Parcelize
    data class BitcoinAddress(
        val address: String,
        val blockchainType: BlockchainType,
        val tokenType: TokenType
    ) : AccountType() {

        val serialized: String
            get() = "$address|${blockchainType.uid}|${tokenType.id}"

        companion object {
            fun fromSerialized(serialized: String): BitcoinAddress {
                val split = serialized.split("|")
                return BitcoinAddress(
                    split[0],
                    BlockchainType.fromUid(split[1]),
                    TokenType.fromId(split[2])!!
                )
            }
        }
    }

    @Parcelize
    data class Mnemonic(val words: List<String>, val passphrase: String) : AccountType() {
        @IgnoredOnParcel
        val seed by lazy { Mnemonic().toSeed(words, passphrase) }

        override fun equals(other: Any?): Boolean {
            return other is Mnemonic
                    && words.toTypedArray().contentEquals(other.words.toTypedArray())
                    && passphrase == other.passphrase
        }

        override fun hashCode(): Int {
            return words.toTypedArray().contentHashCode() + passphrase.hashCode()
        }
    }

    @Parcelize
    data class StellarSecretKey(val key: String) : AccountType() {
        override fun equals(other: Any?): Boolean {
            return other is StellarSecretKey && key == other.key
        }

        override fun hashCode(): Int {
            return key.hashCode()
        }
    }

    @Parcelize
    data class MnemonicMonero(
        val words: List<String>,
        val password: String,
        val height: Long,
        val walletInnerName: String
    ) : AccountType()

    @Parcelize
    data class EvmPrivateKey(val key: BigInteger) : AccountType() {
        override fun equals(other: Any?): Boolean {
            return other is EvmPrivateKey && key == other.key
        }

        override fun hashCode(): Int {
            return key.hashCode()
        }
    }

    @Parcelize
    data class HdExtendedKey(val keySerialized: String) : AccountType() {
        val hdExtendedKey: HDExtendedKey
            get() = HDExtendedKey(keySerialized)

        override fun equals(other: Any?): Boolean {
            return other is HdExtendedKey && keySerialized.contentEquals(other.keySerialized)
        }

        override fun hashCode(): Int {
            return keySerialized.hashCode()
        }
    }

    @Parcelize
    data class ZCashUfvKey(val key: String) : AccountType() {
        override fun equals(other: Any?): Boolean {
            return other is ZCashUfvKey && key == other.key
        }

        override fun hashCode(): Int {
            return key.hashCode()
        }
    }

    @Parcelize
    data class HardwareCard(
        val cardId: String,
        val backupCardsCount: Int,
        val walletPublicKey: String,
        val signedHashes: Int
    ) : AccountType() {
        override fun equals(other: Any?): Boolean {
            return other is HardwareCard &&
                    cardId == other.cardId &&
                    backupCardsCount == other.backupCardsCount &&
                    walletPublicKey == other.walletPublicKey
        }

        override fun hashCode(): Int {
            return cardId.hashCode()
        }
    }


    val description: String
        get() = when (this) {
            is Mnemonic -> {
                val count = words.size

                if (passphrase.isNotBlank()) {
                    cash.p.terminal.strings.helpers.Translator.getString(
                        R.string.ManageAccount_NWordsWithPassphrase,
                        count
                    )
                } else {
                    cash.p.terminal.strings.helpers.Translator.getString(
                        R.string.ManageAccount_NWords,
                        count
                    )
                }
            }

            is BitcoinAddress -> "BTC Address"
            is EvmAddress -> "EVM Address"
            is SolanaAddress -> "Solana Address"
            is TronAddress -> "Tron Address"
            is TonAddress -> "Ton Address"
            is StellarAddress -> "Stellar Address"
            is StellarSecretKey -> "Stellar Secret Key"
            is EvmPrivateKey -> "EVM Private Key"
            is ZCashUfvKey -> "ZCash UFV Key"
            is HardwareCard -> "Hardware card"
            is MnemonicMonero -> "Monero Wallet"
            is HdExtendedKey -> {
                when (this.hdExtendedKey.derivedType) {
                    HDExtendedKey.DerivedType.Master -> "BIP32 Root Key"
                    HDExtendedKey.DerivedType.Account -> {
                        if (hdExtendedKey.isPublic) {
                            "Account xPubKey"
                        } else {
                            "Account xPrivKey"
                        }
                    }

                    else -> ""
                }
            }
        }

    val supportedDerivations: List<Derivation>
        get() = when (this) {
            is Mnemonic -> {
                listOf(Derivation.bip44, Derivation.bip49, Derivation.bip84, Derivation.bip86)
            }

            is HdExtendedKey -> {
                hdExtendedKey.purposes.map { it.derivation }
            }

            else -> emptyList()
        }

    val hideZeroBalances: Boolean
        get() = false

    val detailedDescription: String
        get() = when (this) {
            is EvmAddress -> this.address.shorten()
            is SolanaAddress -> this.address.shorten()
            is TronAddress -> this.address.shorten()
            is TonAddress -> this.address.shorten()
            is StellarAddress -> this.address.shorten()
            is BitcoinAddress -> this.address.shorten()
            else -> this.description
        }

    val canAddTokens: Boolean
        get() = when (this) {
            is Mnemonic, is EvmPrivateKey -> true
            else -> false
        }

    val supportsWalletConnect: Boolean
        get() = when (this) {
            is HardwareCard,
            is Mnemonic,
            is EvmPrivateKey -> true
            else -> false
        }

    val isWatchAccountType: Boolean
        get() = when (this) {
            is ZCashUfvKey,
            is EvmAddress,
            is SolanaAddress,
            is TronAddress,
            is TonAddress,
            is StellarAddress,
            is BitcoinAddress -> true

            is HdExtendedKey -> hdExtendedKey.isPublic
            else -> false
        }

    fun evmAddress(chain: Chain) = when (this) {
        is Mnemonic -> Signer.address(seed, chain)
        is EvmPrivateKey -> Signer.address(key)
        else -> null
    }

    fun sign(
        message: ByteArray,
        getChain: (BlockchainType) -> Chain,
        isLegacy: Boolean = false
    ): ByteArray? {
        val signer = when (this) {
            is Mnemonic -> {
                Signer.getInstance(seed, getChain(BlockchainType.Ethereum))
            }

            is EvmPrivateKey -> {
                Signer.getInstance(key, getChain(BlockchainType.Ethereum))
            }

            else -> null
        } ?: return null

        return if (isLegacy) {
            signer.signByteArrayLegacy(message)
        } else {
            signer.signByteArray(message)
        }
    }
}

val HDWallet.Purpose.derivation: Derivation
    get() = when (this) {
        HDWallet.Purpose.BIP44 -> Derivation.bip44
        HDWallet.Purpose.BIP49 -> Derivation.bip49
        HDWallet.Purpose.BIP84 -> Derivation.bip84
        HDWallet.Purpose.BIP86 -> Derivation.bip86
    }

val HDWallet.Purpose.tokenTypeDerivation: TokenType.Derivation
    get() = when (this) {
        HDWallet.Purpose.BIP44 -> TokenType.Derivation.Bip44
        HDWallet.Purpose.BIP49 -> TokenType.Derivation.Bip49
        HDWallet.Purpose.BIP84 -> TokenType.Derivation.Bip84
        HDWallet.Purpose.BIP86 -> TokenType.Derivation.Bip86
    }

@Parcelize
enum class AccountOrigin(val value: String) : Parcelable {
    Created("Created"),
    Restored("Restored");
}

fun String.normalizeNFKD(): String = Normalizer.normalize(this, Normalizer.Form.NFKD)
