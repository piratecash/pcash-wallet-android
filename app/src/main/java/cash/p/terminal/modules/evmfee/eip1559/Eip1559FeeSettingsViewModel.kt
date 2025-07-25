package cash.p.terminal.modules.evmfee.eip1559

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.ethereum.EvmCoinService
import cash.p.terminal.core.feePriceScale
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.entities.FeePriceScale
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.evmfee.FeeSummaryViewItem
import cash.p.terminal.modules.evmfee.FeeViewItem
import cash.p.terminal.modules.evmfee.GasPriceInfo
import cash.p.terminal.modules.evmfee.IEvmFeeService
import cash.p.terminal.modules.evmfee.Transaction
import cash.p.terminal.modules.fee.FeeItem
import io.horizontalsystems.ethereumkit.models.GasPrice
import kotlinx.coroutines.launch

class Eip1559FeeSettingsViewModel(
    private val gasPriceService: Eip1559GasPriceService,
    feeService: IEvmFeeService,
    private val coinService: EvmCoinService
) : ViewModel() {

    private val scale = coinService.token.blockchainType.feePriceScale

    var feeSummaryViewItem by mutableStateOf<FeeSummaryViewItem?>(null)
        private set

    var currentBaseFee by mutableStateOf<String?>(null)
        private set

    var maxFeeViewItem by mutableStateOf<FeeViewItem?>(null)
        private set

    var priorityFeeViewItem by mutableStateOf<FeeViewItem?>(null)
        private set

    init {
        viewModelScope.launch {
            gasPriceService.stateFlow.collect {
                sync(it)
            }
        }

        viewModelScope.launch {
            feeService.transactionStatusFlow.collect {
                syncTransactionStatus(it)
            }
        }
    }

    fun onSelectGasPrice(maxFee: Long, priorityFee: Long) {
        gasPriceService.setGasPrice(maxFee, priorityFee)
    }

    fun onIncrementMaxFee(maxFee: Long, priorityFee: Long) {
        gasPriceService.setGasPrice(maxFee + scale.scaleValue, priorityFee)
    }

    fun onDecrementMaxFee(maxFee: Long, priorityFee: Long) {
        gasPriceService.setGasPrice((maxFee - scale.scaleValue).coerceAtLeast(0), priorityFee)
    }

    fun onIncrementPriorityFee(maxFee: Long, priorityFee: Long) {
        gasPriceService.setGasPrice(maxFee, priorityFee + scale.scaleValue)
    }

    fun onDecrementPriorityFee(maxFee: Long, priorityFee: Long) {
        gasPriceService.setGasPrice(maxFee, (priorityFee - scale.scaleValue).coerceAtLeast(0))
    }

    private fun sync(state: DataState<GasPriceInfo>) {
        sync(gasPriceService.currentBaseFee)

        state.dataOrNull?.let { gasPriceInfo ->
            if (gasPriceInfo.gasPrice is GasPrice.Eip1559) {
                maxFeeViewItem = FeeViewItem(
                    weiValue = gasPriceInfo.gasPrice.maxFeePerGas,
                    scale = scale,
                    warnings = gasPriceInfo.warnings,
                    errors = gasPriceInfo.errors
                )
                priorityFeeViewItem = FeeViewItem(
                    weiValue = gasPriceInfo.gasPrice.maxPriorityFeePerGas,
                    scale = scale,
                    warnings = gasPriceInfo.warnings,
                    errors = gasPriceInfo.errors
                )
            }
        }
    }

    private fun scaledString(wei: Long, scale: FeePriceScale): String {
        val gwei = wei.toDouble() / scale.scaleValue
        return "${gwei.toBigDecimal().toPlainString()} ${scale.unit}"
    }

    private fun sync(baseFee: Long?) {
        currentBaseFee = if (baseFee != null) {
            scaledString(baseFee, coinService.token.blockchainType.feePriceScale)
        } else {
            cash.p.terminal.strings.helpers.Translator.getString(R.string.NotAvailable)
        }
    }

    private fun syncTransactionStatus(transactionStatus: DataState<Transaction>) {
        syncFeeViewItems(transactionStatus)
    }

    private fun syncFeeViewItems(transactionStatus: DataState<Transaction>) {
        val notAvailable = cash.p.terminal.strings.helpers.Translator.getString(R.string.NotAvailable)
        when (transactionStatus) {
            DataState.Loading -> {
                feeSummaryViewItem = FeeSummaryViewItem(null, notAvailable, ViewState.Loading)
            }
            is DataState.Error -> {
                feeSummaryViewItem = FeeSummaryViewItem(null, notAvailable, ViewState.Error(transactionStatus.error))
            }
            is DataState.Success -> {
                val transaction = transactionStatus.data
                val viewState = transaction.errors.firstOrNull()?.let { ViewState.Error(it) } ?: ViewState.Success
                val feeAmountData = coinService.amountData(transactionStatus.data.gasData.estimatedFee, transactionStatus.data.gasData.isSurcharged)
                val feeItem = FeeItem(feeAmountData.primary.getFormattedPlain(), feeAmountData.secondary?.getFormattedPlain())
                val gasLimit = App.numberFormatter.format(transactionStatus.data.gasData.gasLimit.toBigDecimal(), 0, 0)

                feeSummaryViewItem = FeeSummaryViewItem(feeItem, gasLimit, viewState)
            }
        }
    }
}
