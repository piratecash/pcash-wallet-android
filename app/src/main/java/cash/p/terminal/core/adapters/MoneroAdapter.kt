package cash.p.terminal.core.adapters

import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.ISendMoneroAdapter
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineMoneroSignRequest
import cash.p.terminal.core.OfflineSignRequest
import cash.p.terminal.core.SignedOfflineMoneroTransaction
import cash.p.terminal.core.canonicalTransactionHash
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.core.managers.MoneroKitWrapper
import cash.p.terminal.core.managers.MoneroSubaddressInfo
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.entities.BalanceData
import com.m2049r.xmrwallet.offline.RawMoneroBroadcastResult
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal

class MoneroAdapter(
    private val moneroKitWrapper: MoneroKitWrapper,
) : IAdapter, IBalanceAdapter, IReceiveAdapter, ISendMoneroAdapter {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var collectJob: Job? = null
    private val _fee = MutableStateFlow(BigDecimal.ZERO)
    override val fee: StateFlow<BigDecimal> = _fee.asStateFlow()

    override val maxSpendableBalance: BigDecimal
        get() = maxOf(unlockedBalance - fee.value, BigDecimal.ZERO)

    private val unlockedBalance: BigDecimal
        get() = balanceInBigDecimal(moneroKitWrapper.getUnlockedBalance(), decimal)

    override val debugInfo: String
        get() = "Monero wallet: ${moneroKitWrapper.statusInfo()}"

    override val receiveAddress: String
        get() = moneroKitWrapper.getAddress()

    override val isMainNet: Boolean = true

    override val isAddressHistorySupported: Boolean = true

    suspend fun getSubaddresses(): List<MoneroSubaddressInfo> =
        moneroKitWrapper.getSubaddresses()

    suspend fun createNewSubaddress(): String =
        moneroKitWrapper.createNewSubaddress()

    override val statusInfo: Map<String, Any>
        get() = moneroKitWrapper.statusInfo()

    // IAdapter

    override fun start() {
        collectJob?.cancel()
        collectJob = coroutineScope.launch {
            moneroKitWrapper.syncState.collect { state ->
                if (state is AdapterState.Synced) {
                    estimateFeeForMax()
                }
            }
        }
    }

    override fun stop() {
        collectJob?.cancel()
        collectJob = null
    }

    private suspend fun estimateFeeForMax() {
        tryOrNull {
            val address = AppConfigProvider.donateAddresses[BlockchainType.Monero] ?: return@tryOrNull
            val amount = maxOf(unlockedBalance, BigDecimal.ONE.movePointLeft(12))
            _fee.value = estimateFee(amount, address, null)
        }
    }

    override suspend fun refresh() {
        // Refresh Monero wallet data
        moneroKitWrapper.refresh()
    }

    // IBalanceAdapter

    override val balanceState: AdapterState
        get() = moneroKitWrapper.syncState.value

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = moneroKitWrapper.syncState.map { }

    override val balanceData: BalanceData
        get() = BalanceData(balanceInBigDecimal(moneroKitWrapper.getBalance(), decimal))

    override val balanceUpdatedFlow: Flow<Unit>
        get() = moneroKitWrapper.syncState.map { }

    override suspend fun send(
        amount: BigDecimal,
        address: String,
        memo: String?
    ): String = moneroKitWrapper.send(amount, address, memo)

    override suspend fun estimateFee(
        amount: BigDecimal,
        address: String,
        memo: String?
    ): BigDecimal {
        return moneroKitWrapper.estimateFee(amount, address, memo).toBigDecimal()
            .movePointLeft(decimal)
    }

    override suspend fun signOffline(request: OfflineSignRequest): SignedOfflineMoneroTransaction {
        require(request is OfflineMoneroSignRequest) { "OfflineMoneroSignRequest is required" }
        val signed = moneroKitWrapper.createSignedRawTransaction(
            amount = request.amount,
            address = request.address,
            memo = request.memo,
        )
        return SignedOfflineMoneroTransaction(
            rawHex = signed.raw.toRawHexString(),
            txHash = signed.txId.canonicalTransactionHash(),
            fee = signed.fee.toBigDecimal().movePointLeft(decimal),
        )
    }

    override suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata?,
    ): BroadcastRawTransactionResult {
        require(metadata == null) { "Monero offline broadcast does not support retry metadata" }
        val normalizedRawHex = rawTransactionHex.trim()
        require(OfflineTransactionPayloadEncoder.isRawTransactionHex(normalizedRawHex)) {
            "Valid raw transaction hex is required"
        }
        val result = moneroKitWrapper.submitSignedRawTransaction(normalizedRawHex.hexToByteArray())
        val status = when (result) {
            is RawMoneroBroadcastResult.Submitted -> BroadcastRawTransactionStatus.Submitted
            is RawMoneroBroadcastResult.AlreadyKnown -> BroadcastRawTransactionStatus.AlreadyKnown
        }
        return BroadcastRawTransactionResult(
            txHash = result.txId.canonicalTransactionHash(),
            status = status,
        )
    }

    companion object {
        const val decimal = 12 // Monero has 12 decimal places

        private fun scaleDown(amount: BigDecimal, decimals: Int = decimal): BigDecimal {
            return amount.movePointLeft(decimals).stripTrailingZeros()
        }

        fun balanceInBigDecimal(balance: Long?, decimal: Int): BigDecimal {
            return balance?.toBigDecimal()?.let {
                scaleDown(it, decimal)
            } ?: BigDecimal.ZERO
        }
    }
}
