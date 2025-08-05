package cash.p.terminal.premium.data.model

import kotlinx.serialization.Serializable

@Serializable
internal data class JsonRpcResponse(
    val jsonrpc: String,
    val id: Int?,
    val result: String? = null,
    val error: JsonRpcError? = null
)

@Serializable
internal data class JsonRpcError(
    val code: Int,
    val message: String
)
