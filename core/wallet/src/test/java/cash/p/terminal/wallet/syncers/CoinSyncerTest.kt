package cash.p.terminal.wallet.syncers

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.models.BlockchainEntity
import cash.p.terminal.wallet.models.BlockchainResponse
import cash.p.terminal.wallet.models.CoinResponse
import cash.p.terminal.wallet.models.TokenEntity
import cash.p.terminal.wallet.models.TokenResponse
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoinSyncerTest {

    private val virtualCoinMapper = VirtualCoinMapper()

    private fun createCoin(uid: String, code: String) = Coin(
        uid = uid,
        name = code,
        code = code,
        marketCapRank = null,
        coinGeckoId = null,
        image = null,
        priority = 0
    )

    @Test
    fun mapFetched_duplicatePrimaryKeyTokenRows_keepsLastRow() {
        val coins = listOf(CoinResponse("dogwifcoin", "dogwifhat", "wif", null, null, null, null))
        val blockchains = listOf(BlockchainResponse("solana", "Solana", null))
        val duplicateToken = TokenResponse("dogwifcoin", "solana", "spl", null, "EKpQ", null)

        val result = CoinResponseMapper.mapFetched(
            coins,
            blockchains,
            listOf(duplicateToken, duplicateToken.copy(decimals = 6)),
            virtualCoinMapper
        )

        assertEquals(1, result.tokens.size)
        assertEquals(6, result.tokens.single().decimals)
    }

    @Test
    fun tokenPipeline_duplicatePrimaryKeyRows_preservesDuplicates() {
        val duplicateToken = TokenResponse("dogwifcoin", "solana", "spl", null, "EKpQ", null)

        val result = CoinResponseMapper.tokenPipeline(
            listOf(createCoin("dogwifcoin", "WIF")),
            listOf(BlockchainEntity(uid = "solana", name = "Solana", eip3091url = null)),
            listOf(duplicateToken, duplicateToken.copy(decimals = 6)),
            virtualCoinMapper
        )

        // Pre-dedup view relied on by the generator's collision-provenance guard.
        assertEquals(2, result.size)
    }

    @Test
    fun mapFetched_duplicateNativeRowsOnTransformedChain_matchesLiveDatabaseState() {
        val coins = listOf(CoinResponse("litecoin", "Litecoin", "ltc", null, null, null, null))
        val blockchains = listOf(BlockchainResponse("litecoin", "Litecoin", null))
        val nativeToken = TokenResponse("litecoin", "litecoin", "native", null, null, null)

        val result = CoinResponseMapper.mapFetched(
            coins,
            blockchains,
            listOf(nativeToken, nativeToken.copy(decimals = 8)),
            virtualCoinMapper
        )

        // Live behavior: transform consumes the first native row (null decimals) into the
        // derived rows; the second native row survives with its own decimals.
        val derived = result.tokens.filter { it.type == "derived" }
        assertEquals(listOf("Bip44", "Bip49", "Bip84", "Bip86"), derived.map { it.reference })
        assertTrue(derived.all { it.decimals == null })
        assertEquals(8, result.tokens.single { it.type == "native" }.decimals)
        assertEquals(5, result.tokens.size)
    }

    // region injectVirtualTokens tests

    @Test
    fun injectVirtualTokens_bscUsdWithTether_addsVirtualUsdtToken() {
        val coins = listOf(
            createCoin("tether", "USDT"),
            createCoin("bsc-usd", "BSC-USD")
        )
        val tokens = listOf(
            createToken("bsc-usd", "binance-smart-chain")
        )

        val result = CoinResponseMapper.injectVirtualTokens(coins, tokens, virtualCoinMapper)

        assertEquals(2, result.size)
        assertTrue(result.any { it.coinUid == "bsc-usd" && it.blockchainUid == "binance-smart-chain" })
        assertTrue(result.any { it.coinUid == "tether" && it.blockchainUid == "binance-smart-chain" })
    }

    @Test
    fun injectVirtualTokens_missingTetherCoin_returnsOriginalTokens() {
        val coins = listOf(
            createCoin("bsc-usd", "BSC-USD")
        )
        val tokens = listOf(
            createToken("bsc-usd", "binance-smart-chain")
        )

        val result = CoinResponseMapper.injectVirtualTokens(coins, tokens, virtualCoinMapper)

        assertEquals(1, result.size)
        assertEquals("bsc-usd", result[0].coinUid)
    }

    @Test
    fun injectVirtualTokens_missingBscUsdCoin_returnsOriginalTokens() {
        val coins = listOf(
            createCoin("tether", "USDT")
        )
        val tokens = listOf(
            createToken("some-token", "binance-smart-chain")
        )

        val result = CoinResponseMapper.injectVirtualTokens(coins, tokens, virtualCoinMapper)

        assertEquals(1, result.size)
        assertEquals("some-token", result[0].coinUid)
    }

    @Test
    fun injectVirtualTokens_bscUsdTokenOnWrongBlockchain_returnsOriginalTokens() {
        val coins = listOf(
            createCoin("tether", "USDT"),
            createCoin("bsc-usd", "BSC-USD")
        )
        val tokens = listOf(
            createToken("bsc-usd", "ethereum")
        )

        val result = CoinResponseMapper.injectVirtualTokens(coins, tokens, virtualCoinMapper)

        assertEquals(1, result.size)
        assertEquals("bsc-usd", result[0].coinUid)
    }

    @Test
    fun injectVirtualTokens_emptyCoins_returnsOriginalTokens() {
        val coins = emptyList<Coin>()
        val tokens = listOf(
            createToken("bsc-usd", "binance-smart-chain")
        )

        val result = CoinResponseMapper.injectVirtualTokens(coins, tokens, virtualCoinMapper)

        assertEquals(tokens, result)
    }

    @Test
    fun injectVirtualTokens_emptyTokens_returnsEmptyList() {
        val coins = listOf(
            createCoin("tether", "USDT"),
            createCoin("bsc-usd", "BSC-USD")
        )
        val tokens = emptyList<TokenEntity>()

        val result = CoinResponseMapper.injectVirtualTokens(coins, tokens, virtualCoinMapper)

        assertTrue(result.isEmpty())
    }

    // endregion

    // region filterValidTokens tests

    @Test
    fun transform_litecoinNativeToken_createsDerivedTokens() {
        val result = CoinResponseMapper.transform(
            listOf(createToken(coinUid = "litecoin", blockchainUid = "litecoin", decimals = 8))
        )

        assertEquals(
            listOf(
                "derived" to "Bip44",
                "derived" to "Bip49",
                "derived" to "Bip84",
                "derived" to "Bip86"
            ),
            result.map { it.type to it.reference }
        )
        assertTrue(result.all { it.coinUid == "litecoin" && it.blockchainUid == "litecoin" && it.decimals == 8 })
    }

    @Test
    fun transform_litecoinNativeAndMwebTokens_preservesMwebToken() {
        val result = CoinResponseMapper.transform(
            listOf(
                createToken(coinUid = "litecoin", blockchainUid = "litecoin", decimals = 8),
                createToken(
                    coinUid = "litecoin",
                    blockchainUid = "litecoin",
                    type = "mweb",
                    decimals = 8
                )
            )
        )

        assertEquals(
            1,
            result.count { it.coinUid == "litecoin" && it.blockchainUid == "litecoin" && it.type == "mweb" })
        assertEquals(
            4,
            result.count { it.coinUid == "litecoin" && it.blockchainUid == "litecoin" && it.type == "derived" })
    }

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

        val result = CoinResponseMapper.filterValidTokens(tokens, blockchains)

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

        val result = CoinResponseMapper.filterValidTokens(tokens, blockchains)

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

        val result = CoinResponseMapper.filterValidTokens(tokens, blockchains)

        assertTrue(result.isEmpty())
    }

    @Test
    fun filterValidTokens_emptyTokens_returnsEmptyList() {
        val blockchains = listOf(
            BlockchainEntity(uid = "ethereum", name = "Ethereum", eip3091url = null)
        )
        val tokens = emptyList<TokenEntity>()

        val result = CoinResponseMapper.filterValidTokens(tokens, blockchains)

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

        val result = CoinResponseMapper.filterValidTokens(tokens, blockchains)

        assertEquals(2, result.size)
        assertEquals("eth", result[0].coinUid)
        assertEquals("bnb", result[1].coinUid)
    }

    // endregion

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
