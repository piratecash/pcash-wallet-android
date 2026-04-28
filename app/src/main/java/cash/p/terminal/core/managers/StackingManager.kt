package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.isCosanta
import cash.p.terminal.wallet.isPirateCash
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.time.Instant

class StackingManager(
    private val piratePlaceRepository: PiratePlaceRepository,
    private val localStorage: ILocalStorage,
    dispatcherProvider: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    private val _unpaidFlow = MutableStateFlow<BigDecimal?>(null)
    val unpaidFlow = _unpaidFlow.asStateFlow()

    private val _nextAccrualAtFlow = MutableStateFlow<Instant?>(null)
    val nextAccrualAtFlow = _nextAccrualAtFlow.asStateFlow()

    fun loadInvestmentData(
        wallet: Wallet,
        address: String,
        currentBalance: BigDecimal? = null,
        forceRefresh: Boolean = false,
    ) {
        if (wallet.isPirateCash()) {
            loadInvestmentData(address, StackingType.PCASH.value.lowercase(), currentBalance, forceRefresh)
        } else if (wallet.isCosanta()) {
            loadInvestmentData(address, StackingType.COSANTA.value.lowercase(), currentBalance, forceRefresh)
        } else {
            _unpaidFlow.value = BigDecimal.ZERO
        }
    }

    private fun loadInvestmentData(
        address: String,
        coin: String,
        currentBalance: BigDecimal?,
        forceRefresh: Boolean,
    ) {
        scope.launch(
            CoroutineExceptionHandler { _, throwable ->
                Timber.e(throwable, "Error loading investment data")
                if (_unpaidFlow.value == null) {
                    _unpaidFlow.value = BigDecimal.ZERO
                }
            }
        ) {
            if (_unpaidFlow.value == null) {
                _unpaidFlow.value = tryOrNull { localStorage.getStackingUnpaid(coin, address) }
                _nextAccrualAtFlow.value = localStorage.getStackingNextAccrualAt(coin, address)
                    ?.let { tryOrNull { Instant.parse(it) } }
            }

            if (!forceRefresh && currentBalance != null && isCacheValid(coin, address, currentBalance)) {
                return@launch
            }

            val data = piratePlaceRepository.getInvestmentData(
                coinGeckoUid = coin,
                address = address,
            )
            val unpaid = tryOrNull { data.unrealizedValue.toBigDecimal() } ?: run {
                Timber.e("Malformed unrealizedValue: ${data.unrealizedValue}")
                if (_unpaidFlow.value == null) _unpaidFlow.value = BigDecimal.ZERO
                return@launch
            }
            _unpaidFlow.value = unpaid
            _nextAccrualAtFlow.value = data.nextAccrualAt

            localStorage.saveStackingData(
                coin = coin,
                address = address,
                unpaid = unpaid,
                nextAccrualAt = data.nextAccrualAt?.toString(),
                balance = currentBalance ?: unpaid,
            )
        }
    }

    private fun isCacheValid(coin: String, address: String, currentBalance: BigDecimal): Boolean {
        if (_unpaidFlow.value == null) return false
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
