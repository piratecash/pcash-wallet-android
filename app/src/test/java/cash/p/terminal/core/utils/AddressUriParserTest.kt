package cash.p.terminal.core.utils

import cash.p.terminal.entities.AddressUri
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AddressUriParserTest {
    @Test
    fun parse_rawAndPrefixedBitcoinAddresses_returnsAddressWithoutScheme() {
        val parser = parser(BlockchainType.Bitcoin)
        assertEquals("1address", parser.uri("1address").address)
        assertEquals("1address", parser.uri("bitcoin:1address").address)
    }
    @Test
    fun parse_bitcoinCashAddress_preservesScheme() {
        assertEquals("bitcoincash:qaddress", parser(BlockchainType.BitcoinCash).uri("bitcoincash:qaddress").address)
    }
    @Test
    fun parse_invalidScheme_returnsInvalidBlockchainType() {
        assertTrue(parser(BlockchainType.Bitcoin).parse("dash:Xaddress") is AddressUriResult.InvalidBlockchainType)
    }
    @Test
    fun parse_schemeWithSingleSlash_removesSlashFromAddress() {
        assertEquals("Xaddress", parser(BlockchainType.Dash).uri("dash:/Xaddress").address)
    }
    @Test
    fun parse_prefixlessAddressWithParameters_extractsKnownFields() {
        val parsed = parser(BlockchainType.Bitcoin).uri("1address?amount=1.25&message=Thank%20you&memo=invoice&tag=customer")
        assertEquals("1address", parsed.address)
        assertEquals(BigDecimal("1.25"), parsed.amount)
        assertEquals("Thank you", parsed.value<String>(AddressUri.Field.Message))
        assertEquals("invoice", parsed.value<String>(AddressUri.Field.Memo))
        assertEquals("customer", parsed.value<String>(AddressUri.Field.Tag))
    }
    @Test
    fun parse_prefixlessSolanaAndMoneroUris_extractsAmounts() {
        val solana = parser(BlockchainType.Solana).uri("recipient?amount=1.5")
        val monero = parser(BlockchainType.Monero).uri("4address?tx_amount=2.5")
        assertEquals("recipient", solana.address)
        assertEquals(BigDecimal("1.5"), solana.amount)
        assertEquals("4address", monero.address)
        assertEquals(BigDecimal("2.5"), monero.amount)
    }
    @Test fun parse_nativeTonTransfer_handlesNativeFieldsAndRejectsUnsupportedForms() {
        assertEquals("UQaddress", parser(BlockchainType.Ton).uri("ton://transfer/UQaddress").address)
        val parsed = parser(BlockchainType.Ton).uri(
            "ton://transfer/UQaddress?amount=1500000000&text=Thanks%20%F0%9F%99%82&exp=123"
        )
        assertEquals(BigDecimal("1.5"), parsed.amount)
        assertEquals("Thanks 🙂", parsed.value<String>(AddressUri.Field.Memo))
        assertEquals("123", parsed.unhandledParameters["exp"])
        val decimal = parser(BlockchainType.Ton).uri("ton://transfer/UQaddress?amount=1.5")
        val negative = parser(BlockchainType.Ton).uri("ton://transfer/UQaddress?amount=-1")
        assertEquals(null, decimal.amount)
        assertEquals(null, negative.amount)
        val jetton = parser(BlockchainType.Ton).uri("ton://transfer/UQaddress?jetton=EQtoken&amount=1500000")
        assertEquals(null, jetton.amount)
        assertEquals("1500000", jetton.unhandledParameters["amount"])
        val result = AddressUriParser(BlockchainType.Ton, TokenType.Jetton("EQtoken"))
            .parse("ton://transfer/UQaddress?amount=1500000")
        assertTrue(result is AddressUriResult.InvalidTokenType)
        assertTrue(AddressUriParser(BlockchainType.Ton, TokenType.Jetton("EQtoken")).parse("UQaddress") is AddressUriResult.Uri)
        assertTrue(parser(BlockchainType.Ton).parse("ton://connect/UQaddress") is AddressUriResult.WrongUri)
    }
    @Test fun parse_nonTonUriWithHttpsOptionalParameter_remainsSupported() {
        val parsed = parser(BlockchainType.Dash).uri("dash:Xaddress?callback=https://example.com//pay&amount=1")
        assertEquals(BigDecimal.ONE to "https://example.com//pay", parsed.amount to parsed.unhandledParameters["callback"])
    }
    @Test
    fun parse_bitrefillDashUriWithLeadingAmpersand_retainsIsParameter() {
        val parsed = parser(BlockchainType.Dash).uri("dash:XvmJPjJ8pjKn51YrBnZjqkXqprQ3cmW56U&IS=1")
        assertEquals("XvmJPjJ8pjKn51YrBnZjqkXqprQ3cmW56U", parsed.address)
        assertEquals("1", parsed.value<String>(AddressUri.Field.IS))
    }
    @Test
    fun parse_uppercaseBip321Uri_matchesSchemeAndKnownParametersCaseInsensitively() {
        val parsed = parser(BlockchainType.Bitcoin).uri("BITCOIN:1address?AMOUNT=1.25&LABEL=Donation&MESSAGE=Thanks")
        assertEquals("bitcoin", parsed.scheme)
        assertEquals(BigDecimal("1.25"), parsed.amount)
        assertEquals("Donation", parsed.value<String>(AddressUri.Field.Label))
        assertEquals("Thanks", parsed.value<String>(AddressUri.Field.Message))
    }
    @Test
    fun parse_unsupportedBitcoinAndZcashRequiredParameters_returnsWrongUri() {
        assertTrue(parser(BlockchainType.Bitcoin).parse("bitcoin:1address?req-feature=value") is AddressUriResult.WrongUri)
        assertTrue(parser(BlockchainType.Zcash).parse("zcash:zaddress?REQ-feature=value") is AddressUriResult.WrongUri)
    }
    @Test
    fun parse_amountAndValue_usesAmountPrecedence() {
        val parsed = parser(BlockchainType.Ethereum).uri("ethereum:0xaddress?value=2&amount=1")
        assertEquals(BigDecimal.ONE, parsed.amount)
        val tokenResult = AddressUriParser(BlockchainType.Ethereum, TokenType.Eip20("0xtoken"))
            .parse("ethereum:0xaddress?value=2")
        assertTrue(tokenResult is AddressUriResult.InvalidTokenType)
    }
    @Test
    fun parse_singlePaymentZip321Uri_extractsAmountAndMetadata() {
        val parsed = parser(BlockchainType.Zcash).uri("zcash:zaddress?amount=1.25&memo=Thanks&message=Invoice")
        assertEquals("zaddress", parsed.address)
        assertEquals(BigDecimal("1.25"), parsed.amount)
        assertEquals("Thanks", parsed.value<String>(AddressUri.Field.Memo))
        assertEquals("Invoice", parsed.value<String>(AddressUri.Field.Message))
    }
    @Test
    fun parse_indexedZip321Payment_returnsWrongUri() {
        val result = parser(BlockchainType.Zcash).parse("zcash:zaddress?amount=1&address.1=zother&amount.1=2")
        assertTrue(result is AddressUriResult.WrongUri)
    }
    @Test
    fun parse_nativeSolanaPayUri_extractsAmountAndMetadata() {
        val parsed = parser(BlockchainType.Solana).uri("SOLANA:recipient?amount=1.5&label=Store&message=Order&memo=123")
        assertEquals("recipient", parsed.address)
        assertEquals(BigDecimal("1.5"), parsed.amount)
        assertEquals("Store", parsed.value<String>(AddressUri.Field.Label))
        assertEquals("Order", parsed.value<String>(AddressUri.Field.Message))
        assertEquals("123", parsed.value<String>(AddressUri.Field.Memo))
    }
    @Test
    fun parse_solanaPaySplTokenAndNonNativeContexts_doNotApplyAmount() {
        val splTokenRequest = parser(BlockchainType.Solana).parse("solana:recipient?amount=1&spl-token=mint")
        val splContextRequest = AddressUriParser(BlockchainType.Solana, TokenType.Spl("mint")).parse("solana:recipient?amount=1")
        assertTrue(splTokenRequest is AddressUriResult.WrongUri)
        assertTrue(splContextRequest is AddressUriResult.InvalidTokenType)
    }
    @Test
    fun parse_moneroTxAmount_extractsAmount() {
        val parsed = parser(BlockchainType.Monero).uri("monero:4address?tx_amount=2.5&recipient_name=Alice")
        assertEquals("4address", parsed.address)
        assertEquals(BigDecimal("2.5"), parsed.amount)
        assertEquals("Alice", parsed.unhandledParameters["recipient_name"])
    }
    @Test
    fun parse_nonMoneroTxAmount_retainsButDoesNotApplyAmount() {
        val parsed = parser(BlockchainType.Bitcoin).uri("bitcoin:1address?tx_amount=2.5")
        assertEquals("2.5", parsed.value<String>(AddressUri.Field.TxAmount))
        assertEquals(null, parsed.amount)
    }
    @Test
    fun parse_crossContextAndMalformedProtocolParameters_rejectsUnsafeRequests() {
        assertTrue(parser(BlockchainType.Solana).parse("monero:recipient?tx_amount=2.5") is AddressUriResult.InvalidBlockchainType)
        assertTrue(parser(BlockchainType.Solana).parse("bitcoin:recipient?amount=1") is AddressUriResult.InvalidBlockchainType)
        assertTrue(parser(BlockchainType.Bitcoin).parse("bitcoin:1address?amount=1&req-feature=%zz") is AddressUriResult.WrongUri)
        assertTrue(parser(BlockchainType.Zcash).parse("zcash:zaddress?amount=1&address.1=%zz") is AddressUriResult.WrongUri)
        assertTrue(parser(BlockchainType.Zcash).parse("zcash:zaddress?amount=1&address.2147483648=other") is AddressUriResult.WrongUri)
        assertTrue(parser(BlockchainType.Solana).parse("solana:recipient?amount=1&spl-token=%zz") is AddressUriResult.WrongUri)
    }
    @Test
    fun parse_unknownAndMalformedParameters_keepsValidParameters() {
        val parsed = parser(BlockchainType.Bitcoin).uri("bitcoin:1address?unknown=one%3Dtwo&broken&amount=3&bad=%zz&label=valid")
        assertEquals("one=two", parsed.unhandledParameters["unknown"])
        assertEquals(BigDecimal("3"), parsed.amount)
        assertEquals("valid", parsed.value<String>(AddressUri.Field.Label))
    }
    @Test
    fun parse_parameterWithPlus_retainsLiteralPlus() {
        val parsed = parser(BlockchainType.Bitcoin).uri("bitcoin:1address?message=one+two")
        assertEquals("one+two", parsed.value<String>(AddressUri.Field.Message))
    }
    @Test
    fun parse_blockchainAndTokenUidMismatch_returnsCorrespondingError() {
        val parser = parser(BlockchainType.Bitcoin)
        assertTrue(parser.parse("bitcoin:1address?blockchain_uid=dash") is AddressUriResult.InvalidBlockchainType)
        assertTrue(parser.parse("bitcoin:1address?token_uid=eip20:0xabc") is AddressUriResult.InvalidTokenType)
    }

    @Test
    fun uri_knownAndUnknownParameters_generatesCompatibleUri() {
        val addressUri = AddressUri("bitcoin").apply {
            address = "1address"
            parameters[AddressUri.Field.Amount] = "1"
            unhandledParameters["custom"] = "value"
        }

        assertEquals("bitcoin:1address?amount=1&custom=value", parser(BlockchainType.Bitcoin).uri(addressUri))
    }

    @Test
    fun parse_malformedAddress_returnsWrongUri() {
        assertTrue(parser(BlockchainType.Bitcoin).parse("bitcoin:%zz") is AddressUriResult.WrongUri)
    }

    private fun parser(type: BlockchainType) = AddressUriParser(type, TokenType.Native)

    private fun AddressUriParser.uri(value: String): AddressUri {
        return (parse(value) as AddressUriResult.Uri).addressUri
    }
}
