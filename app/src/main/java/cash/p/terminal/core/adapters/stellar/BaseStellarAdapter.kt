package cash.p.terminal.core.adapters.stellar

import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.ISendStellarAdapter
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineSignRequest
import cash.p.terminal.core.OfflineStellarSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.OfflineTransactionStatusAdapter
import cash.p.terminal.core.SignedOfflineStellarTransaction
import cash.p.terminal.core.canonicalTransactionHash
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.core.managers.StellarKitWrapper
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import io.horizontalsystems.stellarkit.models.RawTransactionBroadcastStatus
import io.horizontalsystems.stellarkit.models.RawTransactionRetryMetadata
import io.horizontalsystems.stellarkit.models.SignedRawStellarTransaction
import java.math.BigDecimal

abstract class BaseStellarAdapter(
    stellarKitWrapper: StellarKitWrapper
) : IAdapter,
    IBalanceAdapter,
    IReceiveAdapter,
    ISendStellarAdapter,
    OfflineTransactionAdapter<SignedOfflineStellarTransaction>,
    OfflineTransactionStatusAdapter {
    protected val stellarKit = stellarKitWrapper.stellarKit
    override val receiveAddress: String = stellarKit.receiveAddress
    override val sendFee: BigDecimal = stellarKit.sendFee

    override val debugInfo: String
        get() = ""

    // IReceiveAdapter

    override val isMainNet = stellarKit.isMainNet

    override suspend fun signOffline(request: OfflineSignRequest): SignedOfflineStellarTransaction {
        require(request is OfflineStellarSignRequest) { "OfflineStellarSignRequest is required" }
        val signed = signedTransaction(
            amount = request.amount,
            address = request.address,
            memo = request.memo,
        )
        return SignedOfflineStellarTransaction(
            rawHex = signed.raw.toRawHexString(),
            txHash = signed.txHash.canonicalTransactionHash(),
            fee = sendFee,
            sourceAccountId = signed.sourceAccountId,
            sequenceNumber = signed.sequenceNumber,
            validUntil = signed.validUntil,
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
        val result = stellarKit.broadcastRawTransaction(
            rawTransaction = normalizedRawHex.hexToByteArray(),
            retryMetadata = (metadata as? OfflineBroadcastMetadata.Stellar)?.toStellarRetryMetadata(),
        )
        return BroadcastRawTransactionResult(
            txHash = result.txHash.canonicalTransactionHash(),
            status = result.status.toAppStatus(),
        )
    }

    override suspend fun transactionExists(txHash: String): Boolean =
        stellarKit.transactionExists(txHash.canonicalTransactionHash())

    protected abstract suspend fun signedTransaction(
        amount: BigDecimal,
        address: String,
        memo: String?,
    ): SignedRawStellarTransaction
}

private fun OfflineBroadcastMetadata.Stellar.toStellarRetryMetadata() = RawTransactionRetryMetadata(
    sourceAccountId = sourceAccountId,
    sequenceNumber = sequenceNumber,
    validUntil = validUntil,
)

private fun RawTransactionBroadcastStatus.toAppStatus(): BroadcastRawTransactionStatus =
    when (this) {
        RawTransactionBroadcastStatus.Submitted -> BroadcastRawTransactionStatus.Submitted
        RawTransactionBroadcastStatus.Queued -> BroadcastRawTransactionStatus.Queued
        RawTransactionBroadcastStatus.AlreadyKnown -> BroadcastRawTransactionStatus.AlreadyKnown
    }
