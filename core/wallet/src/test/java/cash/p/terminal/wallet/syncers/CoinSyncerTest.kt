package cash.p.terminal.wallet.syncers

import cash.p.terminal.wallet.models.BlockchainEntity
import cash.p.terminal.wallet.models.TokenEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoinSyncerTest {

    @Test
    fun filterValidTokens_validBlockchainUid_retainsToken() {
        val blockchains = listOf(
            BlockchainEntity(uid = "ethereum", name = "Ethereum", eip3091url = null),
            BlockchainEntity(uid = "bitcoin", name = "Bitcoin", eip3091url = null)
        )
        val tokens = listOf(
            createToken(coinUid = "eth", blockchainUid = "ethereum"),
            createToken(coinUid = "btc", blockchainUid = "bitcoin")
        )

        val result = CoinSyncer.filterValidTokens(tokens, blockchains)

        assertEquals(2, result.size)
        assertEquals("eth", result[0].coinUid)
        assertEquals("btc", result[1].coinUid)
    }

    @Test
    fun filterValidTokens_invalidBlockchainUid_filtersOutToken() {
        val blockchains = listOf(
            BlockchainEntity(uid = "ethereum", name = "Ethereum", eip3091url = null)
        )
        val tokens = listOf(
            createToken(coinUid = "eth", blockchainUid = "ethereum"),
            createToken(coinUid = "canton-token", blockchainUid = "canton-network")
        )

        val result = CoinSyncer.filterValidTokens(tokens, blockchains)

        assertEquals(1, result.size)
        assertEquals("eth", result[0].coinUid)
    }

    @Test
    fun filterValidTokens_emptyBlockchainEntities_filtersOutAllTokens() {
        val blockchains = emptyList<BlockchainEntity>()
        val tokens = listOf(
            createToken(coinUid = "eth", blockchainUid = "ethereum"),
            createToken(coinUid = "btc", blockchainUid = "bitcoin")
        )

        val result = CoinSyncer.filterValidTokens(tokens, blockchains)

        assertTrue(result.isEmpty())
    }

    @Test
    fun filterValidTokens_emptyTokens_returnsEmptyList() {
        val blockchains = listOf(
            BlockchainEntity(uid = "ethereum", name = "Ethereum", eip3091url = null)
        )
        val tokens = emptyList<TokenEntity>()

        val result = CoinSyncer.filterValidTokens(tokens, blockchains)

        assertTrue(result.isEmpty())
    }

    @Test
    fun filterValidTokens_mixedValidAndInvalidTokens_retainsOnlyValid() {
        val blockchains = listOf(
            BlockchainEntity(uid = "ethereum", name = "Ethereum", eip3091url = null),
            BlockchainEntity(uid = "binance-smart-chain", name = "BSC", eip3091url = null)
        )
        val tokens = listOf(
            createToken(coinUid = "eth", blockchainUid = "ethereum"),
            createToken(coinUid = "orphan1", blockchainUid = "canton-network"),
            createToken(coinUid = "bnb", blockchainUid = "binance-smart-chain"),
            createToken(coinUid = "orphan2", blockchainUid = "unknown-chain")
        )

        val result = CoinSyncer.filterValidTokens(tokens, blockchains)

        assertEquals(2, result.size)
        assertEquals("eth", result[0].coinUid)
        assertEquals("bnb", result[1].coinUid)
    }

    private fun createToken(
        coinUid: String,
        blockchainUid: String,
        type: String = "native",
        decimals: Int = 18,
        reference: String = ""
    ) = TokenEntity(
        coinUid = coinUid,
        blockchainUid = blockchainUid,
        type = type,
        decimals = decimals,
        reference = reference
    )
}
