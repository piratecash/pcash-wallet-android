package cash.p.terminal.modules.walletconnect.request

import android.os.Parcelable
import androidx.lifecycle.ViewModel
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.core.managers.EvmMessageSigning
import cash.p.terminal.core.to0xHexString
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.WCSessionManager
import cash.p.terminal.wallet.IAccountManager
import com.google.gson.JsonParser
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val PERSONAL_SIGN_METHOD = "personal_sign"
private const val TYPED_DATA_METHOD = "eth_signTypedData"
private const val TYPED_DATA_METHOD_V4 = "eth_signTypedData_v4"
private const val ETH_SIGN_METHOD = "eth_sign"
private const val SEND_TRANSACTION_METHOD = "eth_sendTransaction"
private const val SIGN_TRANSACTION_METHOD = "eth_signTransaction"

class WCRequestEvmViewModel(
    private val accountManager: IAccountManager,
    private val evmBlockchainManager: EvmBlockchainManager,
    private val wcManager: WCManager,
) : ViewModel() {

    private val sessionRequestEvent = WCDelegate.sessionRequestEvent

    val blockchainType = wcManager.getBlockchainType(sessionRequestEvent?.chainId)
    private val chainData = sessionRequestEvent?.let {
        wcManager.getChainData(it.chainId)
    }
    private val chainName = chainData?.name
    private val chainAddress = chainData?.address

    private val evmKitWrapper: EvmKitWrapper? = getEthereumKitWrapper()
    var sessionRequestUi: SessionRequestUI = generateSessionRequestUI()

    private fun clearSessionRequest() {
        sessionRequestUi = SessionRequestUI.Initial
    }

    private fun generateSessionRequestUI(): SessionRequestUI {
        return sessionRequestEvent?.let { sessionRequest ->
            if (evmKitWrapper == null) {
                clearSessionRequest()
                return@let SessionRequestUI.Initial
            }

            SessionRequestUI.Content(
                peerUI = PeerUI(
                    peerName = sessionRequest.peerMetaData?.name ?: "",
                    peerIcon = sessionRequest.peerMetaData?.icons?.firstOrNull() ?: "",
                    peerUri = sessionRequest.peerMetaData?.url ?: "",
                    peerDescription = sessionRequest.peerMetaData?.description ?: "",
                ),
                topic = sessionRequest.topic,
                requestId = sessionRequest.request.id,
                param = getParam(sessionRequest),
                method = sessionRequest.request.method,
                chainName = chainName,
                chainAddress = chainAddress,
            )
        } ?: SessionRequestUI.Initial
    }

    private fun getParam(sessionRequest: Wallet.Model.SessionRequest) =
        when (sessionRequest.request.method) {
            PERSONAL_SIGN_METHOD -> {
                extractMessageParamFromPersonalSign(sessionRequest.request.params)
            }

            ETH_SIGN_METHOD -> {
                val params = JsonParser.parseString(sessionRequest.request.params).asJsonArray
                if (params.size() >= 2) {
                    params.get(1).asString
                } else {
                    throw Exception("Invalid Data")
                }
            }

            TYPED_DATA_METHOD, TYPED_DATA_METHOD_V4, SEND_TRANSACTION_METHOD, SIGN_TRANSACTION_METHOD -> {
                val params = JsonParser.parseString(sessionRequest.request.params).asJsonArray
                params.firstOrNull { it.isJsonObject }?.asJsonObject?.toString()
                    ?: throw Exception("Invalid Data")
            }

            else -> {
                sessionRequest.request.params
            }
        }

    private fun extractMessageParamFromPersonalSign(input: String): String {
        val jsonArray = JSONArray(input)
        return if (jsonArray.length() > 0) {
            val message = jsonArray.getString(0)
            try {
                String(message.hexStringToByteArray())
            } catch (_: Throwable) {
                message
            }
        } else {
            throw IllegalArgumentException()
        }
    }

    /**
     * personal_sign message, decoded to raw bytes exactly as received (no hex->String round-trip,
     * which would corrupt non-UTF-8 payloads before signing).
     */
    private fun personalSignBytes(): ByteArray {
        val jsonArray = JSONArray(sessionRequestEvent?.request?.params.orEmpty())
        if (jsonArray.length() == 0) throw IllegalArgumentException()
        val raw = jsonArray.getString(0)
        return try {
            raw.hexStringToByteArray()
        } catch (_: Throwable) {
            raw.toByteArray()
        }
    }

    private fun getEthereumKitWrapper(): EvmKitWrapper? {
        val blockchainType = blockchainType ?: return null
        val account = accountManager.activeAccount ?: return null
        val evmKitManager = evmBlockchainManager.getEvmKitManager(blockchainType)

        return runBlocking { evmKitManager.getEvmKitWrapper(account, blockchainType) }
    }

    suspend fun allow() {
        val evmKit = evmKitWrapper ?: throw WCSessionManager.RequestDataError.NoSuitableEvmKit
        val signer = evmKit.signer ?: throw WCSessionManager.RequestDataError.NoSigner
        val content = sessionRequestUi as? SessionRequestUI.Content ?: return

        val result = try {
            when (content.method) {
                ETH_SIGN_METHOD -> {
                    val message = content.param.hexStringToByteArray()
                    if (message.size == 32) {
                        EvmMessageSigning.signLegacyHash(signer, message)
                    } else {
                        EvmMessageSigning.signPersonalMessage(signer, message)
                    }
                }

                PERSONAL_SIGN_METHOD -> {
                    EvmMessageSigning.signPersonalMessage(signer, personalSignBytes())
                }

                TYPED_DATA_METHOD, TYPED_DATA_METHOD_V4 -> {
                    EvmMessageSigning.signTypedData(signer, content.param)
                }

                else -> throw Exception("Unsupported Chain")
            }
        } catch (e: Throwable) {
            // Signing failed or is unsupported by the hardware wallet (e.g. eth_sign or typed data
            // on Trezor, or the user cancelled on the device): respond with a JSON-RPC error so the
            // dApp doesn't hang, and propagate the failure to the caller.
            return suspendCoroutine { continuation ->
                WCDelegate.respondError(
                    content.topic,
                    content.requestId,
                    e.message ?: "Signing failed",
                    onSuccessResult = {
                        clearSessionRequest()
                        continuation.resumeWithException(e)
                    },
                    onErrorResult = {
                        clearSessionRequest()
                        continuation.resumeWithException(it)
                    }
                )
            }
        }

        return suspendCoroutine { continuation ->
            WCDelegate.respondPendingRequest(
                content.requestId,
                content.topic,
                result.to0xHexString().normalizeSignature(),
                onSuccessResult = {
                    continuation.resume(Unit)
                    clearSessionRequest()
                },
                onErrorResult = {
                    continuation.resumeWithException(it)
                    clearSessionRequest()
                }
            )
        }
    }

    /***
     * Pancake needs old r standard value in sign
     */
    private fun String.normalizeSignature(): String {
        val v = takeLast(2).lowercase()
        val normalizedV = when (v) {
            "00" -> "1b"
            "01" -> "1c"
            else -> v
        }
        return this.dropLast(2) + normalizedV
    }

    suspend fun reject() {
        return suspendCoroutine { continuation ->
            val sessionRequest = sessionRequestUi as? SessionRequestUI.Content
            if (sessionRequest != null) {
                WCDelegate.rejectRequest(
                    sessionRequest.topic,
                    sessionRequest.requestId,
                    onSuccessResult = {
                        clearSessionRequest()
                        continuation.resume(Unit)
                    },
                    onErrorResult = {
                        clearSessionRequest()
                        continuation.resumeWithException(it)
                    }
                )
            }
        }
    }

}

sealed class SessionRequestUI {
    object Initial : SessionRequestUI()

    data class Content(
        val peerUI: PeerUI,
        val topic: String,
        val requestId: Long,
        val param: String,
        val method: String,
        val chainName: String?,
        val chainAddress: String?,
    ) : SessionRequestUI()
}

@Parcelize
data class WCChainData(
    val id: Int,
    val name: String,
    val address: String?
) : Parcelable

data class PeerUI(
    val peerIcon: String,
    val peerName: String,
    val peerUri: String,
    val peerDescription: String,
) {
    companion object {
        val Empty = PeerUI("", "", "", "")
    }
}