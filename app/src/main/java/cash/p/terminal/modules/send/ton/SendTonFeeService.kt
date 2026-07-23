package cash.p.terminal.modules.send.ton

import cash.p.terminal.core.ISendTonAdapter
import io.horizontalsystems.tonkit.FriendlyAddress
import io.tonapi.infrastructure.ClientException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.math.BigDecimal

class SendTonFeeService(
    private val adapter: ISendTonAdapter,
    private val externalScope: CoroutineScope? = null
) : Closeable {
    private var memo: String? = null
    private var address: FriendlyAddress? = null
    private var amount: BigDecimal? = null

    private var fee: FeeStatus? = null
    private var quote: TonFeeQuote? = null
    private var inProgress = false
    private val _stateFlow = MutableStateFlow(
        State(
            feeStatus = fee,
            inProgress = inProgress
        )
    )
    val stateFlow = _stateFlow.asStateFlow()
    private val coroutineScope = externalScope ?: CoroutineScope(Dispatchers.Default)
    private var estimateFeeJob: Job? = null

    private fun refreshFeeAndEmitState() {
        val amount = amount
        val address = address
        val memo = memo

        estimateFeeJob?.cancel()
        estimateFeeJob = coroutineScope.launch(Dispatchers.Default) {
            if (amount != null && address != null) {
                inProgress = true
                emitState()

                delay(1000)
                ensureActive()
                try {
                    val estimated = adapter.estimateFee(amount, address, memo)
                    fee = FeeStatus.Success(estimated)
                    quote = TonFeeQuote(estimated, amount, address, memo)
                } catch (e: Throwable) {
                    if (e is ClientException) {
                        fee = FeeStatus.NoEnoughBalance
                    }
                    e.printStackTrace()
                    delay(500)
                    refreshFeeAndEmitState()
                }
            } else {
                fee = null
                quote = null
            }

            inProgress = false
            emitState()
        }
    }

    fun setAmount(amount: BigDecimal?) {
        this.amount = amount

        refreshFeeAndEmitState()
    }

    fun setTonAddress(address: FriendlyAddress?) {
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
                inProgress = inProgress,
                quote = quote
            )
        }
    }


    data class State(
        val feeStatus: FeeStatus?,
        val inProgress: Boolean,
        val quote: TonFeeQuote? = null
    )

    override fun close() {
        if (externalScope != null) return // external scope should be closed externally
        coroutineScope.cancel()
    }
}

/**
 * A successful fee estimate together with the exact inputs it was computed for.
 * Offline signing reuses the fee only when the current inputs still match.
 */
data class TonFeeQuote(
    val fee: BigDecimal,
    val amount: BigDecimal,
    val address: FriendlyAddress,
    val memo: String?,
) {
    fun matches(amount: BigDecimal, address: FriendlyAddress, memo: String?): Boolean =
        this.amount.compareTo(amount) == 0 &&
                this.address.matches(address) &&
                this.memo == memo
}

// FriendlyAddress has no equals; compare the canonical raw form plus bounceability.
private fun FriendlyAddress.matches(other: FriendlyAddress): Boolean =
    isBounceable == other.isBounceable &&
            addrStd.toString(userFriendly = false) == other.addrStd.toString(userFriendly = false)
