package cash.p.terminal.network.pirate.domain.useCase

import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FiatCurrencyRateService(
    private val piratePlaceRepository: PiratePlaceRepository,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = CACHE_TTL_MILLIS,
) {
    private val mutex = Mutex()
    private var cachedPrices: CachedPrices? = null

    suspend fun rate(fromCurrencyCode: String, toCurrencyCode: String): BigDecimal? {
        val fromCode = fromCurrencyCode.lowercase()
        val toCode = toCurrencyCode.lowercase()
        if (fromCode == toCode) return BigDecimal.ONE

        val fromCurrencyPerUsdt = usdtToCurrencyRate(fromCode) ?: return null
        val toCurrencyPerUsdt = usdtToCurrencyRate(toCode) ?: return null

        return toCurrencyPerUsdt.divide(fromCurrencyPerUsdt, RATE_SCALE, RoundingMode.HALF_UP)
    }

    suspend fun usdtToCurrencyRate(currencyCode: String): BigDecimal? {
        val code = currencyCode.lowercase()
        if (code == USD) return BigDecimal.ONE
        return usdtPriceMap()?.get(code)?.takeIf { it > BigDecimal.ZERO }
    }

    private suspend fun usdtPriceMap(): Map<String, BigDecimal>? {
        cachedPrices?.takeIf { it.isFresh(currentTimeMillis()) }?.let { return it.prices }

        return mutex.withLock {
            cachedPrices?.takeIf { it.isFresh(currentTimeMillis()) }?.let { return@withLock it.prices }

            try {
                piratePlaceRepository.getCoinInfo(TETHER_COIN_GECKO_UID).price
                    .mapKeys { it.key.lowercase() }
                    .also { cachedPrices = CachedPrices(it, currentTimeMillis()) }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun CachedPrices.isFresh(now: Long): Boolean = now - fetchedAtMillis < ttlMillis

    private data class CachedPrices(
        val prices: Map<String, BigDecimal>,
        val fetchedAtMillis: Long,
    )

    companion object {
        const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
        private const val RATE_SCALE = 18
        private const val TETHER_COIN_GECKO_UID = "tether"
        private const val USD = "usd"
    }
}
