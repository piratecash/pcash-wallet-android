package cash.p.terminal.core.managers

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoEnableTokenFilterSanitizationTest {

    /** No row here is expected to resolve — the catalog knows none of these contracts. */
    private val marketKit = mockk<MarketKitWrapper> {
        every { tokens(any<List<TokenQuery>>()) } returns emptyList()
    }

    @Test
    fun curatedEnabledWallets_unsupportedBlockchainRow_dropsIt() = runTest {
        val custom = TokenQuery(BlockchainType.Unsupported("evil-chain"), TokenType.Eip20("0xCUSTOM"))

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(StoredToken(custom.id, TrustedTokenMetadata("My Token", "MYT", 8), trustedDecimals = false)),
            approvedTokenQueryIds = setOf(custom.id),
        )

        assertTrue(result.enabled.isEmpty())
        assertTrue(result.declined.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_chainPairedWithImpossibleTokenType_dropsIt() = runTest {
        val impossible = TokenQuery(BlockchainType.Bitcoin, TokenType.Eip20("0xCUSTOM"))

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(StoredToken(impossible.id, TrustedTokenMetadata("My Token", "MYT", 8), trustedDecimals = false)),
            approvedTokenQueryIds = setOf(impossible.id),
        )

        assertTrue(result.enabled.isEmpty())
        assertTrue(result.declined.isEmpty())
    }

    // Both labels embed a bidi override char (U+202E) and a newline.
    @Test
    fun curatedEnabledWallets_labelsWithControlAndBidiChars_persistsNormalizedLabels() = runTest {
        val custom = TokenQuery.eip20(BlockchainType.Ethereum, "0xCUSTOM")
        val crafted = TrustedTokenMetadata("Bad‮Token\nX", "M‮Y\nT", 8)

        val wallet = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(StoredToken(custom.id, crafted, trustedDecimals = true)),
            approvedTokenQueryIds = setOf(custom.id),
        ).enabled.single()

        assertEquals("Bad Token X", wallet.coinName)
        assertEquals("M Y T", wallet.coinCode)
    }

    // 64 is the bound coinName/coinCode are truncated to.
    @Test
    fun curatedEnabledWallets_veryLongLabel_persistsBoundedValue() = runTest {
        val custom = TokenQuery.eip20(BlockchainType.Ethereum, "0xCUSTOM")
        val huge = TrustedTokenMetadata("N".repeat(5000), "C".repeat(5000), 8)

        val wallet = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(StoredToken(custom.id, huge, trustedDecimals = true)),
            approvedTokenQueryIds = setOf(custom.id),
        ).enabled.single()

        assertEquals(64, wallet.coinName?.length)
        assertEquals(64, wallet.coinCode?.length)
    }

    private companion object {
        const val ACCOUNT_ID = "acc"
    }
}
