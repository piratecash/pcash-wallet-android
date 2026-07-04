package cash.p.terminal.modules.paycore

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PayCoreBanksRepository(
    private val apiService: PayCoreApiService,
) {
    private val mutex = Mutex()
    @Volatile
    private var cachedBanksByNetworkType: Map<PayCoreTicker, CachedBanks> = emptyMap()

    suspend fun getBanks(networkType: PayCoreTicker): List<PayCoreBankResponse> {
        val now = System.currentTimeMillis()
        cachedBanksByNetworkType[networkType]
            ?.takeIf { it.isActual(now) }
            ?.let { return it.banks }

        return mutex.withLock {
            val lockedNow = System.currentTimeMillis()
            cachedBanksByNetworkType[networkType]
                ?.takeIf { it.isActual(lockedNow) }
                ?.let { return@withLock it.banks }

            val banks = apiService.getBanks(networkType)
            cachedBanksByNetworkType = if (banks.isEmpty()) {
                cachedBanksByNetworkType - networkType
            } else {
                val cacheEntry = networkType to CachedBanks(banks, lockedNow)
                cachedBanksByNetworkType + cacheEntry
            }
            banks.sortedBy { it.name }
        }
    }

    private data class CachedBanks(
        val banks: List<PayCoreBankResponse>,
        val createdAt: Long,
    ) {
        fun isActual(now: Long): Boolean = now - createdAt < BANKS_CACHE_TTL_MS
    }

    private companion object {
        const val BANKS_CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
