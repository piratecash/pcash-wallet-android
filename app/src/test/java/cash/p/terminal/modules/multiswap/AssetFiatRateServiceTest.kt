package cash.p.terminal.modules.multiswap

import cash.p.terminal.modules.paycore.PayCoreAssets
import cash.p.terminal.network.pirate.domain.useCase.FiatCurrencyRateService
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.models.CoinPrice
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.reactivex.Observable
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetFiatRateServiceTest {

    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val fiatCurrencyRateService = mockk<FiatCurrencyRateService>(relaxed = true)
    private val currency = Currency("USD", "$", 2, 0)

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun rate_fiatToken_usesFiatCurrencyRateService() = runTest {
        coEvery { fiatCurrencyRateService.rate("RUB", "USD") } returns BigDecimal("0.01")
        val service = createService()

        assertBigDecimalEquals(BigDecimal("0.01"), service.rate(PayCoreAssets.rubToken, currency))
        coVerify(exactly = 1) { fiatCurrencyRateService.rate("RUB", "USD") }
        verify(exactly = 0) { marketKit.coinPrice(any(), any()) }
    }

    @Test
    fun rate_cryptoToken_usesMarketKitCoinPrice() = runTest {
        val token = usdtToken()
        every { marketKit.coinPrice("tether", "USD") } returns coinPrice(BigDecimal("1.00"))
        val service = createService()

        assertBigDecimalEquals(BigDecimal("1.00"), service.rate(token, currency))
        coVerify(exactly = 0) { fiatCurrencyRateService.rate(any(), any()) }
    }

    @Test
    fun rateFlow_fiatToken_emitsFiatRate() = runTest {
        coEvery { fiatCurrencyRateService.rate("RUB", "USD") } returns BigDecimal("0.01")
        val service = createService()

        assertBigDecimalEquals(BigDecimal("0.01"), service.rateFlow("tag", PayCoreAssets.rubToken, currency).first())
    }

    @Test
    fun rateFlow_cryptoToken_emitsCurrentMarketKitPriceFirst() = runTest {
        val token = usdtToken()
        every { marketKit.coinPrice("tether", "USD") } returns coinPrice(BigDecimal("1.00"))
        every { marketKit.coinPriceObservable("tag", "tether", "USD") } returns Observable.never()
        val service = createService()

        assertBigDecimalEquals(BigDecimal("1.00"), service.rateFlow("tag", token, currency).first())
    }

    private fun createService() = AssetFiatRateService(
        marketKit = marketKit,
        fiatCurrencyRateService = fiatCurrencyRateService,
    )

    private fun usdtToken() = Token(
        coin = Coin(
            uid = "tether",
            name = "Tether",
            code = "USDT",
            marketCapRank = null,
            coinGeckoId = null,
            image = null,
        ),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7"),
        decimals = 6,
    )

    private fun coinPrice(value: BigDecimal) = CoinPrice(
        coinUid = "tether",
        currencyCode = "USD",
        value = value,
        diff1h = null,
        diff24h = null,
        diff7d = null,
        diff30d = null,
        diff1y = null,
        diffAll = null,
        timestamp = 0L,
    )

    private fun assertBigDecimalEquals(expected: BigDecimal, actual: BigDecimal?) {
        assertEquals(0, expected.compareTo(requireNotNull(actual)))
    }
}
