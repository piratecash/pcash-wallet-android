package cash.p.terminal.modules.amount

import cash.p.terminal.core.HSCaution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal

class SendAmountService(
    private val amountValidator: AmountValidator,
    private val coinCode: String,
    private val availableBalance: BigDecimal,
    private val leaveSomeBalanceForFee: Boolean = false
) {
    private var amount: BigDecimal? = null
    private var amountCaution: HSCaution? = null
    private var minimumSendAmount: BigDecimal? = null

    private val _stateFlow = MutableStateFlow(
        State(
            amount = amount,
            amountCaution = amountCaution,
            availableBalance = availableBalance,
            canBeSend = false,
        )
    )
    val stateFlow = _stateFlow.asStateFlow()

    private fun emitState() {
        val tmpAmount = amount
        val tmpAmountCaution = amountCaution

        val canBeSend = tmpAmount != null
            && tmpAmount > BigDecimal.ZERO
            && (tmpAmountCaution == null || tmpAmountCaution.isWarning())

        _stateFlow.update {
            State(
                amount = amount,
                amountCaution = amountCaution,
                availableBalance = availableBalance,
                canBeSend = canBeSend
            )
        }
    }

    private fun validateAmount() {
        amountCaution = amountValidator.validate(
            coinAmount = amount,
            coinCode = coinCode,
            availableBalance = availableBalance,
            minimumSendAmount = minimumSendAmount,
            leaveSomeBalanceForFee = leaveSomeBalanceForFee
        )
    }

    fun setAmount(amount: BigDecimal?) {
        this.amount = amount

        validateAmount()

        emitState()
    }

    fun setMinimumSendAmount(minimumSendAmount: BigDecimal?) {
        this.minimumSendAmount = minimumSendAmount

        validateAmount()

        emitState()
    }

    data class State(
        val amount: BigDecimal?,
        val amountCaution: HSCaution?,
        val availableBalance: BigDecimal,
        val canBeSend: Boolean
    )
}
