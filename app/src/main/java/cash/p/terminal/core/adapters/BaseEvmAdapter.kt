package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.ISendEthereumAdapter
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.EthereumKit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal
import java.math.BigInteger

internal abstract class BaseEvmAdapter(
    final override val evmTransactionRepository: EvmTransactionRepository,
    val decimal: Int,
    val coinManager: ICoinManager
) : IAdapter, ISendEthereumAdapter, IBalanceAdapter, IReceiveAdapter {

    override val debugInfo: String
        get() = evmTransactionRepository.debugInfo()

    override val statusInfo: Map<String, Any>
        get() = evmTransactionRepository.statusInfo()

    protected fun scaleDown(amount: BigDecimal, decimals: Int = decimal): BigDecimal {
        return amount.movePointLeft(decimals).stripTrailingZeros()
    }

    override val receiveAddress: String
        get() = evmTransactionRepository.receiveAddress.eip55

    override val isMainNet: Boolean
        get() = evmTransactionRepository.chain.isMainNet

    protected fun balanceInBigDecimal(balance: BigInteger?, decimal: Int): BigDecimal {
        balance?.toBigDecimal()?.let {
            return scaleDown(it, decimal)
        } ?: return BigDecimal.ZERO
    }

    private fun bscHistoricalSyncing(): AdapterState.Syncing? {
        if (evmTransactionRepository.getBlockchainType() != BlockchainType.BinanceSmartChain) return null
        val state = evmTransactionRepository.historicalSyncState.value as? EthereumKit.HistoricalSyncState.Syncing
            ?: return null
        return AdapterState.Syncing(
            progress = state.progress * 100.0,
            blocksRemained = state.blocksRemaining
        )
    }

    private fun forwardSyncing(): AdapterState.Syncing? {
        val state = evmTransactionRepository.forwardSyncState.value as? EthereumKit.ForwardSyncState.Syncing
            ?: return null
        return AdapterState.Syncing(progress = 0.0, blocksRemained = state.blocksRemaining)
    }

    private fun txSyncToAdapterState(): AdapterState =
        when (val txSync = evmTransactionRepository.transactionsSyncState) {
            is EthereumKit.SyncState.Synced -> AdapterState.Synced
            is EthereumKit.SyncState.Syncing -> AdapterState.Syncing()
            is EthereumKit.SyncState.NotSynced ->
                if (txSync.error is EthereumKit.SyncError.NotStarted) AdapterState.Synced
                else AdapterState.NotSynced(txSync.error)
        }

    // Decoupled from balance readiness: drives the spinner, must not block send/swap.
    override val transactionsSyncState: AdapterState
        get() = bscHistoricalSyncing() ?: forwardSyncing() ?: txSyncToAdapterState()

    override val transactionsSyncStateUpdatedFlow: Flow<Unit>
        get() = merge(
            evmTransactionRepository.transactionsSyncStateFlowable.map { }.asFlow(),
            evmTransactionRepository.historicalSyncState.map { },
            evmTransactionRepository.forwardSyncState.map { },
        )

    companion object {
        const val confirmationsThreshold: Int = 12
    }
}
