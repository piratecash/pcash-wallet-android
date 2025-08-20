import cash.p.terminal.network.binance.api.SolanaAccountInfo
import cash.p.terminal.network.binance.api.SolanaRpcApi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class SolanaRpcApiImpl(
    private val httpClient: HttpClient
) : SolanaRpcApi {

    override suspend fun getAccountInfo(address: String): SolanaAccountInfo? {
        val rpcUrl = "https://api.mainnet-beta.solana.com"
        val response: SolanaRpcResponse = httpClient.post(rpcUrl) {
            contentType(ContentType.Application.Json)
            setBody(
                SolanaRpcRequest(
                    jsonrpc = "2.0",
                    id = 1,
                    method = "getAccountInfo",
                    params = listOf(
                        JsonPrimitive(address),
                        buildJsonObject {
                            put("encoding", "base64")
                            put("commitment", "finalized")
                        }
                    )
                )
            )
        }.body()

        return response.result?.value?.executable?.let { executable ->
            SolanaAccountInfo(executable = executable)
        }
    }

    @Serializable
    private data class SolanaRpcRequest(
        val jsonrpc: String,
        val id: Int,
        val method: String,
        val params: List<JsonElement>
    )

    @Serializable
    private data class SolanaRpcResponse(
        val jsonrpc: String,
        val id: Int?,
        val result: SolanaAccountInfoResult? = null,
        val error: SolanaRpcError? = null
    )

    @Serializable
    private data class SolanaAccountInfoResult(
        val value: SolanaAccountValue? = null
    )

    @Serializable
    private data class SolanaAccountValue(
        val executable: Boolean? = null
    )

    @Serializable
    private data class SolanaRpcError(
        val code: Int,
        val message: String
    )
}
