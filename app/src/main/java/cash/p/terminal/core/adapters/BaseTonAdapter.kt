package cash.p.terminal.core.adapters

import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.core.managers.statusInfo
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.models.Network
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

abstract class BaseTonAdapter(
    protected val tonKitWrapper: TonKitWrapper,
    val decimals: Int
) : IAdapter, IBalanceAdapter, IReceiveAdapter {

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
}
