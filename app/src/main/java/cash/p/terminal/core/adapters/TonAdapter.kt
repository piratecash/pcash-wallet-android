package cash.p.terminal.core.adapters

import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.managers.TonKitWrapper
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal
import java.math.BigInteger

class TonAdapter(tonKitWrapper: TonKitWrapper) : BaseTonAdapter(tonKitWrapper, 9), ISendTonAdapter {

    private val balanceUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()
    private val balanceStateUpdatedSubject: PublishSubject<Unit> = PublishSubject.create()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var balance = getBalanceFromAccount(tonKit.account)

    override fun start() {
        coroutineScope.launch {
            tonKit.accountFlow.collect { account ->
                balance = getBalanceFromAccount(account)
                balanceUpdatedSubject.onNext(Unit)
            }
        }
        coroutineScope.launch {
            combine(
                tonKit.syncStateFlow,
                tonKit.eventSyncStateFlow,
                tonKit.jettonSyncStateFlow
            ) { accountSync, eventSync, jettonSync ->
                getCombinedSyncState(accountSync, eventSync, jettonSync)
            }.collect { combinedState ->
                balanceState = combinedState
                balanceStateUpdatedSubject.onNext(Unit)
            }
        }
    }

    private fun getCombinedSyncState(
        accountSync: SyncState,
        eventSync: SyncState,
        jettonSync: SyncState
    ): AdapterState {
        // Check for errors first (any sync in error state)
        if (accountSync is SyncState.NotSynced) {
            return AdapterState.NotSynced(accountSync.error)
        }
        if (eventSync is SyncState.NotSynced) {
            return AdapterState.NotSynced(eventSync.error)
        }
        if (jettonSync is SyncState.NotSynced) {
            return AdapterState.NotSynced(jettonSync.error)
        }

        // Account syncing - show as Syncing (balance sync phase)
        if (accountSync is SyncState.Syncing) {
            return AdapterState.Syncing()
        }

        // Account synced, but events or jettons still syncing - show as SearchingTxs
        if (eventSync is SyncState.Syncing || jettonSync is SyncState.Syncing) {
            return AdapterState.SearchingTxs(0)
        }

        // All synced
        return AdapterState.Synced
    }

    private fun getBalanceFromAccount(account: Account?): BigDecimal {
        return account?.balance?.toBigDecimal()?.movePointLeft(decimals) ?: BigDecimal.ZERO
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

    override var balanceState: AdapterState = AdapterState.Connecting
    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = balanceStateUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER).asFlow()
    override val balanceData: BalanceData
        get() = BalanceData(balance)
    override val balanceUpdatedFlow: Flow<Unit>
        get() = balanceUpdatedSubject.toFlowable(BackpressureStrategy.BUFFER).asFlow()

    override val availableBalance: BigDecimal
        get() = balance

    private fun getSendAmount(amount: BigDecimal) = when {
        amount.compareTo(availableBalance) == 0 -> SendAmount.Max
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

