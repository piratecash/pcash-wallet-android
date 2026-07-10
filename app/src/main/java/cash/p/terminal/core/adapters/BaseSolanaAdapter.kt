package cash.p.terminal.core.adapters

import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.ISendSolanaAdapter
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineSolanaTransaction
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.core.managers.SolanaKitWrapper
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import io.horizontalsystems.solanakit.Signer
import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.solanakit.models.RawTransactionBroadcastStatus
import io.horizontalsystems.solanakit.models.RawTransactionRetryMetadata
import java.math.BigDecimal

abstract class BaseSolanaAdapter(
        solanaKitWrapper: SolanaKitWrapper,
        val decimal: Int
) : IAdapter,
    IBalanceAdapter,
    IReceiveAdapter,
    ISendSolanaAdapter,
    OfflineTransactionAdapter<SignedOfflineSolanaTransaction> {

    val solanaKit = solanaKitWrapper.solanaKit
    protected val signer: Signer? = solanaKitWrapper.signer

    override val debugInfo: String
        get() = solanaKit.debugInfo()

    override val statusInfo: Map<String, Any>
        get() = solanaKit.statusInfo()

    // IReceiveAdapter

    override val receiveAddress: String
        get() = solanaKit.receiveAddress

    override val isMainNet: Boolean
        get() = solanaKit.isMainnet

    override fun estimateFee(rawTransaction: ByteArray): BigDecimal {
        return solanaKit.estimateFee(rawTransaction)
    }

    override suspend fun send(rawTransaction: ByteArray): FullTransaction {
        val signer = signer ?: throw SolanaSignerNotInitializedException()

        return solanaKit.sendRawTransaction(rawTransaction, signer)
    }

    override suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata?,
    ): BroadcastRawTransactionResult {
        val normalizedRawHex = rawTransactionHex.trim()
        require(OfflineTransactionPayloadEncoder.isRawTransactionHex(normalizedRawHex)) {
            "Valid raw transaction hex is required"
        }
        val result = solanaKit.broadcastRawTransaction(
            rawTransaction = normalizedRawHex.hexToByteArray(),
            retryMetadata = (metadata as? OfflineBroadcastMetadata.Solana)?.toSolanaRetryMetadata(),
        )
        return BroadcastRawTransactionResult(
            txHash = result.signature,
            status = result.status.toAppStatus(),
        )
    }

    companion object {
        const val confirmationsThreshold: Int = 12
    }

}

internal class SolanaSignerNotInitializedException : IllegalStateException("Solana signer is not initialized")

private fun OfflineBroadcastMetadata.Solana.toSolanaRetryMetadata() = RawTransactionRetryMetadata(
    blockHash = blockHash,
    lastValidBlockHeight = lastValidBlockHeight,
)

private fun RawTransactionBroadcastStatus.toAppStatus(): BroadcastRawTransactionStatus =
    when (this) {
        RawTransactionBroadcastStatus.Submitted -> BroadcastRawTransactionStatus.Submitted
        RawTransactionBroadcastStatus.Queued -> BroadcastRawTransactionStatus.Queued
        RawTransactionBroadcastStatus.AlreadyKnown -> BroadcastRawTransactionStatus.AlreadyKnown
    }
