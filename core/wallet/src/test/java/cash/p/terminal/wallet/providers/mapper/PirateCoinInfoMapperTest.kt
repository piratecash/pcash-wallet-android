package cash.p.terminal.wallet.providers.mapper

import cash.p.terminal.network.pirate.domain.enity.Changes
import cash.p.terminal.network.pirate.domain.enity.CommunityData
import cash.p.terminal.network.pirate.domain.enity.Links
import cash.p.terminal.network.pirate.domain.enity.MarketCap
import cash.p.terminal.network.pirate.domain.enity.PiratePlaceCoin
import cash.p.terminal.network.pirate.domain.enity.PriceChange
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PirateCoinInfoMapperTest {

    private val mapper = PirateCoinInfoMapper()

    @Test
    fun mapCoinInfo_currencyRub_usesRubMarketCapAndDilutedValue() {
        val coin = piratePlaceCoin(
            marketCap = mapOf("usd" to BigDecimal("2660000"), "rub" to BigDecimal("191000000")),
            fullyDilutedValuation = mapOf("usd" to BigDecimal("1160000"), "rub" to BigDecimal("83000000")),
        )

        val result = mapper.mapCoinInfo(coin, currencyCode = "RUB")

        assertEquals(BigDecimal("191000000"), result.marketData.marketCap)
        assertEquals(BigDecimal("83000000"), result.marketData.dilutedMarketCap)
    }

    @Test
    fun mapCoinInfo_currencyUsd_usesUsdMarketCapAndDilutedValue() {
        val coin = piratePlaceCoin(
            marketCap = mapOf("usd" to BigDecimal("2660000"), "rub" to BigDecimal("191000000")),
            fullyDilutedValuation = mapOf("usd" to BigDecimal("1160000"), "rub" to BigDecimal("83000000")),
        )

        val result = mapper.mapCoinInfo(coin, currencyCode = "USD")

        assertEquals(BigDecimal("2660000"), result.marketData.marketCap)
        assertEquals(BigDecimal("1160000"), result.marketData.dilutedMarketCap)
    }

    @Test
    fun mapCoinInfo_currencyMissingInMap_returnsNullMarketCap() {
        val coin = piratePlaceCoin(
            marketCap = mapOf("usd" to BigDecimal("2660000")),
            fullyDilutedValuation = mapOf("usd" to BigDecimal("1160000")),
        )

        val result = mapper.mapCoinInfo(coin, currencyCode = "RUB")

        assertNull(result.marketData.marketCap)
        assertNull(result.marketData.dilutedMarketCap)
    }

    private fun piratePlaceCoin(
        marketCap: Map<String, BigDecimal>,
        fullyDilutedValuation: Map<String, BigDecimal>,
    ) = PiratePlaceCoin(
        rank = 2201,
        id = "cosanta",
        name = "Cosanta",
        symbol = "COSA",
        circulatingSupply = BigDecimal("860000"),
        totalSupply = BigDecimal("860000"),
        maxSupply = null,
        changes = Changes(
            price = PriceChange(
                percentage1h = emptyMap(),
                percentage24h = emptyMap(),
                percentage7d = emptyMap(),
                percentage30d = emptyMap(),
                percentage1y = emptyMap(),
            ),
            marketCap = MarketCap(value24h = emptyMap()),
        ),
        marketCap = marketCap,
        image = "",
        price = emptyMap(),
        description = emptyMap(),
        links = Links(
            homepage = emptyList(),
            blockchainSite = emptyList(),
            officialForumUrl = null,
            chatUrl = null,
            announcementUrl = null,
            twitterScreenName = null,
            facebookUsername = null,
            bitcointalkIdentifier = null,
            telegramChannelIdentifier = null,
            subredditUrl = null,
        ),
        ath = emptyMap(),
        athPercentage = emptyMap(),
        high24h = emptyMap(),
        low24h = emptyMap(),
        communityData = CommunityData(
            facebookLikes = null,
            twitterFollowers = null,
            redditAveragePosts48h = null,
            redditAverageComments48h = null,
            redditSubscribers = null,
            redditAccountsActive48h = null,
            telegramChannelUserCount = null,
        ),
        graphs = emptyMap(),
        isActive = true,
        isCurrency = false,
        isRealCurrency = false,
        updatedAt = "",
        fullyDilutedValuation = fullyDilutedValuation,
    )
}
