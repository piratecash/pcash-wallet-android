package cash.p.terminal.modules.evmfee

import cash.p.terminal.core.EvmError
import cash.p.terminal.core.convertedError
import cash.p.terminal.modules.transactionInfo.TransactionViewItemFactoryHelper
import cash.p.terminal.ui_compose.entities.DataState
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.TransactionData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.math.BigInteger

class EvmFeeService(
    private val evmKit: EthereumKit,
    private val gasPriceService: IEvmGasPriceService,
    private val gasDataService: EvmCommonGasDataService,
    private var transactionData: TransactionData? = null,
) : IEvmFeeService {

    private var gasLimit: Long? = null
    private var gasPriceInfoState: DataState<GasPriceInfo> = DataState.Loading
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gasPriceInfoJob: Job? = null

    private val evmBalance: BigInteger
        get() = evmKit.accountState?.balance ?: BigInteger.ZERO

    private val _transactionStatusFlow: MutableSharedFlow<DataState<Transaction>> =
        MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val transactionStatusFlow = _transactionStatusFlow.asSharedFlow()

    fun start() {
        coroutineScope.launch {
            gasPriceService.stateFlow.collect {
                gasPriceInfoState = it
                sync()
            }
        }
    }

    override fun reset() {
        gasPriceService.setRecommended()
    }

    override fun clear() {
        coroutineScope.cancel()
    }

    private fun sync() {
        when (val gasPriceInfoState = gasPriceInfoState) {
            is DataState.Error -> {
                _transactionStatusFlow.tryEmit(gasPriceInfoState)
            }

            DataState.Loading -> {
                _transactionStatusFlow.tryEmit(DataState.Loading)
            }

            is DataState.Success -> {
                sync(gasPriceInfoState.data)
            }
        }
    }

    private fun sync(gasPriceInfo: GasPriceInfo) {
        gasPriceInfoJob?.cancel()
        val transactionData = transactionData

        if (transactionData != null) {
            gasPriceInfoJob = coroutineScope.launch {
                try {
                    val transaction = feeDataSingle(gasPriceInfo, transactionData)
                    sync(transaction)
                } catch (e: CancellationException) {
                    // do nothing
                } catch (e: Throwable) {
                    _transactionStatusFlow.tryEmit(DataState.Error(e))
                }
            }
        } else {
            _transactionStatusFlow.tryEmit(DataState.Loading)
        }
    }

    private suspend fun feeDataSingle(
        gasPriceInfo: GasPriceInfo,
        transactionData: TransactionData
    ): Transaction {
        val gasPrice = gasPriceInfo.gasPrice
        val gasPriceDefault = gasPriceInfo.gasPriceDefault
        val default = gasPriceInfo.default
        val warnings = gasPriceInfo.warnings
        val errors = gasPriceInfo.errors

        return if (transactionData.input.isEmpty() && transactionData.value == evmBalance) {
            val gasData = gasDataSingle(gasPrice, gasPriceDefault, BigInteger.ONE, transactionData)
            val adjustedValue = transactionData.value - gasData.fee
            if (adjustedValue <= BigInteger.ZERO) {
                throw FeeSettingsError.InsufficientBalance
            } else {
                val transactionDataAdjusted =
                    TransactionData(transactionData.to, adjustedValue, byteArrayOf())
                val allWarnings = warnings + listOfNotNull(gasData.warning)
                Transaction(transactionDataAdjusted, gasData, default, allWarnings, errors)
            }
        } else {
            val gasData = gasDataSingle(gasPrice, gasPriceDefault, null, transactionData)
            val allWarnings = warnings + listOfNotNull(gasData.warning)
            Transaction(transactionData, gasData, default, allWarnings, errors)
        }
    }

    private suspend fun gasDataSingle(
        gasPrice: GasPrice,
        gasPriceDefault: GasPrice,
        stubAmount: BigInteger? = null,
        transactionData: TransactionData
    ): GasData {
        val gasLimit = gasLimit

        if (gasLimit != null) {
            return GasData(gasLimit = gasLimit, gasPrice = gasPrice)
        }

        return try {
            gasDataService.estimatedGasDataAsync(
                gasPrice,
                transactionData,
                stubAmount
            ).await()
        } catch (error: Throwable) {
            if (error.convertedError == EvmError.LowerThanBaseGasLimit) {
                val gasData = gasDataService.estimatedGasDataAsync(
                    gasPriceDefault,
                    transactionData,
                    stubAmount
                ).await()
                gasData.gasPrice = gasPrice
                gasData
            } else if (error is io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc.ResponseError.RpcError) {
                return if (error.error.code == 3) { // Execution reverted
                    gasDataService.estimatedGasDataAsync(
                        gasPrice = gasPrice,
                        transactionData = transactionData,
                        stubAmount = stubAmount,
                        toAddress = Address(TransactionViewItemFactoryHelper.zeroAddress)
                    ).await().apply {
                        this.warning = CalculateWarning.Reverted
                    }
                } else {
                    throw error
                }
            } else {
                throw error
            }
        }
    }

    private fun sync(transaction: Transaction) {
        _transactionStatusFlow.tryEmit(
            if (transaction.totalAmount > evmBalance) {
                DataState.Success(transaction.copy(errors = transaction.errors + FeeSettingsError.InsufficientBalance))
            } else {
                DataState.Success(transaction)
            }
        )
    }

    fun setGasLimit(gasLimit: Long?) {
        this.gasLimit = gasLimit
        sync()
    }

    fun setTransactionData(transactionData: TransactionData) {
        this.transactionData = transactionData
        sync()
    }

}
