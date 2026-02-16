package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.BalanceData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal

interface IBalanceAdapter {
    val balanceState: AdapterState
    val balanceStateUpdatedFlow: Flow<Unit>

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