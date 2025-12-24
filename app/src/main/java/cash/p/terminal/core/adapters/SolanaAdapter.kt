package cash.p.terminal.core.adapters

import cash.p.terminal.core.App
import cash.p.terminal.core.managers.SolanaKitWrapper
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.BalanceData
import io.horizontalsystems.core.SafeSuspendedCall
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.models.Address
import io.horizontalsystems.solanakit.models.FullTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.math.BigDecimal
import java.math.BigInteger

class SolanaAdapter(private val kitWrapper: SolanaKitWrapper) :
    BaseSolanaAdapter(kitWrapper, decimal) {

    // IAdapter

    override fun start() {
        // started via EthereumKitManager
    }

    override fun stop() {
        // stopped via EthereumKitManager
    }

    override suspend fun refresh() {
        kitWrapper.solanaKit.refresh()
    }

    // IBalanceAdapter

    override val balanceState: AdapterState
        get() = getCombinedSyncState()

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = merge(
            solanaKit.balanceSyncStateFlow.map {},
            solanaKit.transactionsSyncStateFlow.map {}
        )

    override val balanceData: BalanceData
        get() = BalanceData(balanceInBigDecimal(solanaKit.balance, decimal))

    override val balanceUpdatedFlow: Flow<Unit>
        get() = solanaKit.balanceFlow.map {}

    // ISendSolanaAdapter
    override val availableBalance: BigDecimal
        get() {
            val availableBalance =
                balanceData.available - SolanaKit.fee - SolanaKit.accountRentAmount
            return if (availableBalance < BigDecimal.ZERO) BigDecimal.ZERO else availableBalance
        }

    override suspend fun send(amount: BigDecimal, to: Address): FullTransaction {
        if (signer == null) throw Exception()

        return SafeSuspendedCall.executeSuspendable {
            solanaKit.sendSol(to, amount.movePointRight(decimal).toLong(), signer)
        }
    }

    private fun getCombinedSyncState(): AdapterState {
        val balanceSyncState = solanaKit.syncState
        val txSyncState = solanaKit.transactionsSyncState

        return when {
            // Connecting phase: not started yet
            balanceSyncState is SolanaKit.SyncState.NotSynced &&
                balanceSyncState.error is SolanaKit.SyncError.NotStarted -> AdapterState.Connecting

            // Error state
            balanceSyncState is SolanaKit.SyncState.NotSynced ->
                AdapterState.NotSynced(balanceSyncState.error)

            // Syncing balance
            balanceSyncState is SolanaKit.SyncState.Syncing -> AdapterState.Syncing()

            // Balance synced, but transactions still syncing
            balanceSyncState is SolanaKit.SyncState.Synced &&
                txSyncState is SolanaKit.SyncState.Syncing -> AdapterState.SearchingTxs(0)

            // Transaction sync error (balance is synced)
            balanceSyncState is SolanaKit.SyncState.Synced &&
                txSyncState is SolanaKit.SyncState.NotSynced &&
                txSyncState.error !is SolanaKit.SyncError.NotStarted ->
                    AdapterState.NotSynced(txSyncState.error)

            // Fully synced
            else -> AdapterState.Synced
        }
    }

    companion object {
        const val decimal = 9

        private fun scaleDown(amount: BigDecimal, decimals: Int = decimal): BigDecimal {
            return amount.movePointLeft(decimals).stripTrailingZeros()
        }

        private fun scaleUp(amount: BigDecimal, decimals: Int = decimal): BigInteger {
            return amount.movePointRight(decimals).toBigInteger()
        }

        fun balanceInBigDecimal(balance: Long?, decimal: Int): BigDecimal {
            balance?.toBigDecimal()?.let {
                return scaleDown(it, decimal)
            } ?: return BigDecimal.ZERO
        }

        fun clear(walletId: String) {
            SolanaKit.clear(App.instance, walletId)
        }
    }

}
