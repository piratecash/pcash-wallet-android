package cash.p.terminal.core.adapters

import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.core.App
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.managers.TronKitWrapper
import cash.p.terminal.core.tryOrNull
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.transaction.Fee
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class TronAdapter(kitWrapper: TronKitWrapper) : BaseTronAdapter(kitWrapper, decimal), ISendTronAdapter {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val _fee = MutableStateFlow(BigDecimal.ZERO)
    override val fee: StateFlow<BigDecimal> = _fee.asStateFlow()

    override val maxSpendableBalance: BigDecimal
        get() = maxOf(balanceData.available - fee.value, BigDecimal.ZERO)

    // IAdapter

    override fun start() {
        coroutineScope.launch {
            tronKit.trxBalanceFlow.collect {
                estimateFeeForMax()
            }
        }
    }

    override fun stop() {
        coroutineScope.cancel()
    }

    override suspend fun refresh() {
        // refreshed via TronKitManager
    }

    // IBalanceAdapter

    override val balanceState: AdapterState
        get() = convertToAdapterState(tronKit.syncState)

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = tronKit.syncStateFlow.map {}

    override val balanceData: BalanceData
        get() = BalanceData(balanceInBigDecimal(tronKit.trxBalance, decimal))

    override val balanceUpdatedFlow: Flow<Unit>
        get() = tronKit.trxBalanceFlow.map {}

    // ISendTronAdapter

    override val trxBalanceData: BalanceData
        get() = balanceData

    override suspend fun estimateFee(amount: BigDecimal, to: Address): List<Fee> = withContext(Dispatchers.IO) {
        val amountBigInt = amount.movePointRight(decimal).toBigInteger()
        val contract = tronKit.transferContract(amountBigInt, to)
         tronKit.estimateFee(contract)
    }

    override suspend fun send(amount: BigDecimal, to: Address, feeLimit: Long?): String {
        if (signer == null) throw Exception()
        val amountBigInt = amount.movePointRight(decimal).toBigInteger()
        val contract = tronKit.transferContract(amountBigInt, to)

        return tronKit.send(contract, signer, feeLimit)
    }

    private suspend fun estimateFeeForMax() {
        if (balanceData.available <= BigDecimal.ZERO) {
            _fee.value = BigDecimal.ZERO
            return
        }
        tryOrNull {
            val fees = estimateFee(balanceData.available, tronKit.address)
            _fee.value = fees.sumOf { it.feeInSuns }.toBigDecimal().movePointLeft(decimal).stripTrailingZeros()
        }
    }

    private fun convertToAdapterState(syncState: TronKit.SyncState): AdapterState =
        when (syncState) {
            is TronKit.SyncState.Synced -> AdapterState.Synced
            is TronKit.SyncState.NotSynced -> AdapterState.NotSynced(syncState.error)
            is TronKit.SyncState.Syncing -> AdapterState.Syncing()
        }

    companion object {
        const val decimal = 6

        fun clear(walletId: String) {
            Network.values().forEach { network ->
                TronKit.clear(App.instance, network, walletId)
            }
        }
    }

}
