package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.BalanceData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.math.BigDecimal

interface IBalanceAdapter {
    val balanceState: AdapterState
    val balanceStateUpdatedFlow: Flow<Unit>

    /**
     * Transaction-history sync state. Drives the wallet-row progress indicator but
     * does NOT block Send/Swap. Defaults to [balanceState] — this preserves behavior
     * for adapters where the balance itself is derived from history (BTC, etc.).
     */
    val transactionsSyncState: AdapterState
        get() = balanceState

    /**
     * Defaults to `emptyFlow()`. `AdapterManager.subscribeToBalanceUpdates` merges
     * this flow with `balanceStateUpdatedFlow`; an empty default avoids a double tick
     * on every `balanceStateUpdatedFlow` emission for adapters that don't track tx
     * sync separately. Adapters that override `transactionsSyncState` should also
     * override this to emit on tx-sync changes.
     */
    val transactionsSyncStateUpdatedFlow: Flow<Unit>
        get() = emptyFlow()

    val balanceData: BalanceData
    val balanceUpdatedFlow: Flow<Unit>

    val fee: StateFlow<BigDecimal>
        get() = DEFAULT_FEE

    val maxSpendableBalance: BigDecimal
        get() = balanceData.available

    fun sendAllowed() = balanceState !is AdapterState.NotSynced

    companion object {
        private val DEFAULT_FEE: StateFlow<BigDecimal> = MutableStateFlow(BigDecimal.ZERO)
    }
}
