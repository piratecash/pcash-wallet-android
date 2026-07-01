package cash.p.terminal.core.adapters

import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.OfflineTronSignRequest
import cash.p.terminal.core.SignedOfflineTronTransaction
import cash.p.terminal.core.canonicalTransactionHash
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.TronKitWrapper
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.models.RawTransactionBroadcastStatus
import io.horizontalsystems.tronkit.models.RawTransactionRetryMetadata
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.transaction.Fee
import io.horizontalsystems.tronkit.transaction.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

abstract class BaseTronAdapter(
    tronKitWrapper: TronKitWrapper,
    val decimal: Int
) : IAdapter,
    IBalanceAdapter,
    IReceiveAdapter,
    ISendTronAdapter,
    OfflineTransactionAdapter<SignedOfflineTronTransaction> {

    val tronKit = tronKitWrapper.tronKit
    protected val signer: Signer? = tronKitWrapper.signer

    override val debugInfo: String
        get() = ""

    override val statusInfo: Map<String, Any>
        get() = tronKit.statusInfo()

    // IReceiveAdapter

    override suspend fun isAddressActive(address: String): Boolean {
        val tronAddress = Address.fromBase58(address)
        return tronKit.isAccountActive(tronAddress)
    }

    override val receiveAddress: String
        get() = tronKit.address.base58

    override val isMainNet: Boolean
        get() = tronKit.network == Network.Mainnet

    // ISendTronAdapter

    override suspend fun estimateFee(contract: Contract): List<Fee> = withContext(Dispatchers.IO) {
        tronKit.estimateFee(contract)
    }

    override suspend fun estimateFee(amount: BigDecimal, to: Address): List<Fee> =
        estimateFee(transferContract(amount, to))

    override suspend fun estimateFee(transaction: CreatedTransaction): List<Fee> = withContext(Dispatchers.IO) {
        tronKit.estimateFee(transaction)
    }

    override suspend fun send(amount: BigDecimal, to: Address, feeLimit: Long?): String =
        send(transferContract(amount, to), feeLimit)

    override suspend fun send(contract: Contract, feeLimit: Long?): String {
        val signer = signer ?: throw TronSignerNotInitializedException()

        return tronKit.send(contract, signer, feeLimit)
    }

    override suspend fun send(createdTransaction: CreatedTransaction): String {
        val signer = signer ?: throw TronSignerNotInitializedException()

        return tronKit.send(createdTransaction, signer)
    }

    override suspend fun signOffline(request: OfflineSignRequest): SignedOfflineTronTransaction {
        require(request is OfflineTronSignRequest) { "OfflineTronSignRequest is required" }
        val signer = signer ?: throw TronSignerNotInitializedException()
        val signed = tronKit.signedTransaction(
            contract = transferContract(request.amount, request.address),
            signer = signer,
            feeLimit = request.feeLimit,
        )
        return SignedOfflineTronTransaction(
            rawHex = signed.raw.toRawHexString(),
            txHash = signed.txId.canonicalTransactionHash(),
            expiration = signed.expiration,
        )
    }

    override suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata?,
    ): BroadcastRawTransactionResult {
        val normalizedRawHex = rawTransactionHex.trim()
        require(OfflineTransactionPayloadEncoder.isRawTransactionHex(normalizedRawHex)) {
            "Valid raw transaction hex is required"
        }
        val result = tronKit.broadcastRawTransaction(
            rawTransaction = normalizedRawHex.hexToByteArray(),
            retryMetadata = (metadata as? OfflineBroadcastMetadata.Tron)?.toTronRetryMetadata(),
        )
        return BroadcastRawTransactionResult(
            txHash = result.txId.canonicalTransactionHash(),
            status = result.status.toAppStatus(),
        )
    }

    override suspend fun isAddressActive(address: Address): Boolean = withContext(Dispatchers.IO) {
        tronKit.isAccountActive(address)
    }

    override fun isOwnAddress(address: Address): Boolean {
        return address == tronKit.address
    }

    protected fun balanceInBigDecimal(balance: BigInteger?, decimal: Int): BigDecimal {
        balance?.toBigDecimal()?.let {
            return scaleDown(it, decimal)
        } ?: return BigDecimal.ZERO
    }

    protected fun scaleDown(amount: BigDecimal, decimals: Int = decimal): BigDecimal {
        return amount.movePointLeft(decimals).stripTrailingZeros()
    }

    protected fun scaleUp(amount: BigDecimal, decimals: Int = decimal): BigInteger {
        return amount.movePointRight(decimals).toBigInteger()
    }

    protected abstract fun transferContract(amount: BigDecimal, to: Address): Contract

    companion object {
        const val confirmationsThreshold: Int = 19
    }

}

internal class TronSignerNotInitializedException : IllegalStateException("Tron signer is not initialized")

private fun OfflineBroadcastMetadata.Tron.toTronRetryMetadata() = RawTransactionRetryMetadata(
    expiration = expiration,
)

private fun RawTransactionBroadcastStatus.toAppStatus(): BroadcastRawTransactionStatus =
    when (this) {
        RawTransactionBroadcastStatus.Submitted -> BroadcastRawTransactionStatus.Submitted
        RawTransactionBroadcastStatus.Queued -> BroadcastRawTransactionStatus.Queued
        RawTransactionBroadcastStatus.AlreadyKnown -> BroadcastRawTransactionStatus.AlreadyKnown
    }
