package cash.p.terminal.modules.walletconnect.request

import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.gson.JsonParser
import com.walletconnect.web3.wallet.client.Wallet
import cash.p.terminal.core.App
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.core.toHexString
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.modules.walletconnect.WCSessionManager
import cash.p.terminal.modules.walletconnect.WCUtils
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.core.entities.Blockchain
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val PERSONAL_SIGN_METHOD = "personal_sign"
private const val TYPED_DATA_METHOD = "eth_signTypedData"
private const val TYPED_DATA_METHOD_V4 = "eth_signTypedData_v4"
private const val ETH_SIGN_METHOD = "eth_sign"
private const val SEND_TRANSACTION_METHOD = "eth_sendTransaction"
private const val SIGN_TRANSACTION_METHOD = "eth_signTransaction"

class WCNewRequestViewModel(
    private val accountManager: cash.p.terminal.wallet.IAccountManager,
    private val evmBlockchainManager: EvmBlockchainManager,
) : ViewModel() {

    val blockchain: Blockchain? by lazy {
        val sessionChainId = WCDelegate.sessionRequestEvent?.chainId ?: return@lazy null
        val chainId = getChainData(sessionChainId)?.chain?.id ?: return@lazy null
        evmBlockchainManager.getBlockchain(chainId)
    }

    val evmKitWrapper: EvmKitWrapper? = getEthereumKitWrapper()
    var sessionRequest: SessionRequestUI = generateSessionRequestUI()

    private fun clearSessionRequest() {
        sessionRequest = SessionRequestUI.Initial
    }

    private fun generateSessionRequestUI(): SessionRequestUI {
        return WCDelegate.sessionRequestEvent?.let { sessionRequest ->
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
                chainData = getChainData(sessionRequest.chainId),
                method = sessionRequest.request.method,
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

    private fun getChainData(chainId: String?): WCChainData? {
        return WCUtils.getChainData(chainId ?: return null)
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

    private fun getEthereumKitWrapper(): EvmKitWrapper? {
        val blockchain = blockchain ?: return null
        val sessionChainId = WCDelegate.sessionRequestEvent?.chainId ?: return null
        val chainId = getChainData(sessionChainId)?.chain?.id ?: return null

        val account = accountManager.activeAccount ?: return null
        val evmKitManager = evmBlockchainManager.getEvmKitManager(blockchain.type)
        val evmKitWrapper = evmKitManager.getEvmKitWrapper(account, blockchain.type)

        return if (evmKitWrapper.evmKit.chain.id == chainId) {
            evmKitWrapper
        } else {
            evmKitManager.unlink(account)
            null
        }
    }

    suspend fun allow() {
        val evmKit = evmKitWrapper ?: throw WCSessionManager.RequestDataError.NoSuitableEvmKit
        val signer = evmKit.signer ?: throw WCSessionManager.RequestDataError.NoSigner
        return suspendCoroutine { continuation ->
            val sessionRequest = sessionRequest as? SessionRequestUI.Content
            if (sessionRequest != null) {
                val result = when (sessionRequest.method) {
                    ETH_SIGN_METHOD -> {
                        val message = sessionRequest.param.hexStringToByteArray()
                        if (message.size == 32) {
                            signer.signByteArrayLegacy(message = message)
                        } else {
                            signer.signByteArray(message = message)
                        }
                    }

                    PERSONAL_SIGN_METHOD -> {
                        signer.signByteArray(message = sessionRequest.param.toByteArray())
                    }

                    TYPED_DATA_METHOD, TYPED_DATA_METHOD_V4 -> {
                        signer.signTypedData(rawJsonMessage = sessionRequest.param)
                    }

                    else -> throw Exception("Unsupported Chain")
                }

                WCDelegate.respondPendingRequest(
                    sessionRequest.requestId,
                    sessionRequest.topic,
                    result.toHexString(),
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
    }

    suspend fun reject() {
        return suspendCoroutine { continuation ->
            val sessionRequest = sessionRequest as? SessionRequestUI.Content
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

    class Factory() : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WCNewRequestViewModel(App.accountManager, App.evmBlockchainManager) as T
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
        val chainData: WCChainData?,
        val method: String,
    ) : SessionRequestUI()
}

@Parcelize
data class WCChainData(
    val chain: Chain,
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