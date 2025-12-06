package cash.p.terminal.modules.send.bitcoin

import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import io.horizontalsystems.bitcoincore.core.IPluginData
import io.horizontalsystems.bitcoincore.storage.UnspentOutputInfo
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal

class SendBitcoinAmountService(
    private val adapter: ISendBitcoinAdapter,
    private val coinCode: String,
    private val amountValidator: AmountValidator
) {
    private var amount: BigDecimal? = null
    private var customUnspentOutputs: List<UnspentOutputInfo>? = null
    private var amountCaution: HSCaution? = null

    private var minimumSendAmount: BigDecimal? = null
    private var userMinimumSendAmount: BigDecimal? = null
    private var availableBalance: BigDecimal? = null
    private var validAddress: Address? = null
    private var memo: String? = null
    private var feeRate: Int? = null
    private var pluginData: Map<Byte, IPluginData>? = null

    private var changeToFirstInput = false
    private var utxoFilters = UtxoFilters()

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

        val canBeSend = availableBalance != null
                && tmpAmount != null && tmpAmount > BigDecimal.ZERO
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

    private fun refreshAvailableBalance() {
        availableBalance = feeRate?.let {
            adapter.availableBalance(
                it,
                validAddress?.hex,
                memo,
                customUnspentOutputs,
                pluginData,
                changeToFirstInput,
                utxoFilters
            )
        }
    }

    private fun refreshMinimumSendAmount() {
        minimumSendAmount = adapter.minimumSendAmount(validAddress?.hex)
    }

    private fun validateAmount() {
        availableBalance?.let {
            val mins = listOfNotNull(minimumSendAmount, userMinimumSendAmount)
            amountCaution = amountValidator.validate(
                amount,
                coinCode,
                it,
                mins.maxOrNull(),
            )
        }
    }

    fun setAmount(amount: BigDecimal?, forceEmit: Boolean = true) {
        this.amount = amount

        validateAmount()

        if (forceEmit) {
            emitState()
        }
    }

    fun setValidAddress(validAddress: Address?) {
        this.validAddress = validAddress

        refreshAvailableBalance()
        refreshMinimumSendAmount()
        validateAmount()

        emitState()
    }

    fun setFeeRate(feeRate: Int?) {
        this.feeRate = feeRate

        refreshAvailableBalance()
        validateAmount()

        emitState()
    }

    fun setPluginData(pluginData: Map<Byte, IPluginData>?) {
        this.pluginData = pluginData

        refreshAvailableBalance()
        validateAmount()

        emitState()
    }

    fun setUserMinimumSendAmount(userMinimumSendAmount: Int?, forceEmit: Boolean = true) {
        this.userMinimumSendAmount = userMinimumSendAmount?.let {
            adapter.satoshiToBTC(it.toLong())
        }

        validateAmount()

        if (forceEmit) {
            emitState()
        }
    }

    fun setChangeToFirstInput(changeToFirstInput: Boolean, forceEmit: Boolean = true) {
        this.changeToFirstInput = changeToFirstInput

        refreshAvailableBalance()
        validateAmount()

        if (forceEmit) {
            emitState()
        }
    }

    fun setUtxoFilters(utxoFilters: UtxoFilters, forceEmit: Boolean = true) {
        this.utxoFilters = utxoFilters

        refreshAvailableBalance()
        validateAmount()

        if (forceEmit) {
            emitState()
        }
    }

    fun setCustomUnspentOutputs(customUnspentOutputs: List<UnspentOutputInfo>?) {
        this.customUnspentOutputs = customUnspentOutputs
        refreshAvailableBalance()
        validateAmount()
        emitState()
    }

    fun setMemo(memo: String?, forceEmit: Boolean = true) {
        this.memo = memo

        refreshAvailableBalance()
        validateAmount()

        if (forceEmit) {
            emitState()
        }
    }

    data class State(
        val amount: BigDecimal?,
        val amountCaution: HSCaution?,
        val availableBalance: BigDecimal?,
        val canBeSend: Boolean
    )
}
