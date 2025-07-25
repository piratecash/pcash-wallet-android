package cash.p.terminal.modules.send.bitcoin

import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.core.adapters.BitcoinFeeInfo
import cash.p.terminal.entities.Address
import io.horizontalsystems.bitcoincore.core.IPluginData
import io.horizontalsystems.bitcoincore.storage.UnspentOutputInfo
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal

class SendBitcoinFeeService(private val adapter: ISendBitcoinAdapter) {
    private val _bitcoinFeeInfoFlow = MutableStateFlow<BitcoinFeeInfo?>(null)
    val bitcoinFeeInfoFlow = _bitcoinFeeInfoFlow.asStateFlow()

    private var bitcoinFeeInfo: BitcoinFeeInfo? = null
    private var customUnspentOutputs: List<UnspentOutputInfo>? = null

    private var amount: BigDecimal? = null
    private var validAddress: Address? = null
    private var memo: String? = null
    private var pluginData: Map<Byte, IPluginData>? = null

    private var feeRate: Int? = null
    private var dustThreshold: Int? = null
    private var changeToFirstInput = false
    private var utxoFilters = UtxoFilters()

    private fun refreshFeeInfo() {
        val tmpAmount = amount
        val tmpFeeRate = feeRate

        bitcoinFeeInfo = when {
            tmpAmount == null -> null
            tmpFeeRate == null -> null
            else -> adapter.bitcoinFeeInfo(
                tmpAmount,
                tmpFeeRate,
                validAddress?.hex,
                memo,
                customUnspentOutputs,
                pluginData,
                dustThreshold,
                changeToFirstInput,
                utxoFilters
            )
        }
    }

    private fun emitState() {
        _bitcoinFeeInfoFlow.update { bitcoinFeeInfo }
    }

    fun setAmount(amount: BigDecimal?) {
        this.amount = amount

        refreshFeeInfo()
        emitState()
    }

    fun setValidAddress(validAddress: Address?) {
        this.validAddress = validAddress

        refreshFeeInfo()
        emitState()
    }

    fun setPluginData(pluginData: Map<Byte, IPluginData>?) {
        this.pluginData = pluginData

        refreshFeeInfo()
        emitState()
    }

    fun setFeeRate(feeRate: Int?) {
        this.feeRate = feeRate

        refreshFeeInfo()
        emitState()
    }

    fun setCustomUnspentOutputs(customUnspentOutputs: List<UnspentOutputInfo>?) {
        this.customUnspentOutputs = customUnspentOutputs
        refreshFeeInfo()
        emitState()
    }

    fun setMemo(memo: String?) {
        this.memo = memo

        refreshFeeInfo()
        emitState()
    }

    fun setDustThreshold(dustThreshold: Int?) {
        this.dustThreshold = dustThreshold

        refreshFeeInfo()
        emitState()
    }

    fun setChangeToFirstInput(changeToFirstInput: Boolean) {
        this.changeToFirstInput = changeToFirstInput

        refreshFeeInfo()
        emitState()
    }

    fun setUtxoFilters(utxoFilters: UtxoFilters) {
        this.utxoFilters = utxoFilters

        refreshFeeInfo()
        emitState()
    }

}
