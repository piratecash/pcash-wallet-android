package cash.p.terminal.core.adapters

import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.core.App
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.data.repository.EvmTransactionRepository
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.TransactionData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal

internal class EvmAdapter(evmTransactionRepository: EvmTransactionRepository, coinManager: ICoinManager) :
    BaseEvmAdapter(evmTransactionRepository, decimal, coinManager) {

    // IAdapter

    override fun start() {
        // started via EthereumKitManager
    }

    override fun stop() {
        // stopped via EthereumKitManager
    }

    override suspend fun refresh() {
        // refreshed via EthereumKitManager
    }

    // IBalanceAdapter

    override val balanceState: AdapterState
        get() = getCombinedSyncState()

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = evmTransactionRepository.combinedSyncStateFlow

    override val balanceData: BalanceData
        get() = BalanceData(balanceInBigDecimal(evmTransactionRepository.accountState?.balance, decimal))

    override val balanceUpdatedFlow: Flow<Unit>
        get() = evmTransactionRepository.accountStateFlowable.map { }.asFlow()

    private fun getCombinedSyncState(): AdapterState {
        val balanceSyncState = evmTransactionRepository.syncState
        val txSyncState = evmTransactionRepository.transactionsSyncState

        return when {
            // Connecting phase: not started yet
            balanceSyncState is EthereumKit.SyncState.NotSynced &&
                balanceSyncState.error is EthereumKit.SyncError.NotStarted -> AdapterState.Connecting

            // Error state
            balanceSyncState is EthereumKit.SyncState.NotSynced ->
                AdapterState.NotSynced(balanceSyncState.error)

            // Syncing balance
            balanceSyncState is EthereumKit.SyncState.Syncing -> AdapterState.Syncing()

            // Transaction sync error (balance is synced)
            balanceSyncState is EthereumKit.SyncState.Synced &&
                txSyncState is EthereumKit.SyncState.NotSynced &&
                txSyncState.error !is EthereumKit.SyncError.NotStarted ->
                    AdapterState.NotSynced(txSyncState.error)

            // Fully synced
            else -> AdapterState.Synced
        }
    }

    // ISendEthereumAdapter

    override fun getTransactionData(amount: BigDecimal, address: Address): TransactionData {
        val amountBigInt = amount.movePointRight(decimal).toBigInteger()
        return TransactionData(address, amountBigInt, byteArrayOf())
    }

    companion object {
        const val decimal = 18

        fun clear(walletId: String) {
            val networkTypes = listOf(
                Chain.Ethereum,
                Chain.BinanceSmartChain,
                Chain.Polygon,
                Chain.Avalanche,
                Chain.Optimism,
                Chain.ArbitrumOne,
                Chain.Gnosis,
            )
            networkTypes.forEach {
                EthereumKit.clear(App.instance, it, walletId)
            }
        }
    }

}
