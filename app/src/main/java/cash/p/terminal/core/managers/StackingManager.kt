package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.getUniqueKey
import cash.p.terminal.wallet.isCosanta
import cash.p.terminal.wallet.isPirateCash
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.time.Instant

data class StackingInfo(
    val unpaid: BigDecimal = BigDecimal.ZERO,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val nextAccrualAt: Instant? = null,
    val nextPayoutAt: Instant? = null,
)

class StackingManager(
    private val piratePlaceRepository: PiratePlaceRepository,
    private val localStorage: ILocalStorage,
    dispatcherProvider: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    private val state = MutableStateFlow<Map<String, StackingInfo>>(emptyMap())

    fun infoFlow(wallet: Wallet): Flow<StackingInfo?> =
        state.map { it[wallet.getUniqueKey()] }.distinctUntilChanged()

    fun unpaidFlow(wallet: Wallet): Flow<BigDecimal?> =
        infoFlow(wallet).map { it?.unpaid }.distinctUntilChanged()

    fun infoFor(wallet: Wallet): StackingInfo? = state.value[wallet.getUniqueKey()]

    fun unpaidFor(wallet: Wallet): BigDecimal = infoFor(wallet)?.unpaid ?: BigDecimal.ZERO

    fun loadInvestmentData(
        wallet: Wallet,
        address: String,
        currentBalance: BigDecimal? = null,
        forceRefresh: Boolean = false,
    ) {
        val coin = when {
            wallet.isPirateCash() -> StackingType.PCASH.value.lowercase()
            wallet.isCosanta() -> StackingType.COSANTA.value.lowercase()
            else -> {
                setInfo(wallet, StackingInfo())
                return
            }
        }
        loadInvestmentData(wallet, address, coin, currentBalance, forceRefresh)
    }

    private fun loadInvestmentData(
        wallet: Wallet,
        address: String,
        coin: String,
        currentBalance: BigDecimal?,
        forceRefresh: Boolean,
    ) {
        scope.launch(
            CoroutineExceptionHandler { _, throwable ->
                Timber.e(throwable, "Error loading investment data")
                ensureFallbackZero(wallet)
            }
        ) {
            restoreFromCacheIfNeeded(wallet, coin, address)

            if (!forceRefresh &&
                currentBalance != null &&
                isCacheValid(wallet, coin, address, currentBalance)
            ) {
                return@launch
            }

            val data = piratePlaceRepository.getInvestmentData(
                coinGeckoUid = coin,
                address = address,
            )
            val unpaid = tryOrNull { data.unrealizedValue.toBigDecimal() } ?: run {
                Timber.e("Malformed unrealizedValue: ${data.unrealizedValue}")
                ensureFallbackZero(wallet)
                return@launch
            }
            val totalIncome = tryOrNull { data.mint.toBigDecimal() } ?: BigDecimal.ZERO

            setInfo(
                wallet,
                StackingInfo(
                    unpaid = unpaid,
                    totalIncome = totalIncome,
                    nextAccrualAt = data.nextAccrualAt,
                    nextPayoutAt = data.nextPayoutAt,
                )
            )

            localStorage.saveStackingData(
                coin = coin,
                address = address,
                unpaid = unpaid,
                totalIncome = totalIncome,
                nextAccrualAt = data.nextAccrualAt?.toString(),
                nextPayoutAt = data.nextPayoutAt?.toString(),
                balance = currentBalance ?: unpaid,
            )
        }
    }

    private fun restoreFromCacheIfNeeded(wallet: Wallet, coin: String, address: String) {
        val key = wallet.getUniqueKey()
        if (state.value[key] != null) return
        val unpaid = tryOrNull { localStorage.getStackingUnpaid(coin, address) } ?: return
        val totalIncome = tryOrNull { localStorage.getStackingMint(coin, address) } ?: BigDecimal.ZERO
        val nextAccrualAt = localStorage.getStackingNextAccrualAt(coin, address)
            ?.let { tryOrNull { Instant.parse(it) } }
        val nextPayoutAt = localStorage.getStackingNextPayoutAt(coin, address)
            ?.let { tryOrNull { Instant.parse(it) } }
        val restored = StackingInfo(
            unpaid = unpaid,
            totalIncome = totalIncome,
            nextAccrualAt = nextAccrualAt,
            nextPayoutAt = nextPayoutAt,
        )
        state.update { map -> if (map[key] == null) map + (key to restored) else map }
    }

    private fun ensureFallbackZero(wallet: Wallet) {
        val key = wallet.getUniqueKey()
        state.update { map -> if (map[key] == null) map + (key to StackingInfo()) else map }
    }

    private fun setInfo(wallet: Wallet, info: StackingInfo) {
        val key = wallet.getUniqueKey()
        state.update { it + (key to info) }
    }

    private fun isCacheValid(
        wallet: Wallet,
        coin: String,
        address: String,
        currentBalance: BigDecimal,
    ): Boolean {
        if (state.value[wallet.getUniqueKey()] == null) return false
        val cachedBalance = tryOrNull { localStorage.getStackingCachedBalance(coin, address) }
            ?: return false
        if (cachedBalance.compareTo(currentBalance) != 0) return false
        val elapsed = System.currentTimeMillis() - localStorage.getStackingTimestamp(coin, address)
        return elapsed < CACHE_DURATION
    }

    companion object {
        private const val CACHE_DURATION = 5 * 60 * 1000L
    }
}
