package cash.p.terminal.network.pirate.domain.useCase

import cash.p.terminal.network.pirate.domain.enity.PiratePlaceCoin
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FiatCurrencyRateServiceTest {

    private val piratePlaceRepository = mockk<PiratePlaceRepository>()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun rate_sameCurrency_returnsOne() = runTest {
        val service = createService()

        assertBigDecimalEquals(BigDecimal.ONE, service.rate("RUB", "rub"))
        coVerify(exactly = 0) { piratePlaceRepository.getCoinInfo(any()) }
    }

    @Test
    fun rate_rubToUsd_dividesUsdByRub() = runTest {
        mockTetherPrices(mapOf("rub" to BigDecimal("109")))
        val service = createService()

        val expected = BigDecimal.ONE.divide(BigDecimal("109"), 18, RoundingMode.HALF_UP)

        assertBigDecimalEquals(expected, service.rate("RUB", "USD"))
    }

    @Test
    fun rate_rubToJpy_dividesTargetBySource() = runTest {
        mockTetherPrices(
            mapOf(
                "rub" to BigDecimal("109"),
                "jpy" to BigDecimal("153"),
            )
        )
        val service = createService()

        val expected = BigDecimal("153").divide(BigDecimal("109"), 18, RoundingMode.HALF_UP)

        assertBigDecimalEquals(expected, service.rate("RUB", "JPY"))
    }

    @Test
    fun rate_uppercasePriceKeys_normalizesKeys() = runTest {
        mockTetherPrices(mapOf("RUB" to BigDecimal("109")))
        val service = createService()

        val expected = BigDecimal.ONE.divide(BigDecimal("109"), 18, RoundingMode.HALF_UP)

        assertBigDecimalEquals(expected, service.rate("rub", "usd"))
    }

    @Test
    fun rate_missingCurrency_returnsNull() = runTest {
        mockTetherPrices(mapOf("rub" to BigDecimal("109")))
        val service = createService()

        assertNull(service.rate("EUR", "USD"))
    }

    @Test
    fun rate_zeroSourceCurrency_returnsNull() = runTest {
        mockTetherPrices(mapOf("rub" to BigDecimal.ZERO))
        val service = createService()

        assertNull(service.rate("RUB", "USD"))
    }

    @Test
    fun usdtToCurrencyRate_usdPriceMissing_returnsOne() = runTest {
        val service = createService()

        assertBigDecimalEquals(BigDecimal.ONE, service.usdtToCurrencyRate("USD"))
        coVerify(exactly = 0) { piratePlaceRepository.getCoinInfo(any()) }
    }

    @Test
    fun rate_cachedRate_reusesRepositoryWithinTtl() = runTest {
        var now = 0L
        mockTetherPrices(mapOf("rub" to BigDecimal("109")))
        val service = createService(currentTimeMillis = { now }, ttlMillis = 100L)

        service.rate("RUB", "USD")
        service.rate("RUB", "USD")
        coVerify(exactly = 1) { piratePlaceRepository.getCoinInfo("tether") }

        now = 101L
        service.rate("RUB", "USD")
        coVerify(exactly = 2) { piratePlaceRepository.getCoinInfo("tether") }
    }

    private fun createService(
        currentTimeMillis: () -> Long = { 0L },
        ttlMillis: Long = FiatCurrencyRateService.CACHE_TTL_MILLIS,
    ) = FiatCurrencyRateService(
        piratePlaceRepository = piratePlaceRepository,
        currentTimeMillis = currentTimeMillis,
        ttlMillis = ttlMillis,
    )

    private fun mockTetherPrices(prices: Map<String, BigDecimal>) {
        val coin = mockk<PiratePlaceCoin> {
            every { price } returns prices
        }
        coEvery { piratePlaceRepository.getCoinInfo("tether") } returns coin
    }

    private fun assertBigDecimalEquals(expected: BigDecimal, actual: BigDecimal?) {
        assertEquals(0, expected.compareTo(requireNotNull(actual)))
    }
}
