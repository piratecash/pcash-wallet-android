package cash.p.terminal.wallet.entities

import cash.p.terminal.wallet.protocolType
import io.horizontalsystems.core.entities.BlockchainType
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenTypeTest {

    @Test
    fun id_mweb_returnsMwebId() {
        assertEquals("mweb", TokenType.Mweb.id)
    }

    @Test
    fun values_mweb_returnsMwebTypeAndEmptyReference() {
        assertEquals(TokenType.Value(type = "mweb", reference = ""), TokenType.Mweb.values)
    }

    @Test
    fun fromType_mweb_returnsMwebTokenType() {
        assertEquals(TokenType.Mweb, TokenType.fromType("mweb"))
    }

    @Test
    fun fromId_mweb_returnsMwebTokenType() {
        assertEquals(TokenType.Mweb, TokenType.fromId("mweb"))
    }

    @Test
    fun fromType_stellarReference_returnsAssetTokenType() {
        assertEquals(
            TokenType.Asset(code = "USDC", issuer = "GDQOE23QKUSLOXFW77F5GBDSYFND5XPF7FJHJ42SRD7Y7RP2T7BDXYCP"),
            TokenType.fromType("stellar", "USDC-GDQOE23QKUSLOXFW77F5GBDSYFND5XPF7FJHJ42SRD7Y7RP2T7BDXYCP")
        )
    }

    @Test
    fun fromId_stellarAsset_returnsAssetTokenType() {
        val tokenType = TokenType.Asset(
            code = "USDC",
            issuer = "GDQOE23QKUSLOXFW77F5GBDSYFND5XPF7FJHJ42SRD7Y7RP2T7BDXYCP"
        )

        assertEquals(tokenType, TokenType.fromId(tokenType.id))
    }

    @Test
    fun fromType_trc10Reference_returnsStableTrc10Id() {
        val tokenType = TokenType.fromType("trc10", "1005114")

        assertEquals("trc10:1005114", tokenType.id)
        assertEquals(TokenType.Value(type = "trc10", reference = "1005114"), tokenType.values)
    }

    @Test
    fun fromId_trc10_returnsTrc10TokenType() {
        assertEquals(TokenType.Trc10("1005114"), TokenType.fromId("trc10:1005114"))
    }

    @Test
    fun fromType_trc10WithoutReference_returnsUnsupportedTokenType() {
        assertEquals(
            TokenType.Unsupported(type = "trc10", reference = ""),
            TokenType.fromType("trc10"),
        )
    }

    @Test
    fun trc10_assetId_returnsTronTokenQuery() {
        assertEquals(
            TokenQuery(BlockchainType.Tron, TokenType.Trc10("1005114")),
            TokenQuery.trc10("1005114"),
        )
    }

    @Test
    fun fromId_trc10TokenQueryId_returnsTrc10TokenQuery() {
        val query = TokenQuery.trc10("1005114")

        assertEquals(query, TokenQuery.fromId(query.id))
    }

    @Test
    fun protocolType_trc10_returnsTrc10Label() {
        assertEquals("TRC10", TokenQuery.trc10("1005114").protocolType)
    }
}
