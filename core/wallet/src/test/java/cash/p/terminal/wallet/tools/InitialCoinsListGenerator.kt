package cash.p.terminal.wallet.tools

import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.DumpManager
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.models.TokenEntity
import cash.p.terminal.wallet.models.TokenResponse
import cash.p.terminal.wallet.providers.HsProvider
import cash.p.terminal.wallet.providers.RetrofitUtils
import cash.p.terminal.wallet.storage.initialCoinsFile
import cash.p.terminal.wallet.storage.validateDumpSql
import cash.p.terminal.wallet.syncers.CoinResponseMapper
import cash.p.terminal.wallet.syncers.MappedCoinData
import io.horizontalsystems.core.entities.BlockchainType
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

private const val MIN_RECORD_COUNT_RATIO = 0.9

/**
 * Regenerates the checked-in initial coins list dump from the live coins API.
 * Skipped by default (assumeTrue guard) so normal test runs never touch the network;
 * run explicitly via tools/update-coins-list.sh.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InitialCoinsListGenerator {

    @Test
    fun updateInitialCoinsList() {
        assumeTrue(System.getProperty("updateCoinsList") == "true")

        val apiKey = requireNotNull(System.getProperty("marketApiKey")) {
            "marketApiKey system property is required"
        }
        val client = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val service = RetrofitUtils(client)
            .build(HsProvider.COINS_API_BASE_URL, mapOf("apikey" to apiKey))
            .create(HsProvider.MarketService::class.java)

        val coinsResponse = service.getAllCoins().blockingGet()
        val blockchainsResponse = service.getAllBlockchains().blockingGet()
        val tokensResponse = service.getAllTokens().blockingGet()

        val virtualCoinMapper = VirtualCoinMapper()
        val mapped = CoinResponseMapper.mapFetched(
            coinsResponse,
            blockchainsResponse,
            tokensResponse,
            virtualCoinMapper
        )
        val dump = DumpManager.getInitialDump(mapped.blockchains, mapped.coins, mapped.tokens)

        assertFetchedDataIsSane(mapped)
        assertNoPipelineIntroducedPkCollisions(mapped, tokensResponse, virtualCoinMapper)
        validateDumpSql(dump)

        initialCoinsFile().writeText(dump)
    }

    private fun assertFetchedDataIsSane(mapped: MappedCoinData) {
        assertTrue("Fetched coins list is empty", mapped.coins.isNotEmpty())
        assertTrue("Fetched blockchains list is empty", mapped.blockchains.isNotEmpty())
        assertTrue("Fetched tokens list is empty", mapped.tokens.isNotEmpty())

        assertRecordCountsNotRegressed(mapped)
        assertUniqueKeys("coins.uid", mapped.coins.map { it.uid })
        assertUniqueKeys("blockchains.uid", mapped.blockchains.map { it.uid })

        val coinUids = mapped.coins.map { it.uid }.toSet()
        assertTrue(
            "Token references a coin missing from the coins list",
            mapped.tokens.all { it.coinUid in coinUids }
        )
        assertAnchorsPresent(mapped, coinUids)
    }

    private fun assertRecordCountsNotRegressed(mapped: MappedCoinData) {
        val currentDump = initialCoinsFile().readText()
        assertCountNotRegressed("BlockchainEntity", mapped.blockchains.size, currentDump)
        assertCountNotRegressed("Coin", mapped.coins.size, currentDump)
        assertCountNotRegressed("TokenEntity", mapped.tokens.size, currentDump)
    }

    private fun assertCountNotRegressed(table: String, fetchedCount: Int, currentDump: String) {
        val currentCount =
            currentDump.lines().count { it.startsWith("INSERT OR REPLACE INTO $table ") }
        assertTrue(
            "$table record count regressed too much: fetched $fetchedCount, current asset has $currentCount",
            fetchedCount >= currentCount * MIN_RECORD_COUNT_RATIO
        )
    }

    private fun assertUniqueKeys(label: String, keys: List<Any>) {
        assertTrue("Duplicate primary key detected for $label", keys.size == keys.toSet().size)
    }

    /**
     * The final keep-last dedup in mapFetched mirrors the app database and silently
     * collapses duplicate PKs, so upstream dirt must not abort generation. Collisions
     * that only appear AFTER transform/injectVirtualTokens, however, signal a pipeline
     * or upstream-schema problem and must stop the run before the asset is written.
     */
    private fun assertNoPipelineIntroducedPkCollisions(
        mapped: MappedCoinData,
        tokensResponse: List<TokenResponse>,
        virtualCoinMapper: VirtualCoinMapper
    ) {
        val rawCounts = primaryKeyCounts(tokensResponse.map { CoinResponseMapper.tokenEntity(it) })
        val pipelineCounts = primaryKeyCounts(
            CoinResponseMapper.tokenPipeline(
                mapped.coins,
                mapped.blockchains,
                tokensResponse,
                virtualCoinMapper
            )
        )
        // Per-key multiplicities, not collision-key sets: a key already duplicated upstream
        // must still trip the guard when the pipeline adds yet another row for it.
        val introduced = pipelineCounts.filter { (pk, count) ->
            count > 1 && count > (rawCounts[pk] ?: 0)
        }.keys
        assertTrue(
            "Token PK collisions introduced by transform/injectVirtualTokens: $introduced",
            introduced.isEmpty()
        )
    }

    private fun primaryKeyCounts(tokens: List<TokenEntity>): Map<List<String>, Int> =
        tokens.groupingBy { CoinResponseMapper.primaryKey(it) }.eachCount()

    private fun assertAnchorsPresent(mapped: MappedCoinData, coinUids: Set<String>) {
        listOf("piratecash", "cosanta", "tether", "bitcoin").forEach { uid ->
            assertTrue("Expected anchor coin '$uid' is missing", uid in coinUids)
            assertTokenAnchor(mapped.tokens, "any '$uid'") { it.coinUid == uid }
        }
        assertNativeChainAnchors(mapped.tokens)
        assertStartupAnchors(mapped.tokens)
        assertDefaultWalletAnchors(mapped.tokens)
    }

    /**
     * Derived anchors must carry real decimals: CoinDao maps a null-decimal row to
     * TokenType.Unsupported, which would make the base chain unusable from the asset.
     */
    private fun assertNativeChainAnchors(tokens: List<TokenEntity>) {
        assertTokenAnchor(tokens, "mweb litecoin") {
            it.coinUid == "litecoin" && it.blockchainUid == "litecoin" &&
                    it.type == "mweb" && it.decimals == 8 && it.reference == ""
        }
        listOf("bitcoin", "litecoin").forEach { chain ->
            listOf("Bip44", "Bip49", "Bip84", "Bip86").forEach { reference ->
                assertTokenAnchor(tokens, "$chain derived '$reference'") {
                    it.coinUid == chain && it.blockchainUid == chain &&
                            it.type == "derived" && it.decimals == 8 && it.reference == reference
                }
            }
        }
    }

    /**
     * Exact tokens App.needForceUpdateCoins() checks at startup; shipping an asset
     * without them would force a full network resync on every first launch.
     */
    private fun assertStartupAnchors(tokens: List<TokenEntity>) {
        assertTokenAnchor(tokens, "zcash Shielded") {
            it.coinUid == "zcash" && it.blockchainUid == "zcash" &&
                    it.type == "address_spec_type" && it.decimals == 8 && it.reference == "Shielded"
        }
        assertTokenAnchor(tokens, "tether on binance-smart-chain") {
            it.coinUid == "tether" && it.blockchainUid == "binance-smart-chain"
        }
    }

    /**
     * Every default wallet created on a fresh install must resolve from the asset with
     * the chain's fixed decimals: CoinDao maps null decimals to TokenType.Unsupported,
     * and a wrong value would scale balances and transfers incorrectly.
     */
    private fun assertDefaultWalletAnchors(tokens: List<TokenEntity>) {
        val expectedDecimals = mapOf(
            TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84)) to 8,
            TokenQuery(BlockchainType.Monero, TokenType.Native) to 12,
            TokenQuery.ZcashUnified to 8,
            TokenQuery(BlockchainType.Ethereum, TokenType.Native) to 18,
            TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Native) to 18,
            TokenQuery.PirateCashBnb to 8,
            TokenQuery.CosantaBnb to 8
        )
        TokenQuery.defaultTokenQueries.forEach { query ->
            val decimals = requireNotNull(expectedDecimals[query]) {
                "No expected decimals for default token query '${query.id}' — update the map"
            }
            val values = query.tokenType.values
            assertTokenAnchor(tokens, "default wallet '${query.id}'") {
                it.blockchainUid == query.blockchainType.uid &&
                        it.type == values.type && it.reference == values.reference &&
                        it.decimals == decimals
            }
        }
    }

    private fun assertTokenAnchor(
        tokens: List<TokenEntity>,
        description: String,
        predicate: (TokenEntity) -> Boolean
    ) {
        assertTrue("Expected $description token is missing", tokens.any(predicate))
    }
}
