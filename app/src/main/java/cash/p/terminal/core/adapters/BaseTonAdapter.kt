package cash.p.terminal.core.adapters

import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.OfflineTransactionStatusAdapter
import cash.p.terminal.core.SignedOfflineTonTransaction
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.core.managers.statusInfo
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.RawMessageBroadcastMetadata
import io.horizontalsystems.tonkit.models.RawMessageBroadcastStatus
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

abstract class BaseTonAdapter(
    protected val tonKitWrapper: TonKitWrapper,
    val decimals: Int
) : IAdapter,
    IBalanceAdapter,
    IReceiveAdapter,
    OfflineTransactionAdapter<SignedOfflineTonTransaction>,
    OfflineTransactionStatusAdapter {

    val tonKit = tonKitWrapper.tonKit

    override val debugInfo: String
        get() = ""

    override val statusInfo: Map<String, Any>
        get() = tonKit.statusInfo()

    // IReceiveAdapter

    suspend fun sendWithPayloadBoc(amount: BigInteger, address: String, payload: String) {
        val request = SendRequestEntity(
            data = JSONObject().apply {
                put("valid_until", System.currentTimeMillis() / 1000 + 300)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", address)
                        put("amount",amount.toString())
                        put("payload", payload)
                    })
                })
            },
            tonConnectRequestId = "id",
            dAppId = "p.cash"
        )
        tonKit.send(tonKit.sign(request, tonKitWrapper.tonWallet))
    }

    override val receiveAddress: String
        get() = tonKit.receiveAddress.toUserFriendly(false)

    override val isMainNet: Boolean
        get() = tonKit.network == Network.MainNet

    override suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata?,
    ): BroadcastRawTransactionResult {
        val normalizedRawHex = rawTransactionHex.trim()
        require(OfflineTransactionPayloadEncoder.isRawTransactionHex(normalizedRawHex)) {
            "Valid raw transaction hex is required"
        }
        val result = tonKit.broadcastRawTransaction(
            rawMessage = normalizedRawHex.hexToByteArray(),
            metadata = (metadata as? OfflineBroadcastMetadata.Ton)?.toTonMetadata(),
        )
        return BroadcastRawTransactionResult(
            txHash = result.messageHash,
            status = result.status.toAppStatus(),
        )
    }

    override suspend fun transactionExists(txHash: String): Boolean =
        tonKit.transactionExistsByMessageHash(txHash)
}

private fun OfflineBroadcastMetadata.Ton.toTonMetadata() = RawMessageBroadcastMetadata(
    validUntil = validUntil,
    senderAddress = senderAddress,
    seqno = seqno,
)

private fun RawMessageBroadcastStatus.toAppStatus(): BroadcastRawTransactionStatus =
    when (this) {
        RawMessageBroadcastStatus.Submitted -> BroadcastRawTransactionStatus.Submitted
        RawMessageBroadcastStatus.Queued -> BroadcastRawTransactionStatus.Queued
        RawMessageBroadcastStatus.AlreadyKnown -> BroadcastRawTransactionStatus.AlreadyKnown
    }
