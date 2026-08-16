package cash.p.terminal.trezor.client

import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.TrezorInputScriptType
import cash.p.terminal.trezorkit.client.TrezorKeyResult
import cash.p.terminal.trezorkit.client.TrezorPublicKeyRequest
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrezorPublicKeySpecsTest {

    private fun derived(blockchainType: BlockchainType, derivation: TokenType.Derivation) =
        TokenQuery(blockchainType, TokenType.Derived(derivation))

    private fun native(blockchainType: BlockchainType) =
        TokenQuery(blockchainType, TokenType.Native)

    private fun bch(addressType: TokenType.AddressType) =
        TokenQuery(BlockchainType.BitcoinCash, TokenType.AddressTyped(addressType))

    private fun specFor(query: TokenQuery) =
        TrezorPublicKeySpecs.buildQuerySpecs(listOf(query)).singleOrNull()

    private fun result(key: String) =
        TrezorKeyResult(key = key, publicKey = byteArrayOf(9), chainCode = byteArrayOf(8))

    // Table-driven mapping is the primary guarantee that BIP44 and BIP86 never get confused
    // (both serialize as `xpub`, so the post-fetch version check cannot tell them apart).
    @Test
    fun buildQuerySpecs_bitcoinAllDerivations_mapPathAndScriptType() {
        val cases = listOf(
            Triple(TokenType.Derivation.Bip44, "m/44'/0'/0'", TrezorInputScriptType.SPENDADDRESS),
            Triple(
                TokenType.Derivation.Bip49,
                "m/49'/0'/0'",
                TrezorInputScriptType.SPENDP2SHWITNESS
            ),
            Triple(TokenType.Derivation.Bip84, "m/84'/0'/0'", TrezorInputScriptType.SPENDWITNESS),
            Triple(TokenType.Derivation.Bip86, "m/86'/0'/0'", TrezorInputScriptType.SPENDTAPROOT),
        )
        cases.forEach { (derivation, path, scriptType) ->
            val spec = specFor(derived(BlockchainType.Bitcoin, derivation))!!
            assertEquals(path, spec.derivationPath)
            assertEquals(
                TrezorPublicKeyRequest.Bitcoin(
                    "Bitcoin",
                    TrezorDerivationPath.parse(path),
                    scriptType
                ),
                spec.request
            )
        }
    }

    @Test
    fun walletIdentityRequest_matchesDefaultBitcoinBip84Key() {
        assertEquals(
            TrezorPublicKeyRequest.Bitcoin(
                "Bitcoin",
                TrezorDerivationPath.parse("m/84'/0'/0'"),
                TrezorInputScriptType.SPENDWITNESS,
            ),
            TrezorPublicKeySpecs.walletIdentityRequest,
        )
        assertEquals(
            TokenQuery(
                BlockchainType.Bitcoin,
                TokenType.Derived(TokenType.Derivation.Bip84),
            ),
            TrezorPublicKeySpecs.walletIdentityTokenQuery,
        )
    }

    @Test
    fun buildQuerySpecs_litecoin_supports44_49_84_butNotBip86OrMweb() {
        assertEquals(
            "m/44'/2'/0'",
            specFor(derived(BlockchainType.Litecoin, TokenType.Derivation.Bip44))!!.derivationPath
        )
        assertEquals(
            "m/49'/2'/0'",
            specFor(derived(BlockchainType.Litecoin, TokenType.Derivation.Bip49))!!.derivationPath
        )
        assertEquals(
            "m/84'/2'/0'",
            specFor(derived(BlockchainType.Litecoin, TokenType.Derivation.Bip84))!!.derivationPath
        )
        assertNull(specFor(derived(BlockchainType.Litecoin, TokenType.Derivation.Bip86)))
        assertNull(specFor(TokenQuery(BlockchainType.Litecoin, TokenType.Mweb)))
    }

    @Test
    fun buildQuerySpecs_bitcoinCash_type0AndType145_differentCoinType() {
        assertEquals("m/44'/0'/0'", specFor(bch(TokenType.AddressType.Type0))!!.derivationPath)
        assertEquals("m/44'/145'/0'", specFor(bch(TokenType.AddressType.Type145))!!.derivationPath)
        assertEquals(
            TrezorPublicKeyRequest.Bitcoin(
                "Bcash",
                TrezorDerivationPath.parse("m/44'/145'/0'"),
                TrezorInputScriptType.SPENDADDRESS
            ),
            specFor(bch(TokenType.AddressType.Type145))!!.request
        )
    }

    @Test
    fun buildQuerySpecs_dogeAndDash_legacyNativePath() {
        assertEquals("m/44'/3'/0'", specFor(native(BlockchainType.Dogecoin))!!.derivationPath)
        assertEquals("m/44'/5'/0'", specFor(native(BlockchainType.Dash))!!.derivationPath)
    }

    @Test
    fun buildQuerySpecs_evmChains_shareOneEthereumRequest_tokenAgnostic() {
        val specs = TrezorPublicKeySpecs.buildQuerySpecs(
            listOf(
                native(BlockchainType.Ethereum),
                TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Eip20("0xabc")),
                native(BlockchainType.Base),
            )
        )
        assertEquals(3, specs.size)
        assertEquals(1, specs.map { it.request }.distinct().size)
        assertEquals(
            TrezorPublicKeyRequest.Ethereum(TrezorDerivationPath.parse("m/44'/60'/0'/0/0")),
            specs.first().request
        )
    }

    @Test
    fun buildQuerySpecs_solanaAndStellar_dedicatedRequests() {
        assertEquals(
            TrezorPublicKeyRequest.Solana(TrezorDerivationPath.parse("m/44'/501'/0'/0'")),
            specFor(native(BlockchainType.Solana))!!.request
        )
        assertEquals(
            TrezorPublicKeyRequest.Stellar(TrezorDerivationPath.parse("m/44'/148'/0'")),
            specFor(native(BlockchainType.Stellar))!!.request
        )
    }

    @Test
    fun buildQuerySpecs_tron_oneFixedPathRequest_tokenAgnostic() {
        val specs = TrezorPublicKeySpecs.buildQuerySpecs(
            listOf(
                native(BlockchainType.Tron),
                TokenQuery(
                    BlockchainType.Tron,
                    TokenType.Eip20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")
                ),
            )
        )
        assertEquals(2, specs.size)
        assertEquals(1, specs.map { it.request }.distinct().size)
        assertEquals(
            TrezorPublicKeyRequest.Tron(TrezorDerivationPath.parse("m/44'/195'/0'/0/0")),
            specs.first().request
        )
    }

    @Test
    fun buildQuerySpecs_unsupported_isSkipped() {
        assertNull(specFor(native(BlockchainType.Ton)))
        // BTC requires an explicit derivation - a Native query is not derivable.
        assertNull(specFor(native(BlockchainType.Bitcoin)))
    }

    @Test
    fun supports_reflectsRequestForAvailability() {
        assertTrue(
            TrezorPublicKeySpecs.supports(
                null,
                BlockchainType.Bitcoin,
                TokenType.Derived(TokenType.Derivation.Bip44)
            )
        )
        assertTrue(
            TrezorPublicKeySpecs.supports(
                null,
                BlockchainType.Bitcoin,
                TokenType.Derived(TokenType.Derivation.Bip84)
            )
        )
        assertFalse(
            TrezorPublicKeySpecs.supports(
                null,
                BlockchainType.Litecoin,
                TokenType.Derived(TokenType.Derivation.Bip86)
            )
        )
        assertFalse(TrezorPublicKeySpecs.supports(null, BlockchainType.Litecoin, TokenType.Mweb))
        assertFalse(TrezorPublicKeySpecs.supports(null, BlockchainType.Ton, TokenType.Native))
        // Tron is model-gated: available on Safe models (native and TRC-20 alike), absent on One.
        assertTrue(
            TrezorPublicKeySpecs.supports(
                TrezorModel.Safe5,
                BlockchainType.Tron,
                TokenType.Native
            )
        )
        assertTrue(
            TrezorPublicKeySpecs.supports(
                TrezorModel.Safe5,
                BlockchainType.Tron,
                TokenType.Eip20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")
            )
        )
        assertFalse(
            TrezorPublicKeySpecs.supports(
                TrezorModel.One,
                BlockchainType.Tron,
                TokenType.Native
            )
        )
    }

    @Test
    fun toHardwarePublicKey_addressResult_copiesFields() {
        val spec = specFor(native(BlockchainType.Solana))!!
        val hw = TrezorPublicKeySpecs.toHardwarePublicKey(
            spec,
            TrezorKeyResult(
                key = "SolAddr",
                publicKey = byteArrayOf(1, 2, 3),
                chainCode = ByteArray(0)
            ),
            accountId = "acc-1"
        )
        assertEquals("acc-1", hw.accountId)
        assertEquals(BlockchainType.Solana.uid, hw.blockchainType)
        assertEquals(HardwarePublicKeyType.PUBLIC_KEY, hw.type)
        assertEquals("SolAddr", hw.key.value)
        assertEquals("m/44'/501'/0'/0'", hw.derivationPath)
        assertArrayEquals(byteArrayOf(1, 2, 3), hw.publicKey)
    }

    @Test
    fun toHardwarePublicKey_bip84WithZpub_succeeds() {
        val spec = specFor(derived(BlockchainType.Bitcoin, TokenType.Derivation.Bip84))!!
        val zpub = HDExtendedKey(SEED, HDWallet.Purpose.BIP84).serializePublic()
        assertEquals(
            zpub,
            TrezorPublicKeySpecs.toHardwarePublicKey(spec, result(zpub), "acc-1").key.value
        )
    }

    @Test(expected = TrezorKeyValidationException::class)
    fun toHardwarePublicKey_bip84WithXpub_throwsMismatch() {
        val spec = specFor(derived(BlockchainType.Bitcoin, TokenType.Derivation.Bip84))!!
        val xpub = HDExtendedKey(SEED, HDWallet.Purpose.BIP44).serializePublic()
        TrezorPublicKeySpecs.toHardwarePublicKey(spec, result(xpub), "acc-1")
    }

    @Test
    fun toHardwarePublicKey_bip44WithXpub_succeeds() {
        val spec = specFor(derived(BlockchainType.Bitcoin, TokenType.Derivation.Bip44))!!
        val xpub = HDExtendedKey(SEED, HDWallet.Purpose.BIP44).serializePublic()
        assertEquals(
            xpub,
            TrezorPublicKeySpecs.toHardwarePublicKey(spec, result(xpub), "acc-1").key.value
        )
    }

    @Test(expected = TrezorKeyValidationException::class)
    fun toHardwarePublicKey_unparseableKey_throws() {
        val spec = specFor(derived(BlockchainType.Bitcoin, TokenType.Derivation.Bip84))!!
        TrezorPublicKeySpecs.toHardwarePublicKey(spec, result("not-a-key"), "acc-1")
    }

    companion object {
        private val SEED = ByteArray(32) { (it + 1).toByte() }
    }
}
