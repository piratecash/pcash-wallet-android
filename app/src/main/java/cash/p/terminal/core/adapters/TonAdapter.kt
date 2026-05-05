package cash.p.terminal.core.adapters

import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.core.managers.toAdapterState
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.BalanceData
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.core.TonKit.SendAmount
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.SyncState
import io.reactivex.BackpressureStrategy
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal
import java.math.BigInteger

class TonAdapter(tonKitWrapper: TonKitWrapper) : BaseTonAdapter(tonKitWrapper, 9), ISendTonAdapter {

    private val balanceUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var balance = getBalanceFromAccount(tonKit.account)
    private val _fee = MutableStateFlow(BigDecimal.ZERO)
    override val fee: StateFlow<BigDecimal> = _fee.asStateFlow()

    override fun start() {
        coroutineScope.launch {
            tonKit.accountFlow.collect { account ->
                balance = getBalanceFromAccount(account)
                estimateFeeForMax()
                balanceUpdatedSubject.onNext(Unit)
            }
        }
    }

    private fun getBalanceFromAccount(account: Account?): BigDecimal {
        return account?.balance?.toBigDecimal()?.movePointLeft(decimals) ?: BigDecimal.ZERO
    }

    private suspend fun estimateFeeForMax() {
        if (balance <= BigDecimal.ZERO) {
            _fee.value = BigDecimal.ZERO
            return
        }
        tryOrNull {
            val selfAddress = FriendlyAddress.parse(receiveAddress, false)
            val estimatedFee = tonKit.estimateFee(selfAddress, SendAmount.Max, null)
            _fee.value = estimatedFee.toBigDecimal().movePointLeft(decimals).stripTrailingZeros()
        }
    }

    override fun stop() {
        coroutineScope.cancel()
    }

    override suspend fun refresh() {
        if (!isSyncing()) {
            tonKit.refresh()
        }
    }

    private fun isSyncing(): Boolean {
        return tonKit.syncStateFlow.value is SyncState.Syncing ||
                tonKit.eventSyncStateFlow.value is SyncState.Syncing ||
                tonKit.jettonSyncStateFlow.value is SyncState.Syncing
    }

    override val debugInfo: String
        get() = ""

    // Balance: account/balance sync only — must not block send while history (events/jettons) loads.
    override val balanceState: AdapterState
        get() = tonKit.syncStateFlow.value.toAdapterState()

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = tonKit.syncStateFlow.map { }

    override val transactionsSyncState: AdapterState
        get() {
            val event = tonKit.eventSyncStateFlow.value
            val jetton = tonKit.jettonSyncStateFlow.value
            return when {
                event is SyncState.NotSynced -> AdapterState.NotSynced(event.error)
                jetton is SyncState.NotSynced -> AdapterState.NotSynced(jetton.error)
                event is SyncState.Syncing || jetton is SyncState.Syncing -> AdapterState.SearchingTxs(0)
                else -> AdapterState.Synced
            }
        }

    override val transactionsSyncStateUpdatedFlow: Flow<Unit>
        get() = merge(tonKit.eventSyncStateFlow.map { }, tonKit.jettonSyncStateFlow.map { })

    override val balanceData: BalanceData
        get() = BalanceData(balance)
    override val balanceUpdatedFlow: Flow<Unit>
        get() = balanceUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER).asFlow()

    override val maxSpendableBalance: BigDecimal
        get() = maxOf(balance - fee.value, BigDecimal.ZERO)

    private fun getSendAmount(amount: BigDecimal) = when {
        amount.compareTo(maxSpendableBalance) == 0 -> SendAmount.Max
        else -> SendAmount.Amount(amount.movePointRight(decimals).toBigInteger())
    }

    override suspend fun send(amount: BigDecimal, address: FriendlyAddress, memo: String?) {
        tonKit.send(address, getSendAmount(amount), memo)
    }

    override suspend fun sendWithPayload(amount: BigInteger, address: String, payload: String) {
        sendWithPayloadBoc(amount, address, payload)
    }

    override suspend fun estimateFee(
        amount: BigDecimal,
        address: FriendlyAddress,
        memo: String?
    ): BigDecimal {
        val estimateFee = tonKit.estimateFee(address, getSendAmount(amount), memo)
        return estimateFee.toBigDecimal(decimals).stripTrailingZeros()
    }

    companion object {
        fun getAmount(kitAmount: Long): BigDecimal {
            return kitAmount.toBigDecimal().movePointLeft(9).stripTrailingZeros()
        }
    }
}
