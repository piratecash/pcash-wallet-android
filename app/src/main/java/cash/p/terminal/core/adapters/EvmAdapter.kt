package cash.p.terminal.core.adapters

import cash.p.terminal.core.App
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.BalanceData
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.TransactionData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal

internal class EvmAdapter(evmTransactionRepository: EvmTransactionRepository, coinManager: ICoinManager) :
    BaseEvmAdapter(evmTransactionRepository, decimal, coinManager) {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val _fee = MutableStateFlow(BigDecimal.ZERO)
    override val fee: StateFlow<BigDecimal> = _fee.asStateFlow()

    override val maxSpendableBalance: BigDecimal
        get() = maxOf(balanceData.available - fee.value, BigDecimal.ZERO)

    // IAdapter

    override fun start() {
        coroutineScope.launch {
            evmTransactionRepository.accountStateFlowable.asFlow().collect {
                estimateFeeForMax()
            }
        }
        // Initial fee estimation
        coroutineScope.launch {
            estimateFeeForMax()
        }
    }

    override fun stop() {
        coroutineScope.cancel()
    }

    override suspend fun refresh() {
        // refreshed via EthereumKitManager
    }

    private suspend fun estimateFeeForMax() {
        tryOrNull {
            val feeInWei = evmTransactionRepository.estimateNativeTransferFee()
            _fee.value = feeInWei.toBigDecimal().movePointLeft(decimal).stripTrailingZeros()
        }
    }

    // IBalanceAdapter

    override val balanceState: AdapterState
        get() = balanceSyncStateToAdapterState(evmTransactionRepository.syncState)

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = evmTransactionRepository.syncStateFlowable.map { }.asFlow()

    override val balanceData: BalanceData
        get() = BalanceData(balanceInBigDecimal(evmTransactionRepository.accountState?.balance, decimal))

    override val balanceUpdatedFlow: Flow<Unit>
        get() = evmTransactionRepository.accountStateFlowable.map { }.asFlow()

    private fun balanceSyncStateToAdapterState(syncState: EthereumKit.SyncState): AdapterState =
        when (syncState) {
            is EthereumKit.SyncState.Synced -> AdapterState.Synced
            is EthereumKit.SyncState.Syncing -> AdapterState.Syncing()
            is EthereumKit.SyncState.NotSynced ->
                if (syncState.error is EthereumKit.SyncError.NotStarted) AdapterState.Connecting
                else AdapterState.NotSynced(syncState.error)
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
