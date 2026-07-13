package cash.p.terminal.trezor.client

import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.TrezorInputScriptType
import cash.p.terminal.trezorkit.client.TrezorKeyResult
import cash.p.terminal.trezorkit.client.TrezorPublicKeyRequest
import cash.p.terminal.wallet.accountTypeDerivation
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.dogecoinkit.MainNetDogecoin
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.bitcoinkit.MainNet
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.dashkit.MainNetDash
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.litecoinkit.MainNetLitecoin

/**
 * Maps app [TokenQuery]s to the neutral [TrezorPublicKeyRequest]s the kit understands and turns the
 * kit's [TrezorKeyResult]s back into [HardwarePublicKey]s. The single place where a
 * `(blockchainType, tokenType)` pair becomes a concrete BIP-32 path + Trezor script type, so
 * create-wallet, scan-to-add, and UI eligibility all agree on what Trezor can derive.
 */
object TrezorPublicKeySpecs {

    /** One [query] paired with its request, the derivation path to store, and (for BTC-like xpubs)
     *  the purpose the returned key must match. */
    data class QuerySpec(
        val query: TokenQuery,
        val request: TrezorPublicKeyRequest,
        val derivationPath: String,
        val expectedPurpose: HDWallet.Purpose?,
    )

    /** Builds a [QuerySpec] per supported query, skipping pairs Trezor cannot derive here. */
    fun buildQuerySpecs(tokenQueries: List<TokenQuery>): List<QuerySpec> =
        tokenQueries.mapNotNull { specFor(it) }

    /** Single source of truth for UI eligibility: can Trezor derive this token variant at all. */
    fun supports(model: TrezorModel?, blockchainType: BlockchainType, tokenType: TokenType): Boolean =
        TrezorModelSupport.isSupported(model, blockchainType) &&
            specFor(TokenQuery(blockchainType, tokenType)) != null

    fun toHardwarePublicKey(
        spec: QuerySpec,
        result: TrezorKeyResult,
        accountId: String,
    ): HardwarePublicKey {
        spec.expectedPurpose?.let { validateXpubPurpose(result.key, it) }
        return HardwarePublicKey(
            accountId = accountId,
            blockchainType = spec.query.blockchainType.uid,
            type = HardwarePublicKeyType.PUBLIC_KEY,
            tokenType = spec.query.tokenType,
            key = SecretString(result.key),
            derivationPath = spec.derivationPath,
            publicKey = result.publicKey,
            derivedPublicKey = result.publicKey,
        )
    }

    private fun specFor(query: TokenQuery): QuerySpec? = when (query.blockchainType) {
        BlockchainType.Bitcoin -> bitcoinDerived(query, "Bitcoin", bitcoinCoinType, BITCOIN_PURPOSES)
        BlockchainType.Litecoin -> bitcoinDerived(query, "Litecoin", litecoinCoinType, LITECOIN_PURPOSES)
        BlockchainType.BitcoinCash -> bitcoinCash(query)
        BlockchainType.Dogecoin -> bitcoinNative(query, "Dogecoin", dogecoinCoinType)
        BlockchainType.Dash -> bitcoinNative(query, "Dash", dashCoinType)
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.ArbitrumOne,
        BlockchainType.Optimism,
        BlockchainType.Base -> fixedPath(query, TrezorPublicKeyRequest::Ethereum, "m/44'/60'/0'/0/0")
        BlockchainType.Solana -> fixedPath(query, TrezorPublicKeyRequest::Solana, "m/44'/501'/0'/0'")
        BlockchainType.Stellar -> fixedPath(query, TrezorPublicKeyRequest::Stellar, "m/44'/148'/0'")
        else -> null
    }

    /** BTC/LTC — the derivation is chosen by the user via `TokenType.Derived`. */
    private fun bitcoinDerived(
        query: TokenQuery,
        coinName: String,
        coinType: Int,
        allowed: Set<HDWallet.Purpose>,
    ): QuerySpec? {
        val purpose = (query.tokenType as? TokenType.Derived)?.derivation?.accountTypeDerivation?.purpose
            ?: return null
        if (purpose !in allowed) return null
        return bitcoinSpec(query, coinName, coinType, purpose)
    }

    /** BCH — no derivation choice, but two address types map to different coin types. */
    private fun bitcoinCash(query: TokenQuery): QuerySpec? {
        val coinType = when ((query.tokenType as? TokenType.AddressTyped)?.type) {
            TokenType.AddressType.Type0 -> MainNetBitcoinCash.CoinType.Type0.value
            TokenType.AddressType.Type145 -> MainNetBitcoinCash.CoinType.Type145.value
            null -> return null
        }
        // "Bcash" is Trezor's upstream coin_name for Bitcoin Cash (matches the BCH signer).
        return bitcoinSpec(query, "Bcash", coinType, HDWallet.Purpose.BIP44)
    }

    /** DOGE/DASH — single legacy (BIP44) derivation, carried by `TokenType.Native`. */
    private fun bitcoinNative(query: TokenQuery, coinName: String, coinType: Int): QuerySpec? {
        if (query.tokenType != TokenType.Native) return null
        return bitcoinSpec(query, coinName, coinType, HDWallet.Purpose.BIP44)
    }

    private fun bitcoinSpec(
        query: TokenQuery,
        coinName: String,
        coinType: Int,
        purpose: HDWallet.Purpose,
    ): QuerySpec {
        val path = "m/${purpose.value}'/$coinType'/0'"
        val request = TrezorPublicKeyRequest.Bitcoin(coinName, TrezorDerivationPath.parse(path), purpose.scriptType)
        return QuerySpec(query, request, path, purpose)
    }

    /** EVM/Solana/Stellar — one fixed path per chain, independent of the token variant. */
    private fun fixedPath(
        query: TokenQuery,
        request: (List<Int>) -> TrezorPublicKeyRequest,
        path: String,
    ): QuerySpec = QuerySpec(query, request(TrezorDerivationPath.parse(path)), path, null)

    /**
     * Fail-loud: the version of the returned xpub must be compatible with the requested purpose.
     * Distinguishes every device-level mismatch except BIP44 vs BIP86 (both serialize as `xpub`),
     * which is instead guaranteed by the deterministic purpose→path/scriptType mapping (unit-tested).
     */
    private fun validateXpubPurpose(xpub: String, expected: HDWallet.Purpose) {
        val purposes = try {
            HDExtendedKey(xpub).purposes
        } catch (e: HDExtendedKey.ParsingError) {
            throw TrezorKeyValidationException("Trezor returned an unparseable extended public key", e)
        } catch (e: RuntimeException) {
            throw TrezorKeyValidationException("Trezor returned an unparseable extended public key", e)
        }
        if (expected !in purposes) {
            throw TrezorKeyValidationException(
                "Trezor key purpose mismatch: expected $expected but the key permits $purposes"
            )
        }
    }

    private val HDWallet.Purpose.scriptType: TrezorInputScriptType
        get() = when (this) {
            HDWallet.Purpose.BIP44 -> TrezorInputScriptType.SPENDADDRESS
            HDWallet.Purpose.BIP49 -> TrezorInputScriptType.SPENDP2SHWITNESS
            HDWallet.Purpose.BIP84 -> TrezorInputScriptType.SPENDWITNESS
            HDWallet.Purpose.BIP86 -> TrezorInputScriptType.SPENDTAPROOT
        }

    private val bitcoinCoinType = MainNet().coinType
    private val litecoinCoinType = MainNetLitecoin().coinType
    private val dogecoinCoinType = MainNetDogecoin().coinType
    private val dashCoinType = MainNetDash().coinType

    private val BITCOIN_PURPOSES = setOf(
        HDWallet.Purpose.BIP44,
        HDWallet.Purpose.BIP49,
        HDWallet.Purpose.BIP84,
        HDWallet.Purpose.BIP86,
    )
    private val LITECOIN_PURPOSES = setOf(
        HDWallet.Purpose.BIP44,
        HDWallet.Purpose.BIP49,
        HDWallet.Purpose.BIP84,
    )
}
