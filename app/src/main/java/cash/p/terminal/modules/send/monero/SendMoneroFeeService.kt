package cash.p.terminal.modules.send.monero

import cash.p.terminal.core.ISendMoneroAdapter
import cash.p.terminal.modules.send.ton.FeeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable
import java.math.BigDecimal

class SendMoneroFeeService(
    private val adapter: ISendMoneroAdapter,
    private val coroutineScope: CoroutineScope
) : Closeable {
    private var memo: String? = null
    private var address: String? = null
    private var amount: BigDecimal? = null

    private var fee: FeeStatus? = null
    private var inProgress = false
    private val _stateFlow = MutableStateFlow(
        State(
            feeStatus = fee,
            inProgress = inProgress
        )
    )
    val stateFlow = _stateFlow.asStateFlow()
    private var estimateFeeJob: Job? = null

    private fun refreshFeeAndEmitState() {
        val amount = amount
        val address = address
        val memo = memo

        estimateFeeJob?.cancel()
        estimateFeeJob = coroutineScope.launch(Dispatchers.Default) {
            if (amount != null && amount > BigDecimal.ZERO && address != null) {
                inProgress = true
                emitState()

                try {
                    fee = FeeStatus.Success(adapter.estimateFee(amount, address, memo))
                } catch (e: Throwable) {
                    Timber.e(e, "Monero fee estimation failed")
                    fee = FeeStatus.NoEnoughBalance
                }
            } else {
                fee = null
            }

            inProgress = false
            emitState()
        }
    }

    fun setAmount(amount: BigDecimal?) {
        this.amount = amount
        refreshFeeAndEmitState()
    }

    fun setAddress(address: String?) {
        this.address = address
        refreshFeeAndEmitState()
    }

    fun setMemo(memo: String?) {
        this.memo = memo
        refreshFeeAndEmitState()
    }

    private fun emitState() {
        _stateFlow.update {
            State(
                feeStatus = fee,
                inProgress = inProgress
            )
        }
    }

    data class State(
        val feeStatus: FeeStatus?,
        val inProgress: Boolean
    )

    override fun close() {
        coroutineScope.cancel()
    }
}
