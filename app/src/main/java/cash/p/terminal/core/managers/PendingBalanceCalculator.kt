package cash.p.terminal.core.managers

import cash.p.terminal.core.isNative
import cash.p.terminal.entities.PendingTransactionEntity
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

class PendingBalanceCalculator(
    private val pendingRepository: PendingTransactionRepository,
    dispatcherProvider: DispatcherProvider
) : Clearable {
    private val pendingCache = ConcurrentHashMap<String, List<PendingTransactionEntity>>()
    private val observingJobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    fun startObserving(accountId: String) {
        observingJobs.computeIfAbsent(accountId) {
            scope.launch {
                pendingRepository.getActivePendingFlow(accountId).collect { list ->
                    pendingCache[accountId] = list
                }
            }
        }
    }

    fun stopObserving(accountId: String) {
        observingJobs.remove(accountId)?.cancel()
        pendingCache.remove(accountId)
    }

    override fun clear() {
        scope.cancel()
        observingJobs.clear()
        pendingCache.clear()
    }

    fun adjustBalance(wallet: Wallet, rawBalance: BalanceData): BalanceData {
        val pendingList = pendingCache[wallet.account.id] ?: return rawBalance
        val deduction = calculateDeduction(pendingList, wallet.token, rawBalance.available)
        return rawBalance.copy(available = rawBalance.available - deduction)
    }

    /**
     * Smart deduction algorithm that automatically handles different SDK behaviors:
     * - TON/EVM: SDK doesn't deduct until confirmed → we apply full deduction
     * - Bitcoin/Zcash: SDK deducts immediately → we apply 0 deduction
     * - Mixed: SDK partially deducted → we apply remaining deduction
     */
    private fun calculateDeduction(
        pendingList: List<PendingTransactionEntity>,
        token: Token,
        currentSdkBalance: BigDecimal
    ): BigDecimal {
        val relevantPending = pendingList.filter {
            it.coinUid == token.coin.uid &&
                it.tokenTypeId == token.type.id &&
                it.blockchainTypeUid == token.blockchainType.uid
        }
        if (relevantPending.isEmpty()) return BigDecimal.ZERO

        // Fee is only deducted from native token balance (not ERC-20/TRC-20/Jetton)
        val isNativeToken = token.type.isNative

        // 1. Calculate total pending amount (amount + fee for native tokens only)
        val totalPendingAmount = relevantPending.sumOf { entity ->
            val amount = entity.amountAtomic.toBigDecimal().movePointLeft(token.decimals)
            val fee = if (isNativeToken) {
                entity.feeAtomic?.toBigDecimal()?.movePointLeft(token.decimals)
                    ?: BigDecimal.ZERO
            } else BigDecimal.ZERO
            amount + fee
        }

        // 2. Get baseline SDK balance (max from all pending - earliest "clean" balance)
        val baselineSdkBalance = relevantPending.maxOf { entity ->
            entity.sdkBalanceAtCreationAtomic.toBigDecimal().movePointLeft(token.decimals)
        }

        // 3. How much has SDK already deducted from baseline?
        // If SDK deducts immediately (Bitcoin): baselineSdkBalance - currentSdkBalance ≈ totalPending
        // If SDK doesn't deduct (TON): baselineSdkBalance - currentSdkBalance ≈ 0
        val sdkAlreadyDeducted = (baselineSdkBalance - currentSdkBalance)
            .coerceAtLeast(BigDecimal.ZERO)

        // 4. Our deduction = total pending - what SDK already deducted
        val ourDeduction = (totalPendingAmount - sdkAlreadyDeducted)
            .coerceAtLeast(BigDecimal.ZERO)

        // 5. Check for confirmed TXs to cleanup (per-TX confirmation detection)
        // Collect IDs first to avoid race condition during async deletion
        val idsToDelete = relevantPending.filter { entity ->
            val amount = entity.amountAtomic.toBigDecimal().movePointLeft(token.decimals)
            val fee = if (isNativeToken) {
                entity.feeAtomic?.toBigDecimal()?.movePointLeft(token.decimals)
                    ?: BigDecimal.ZERO
            } else BigDecimal.ZERO
            val sdkAtCreation = entity.sdkBalanceAtCreationAtomic.toBigDecimal()
                .movePointLeft(token.decimals)
            val expectedAfterConfirm = sdkAtCreation - amount - fee
            val tolerance = (amount + fee) * BigDecimal("0.05") // 5% tolerance
            (currentSdkBalance - expectedAfterConfirm).abs() <= tolerance
        }.map { it.id }
        if (idsToDelete.isNotEmpty()) {
            scope.launch {
                runCatching { pendingRepository.deleteByIds(idsToDelete) }
                    .onFailure { Timber.e(it, "Failed to delete confirmed pending txs: $idsToDelete") }
            }
        }

        // 6. Safety: never deduct more than current balance
        return ourDeduction.coerceAtMost(currentSdkBalance)
    }
}
