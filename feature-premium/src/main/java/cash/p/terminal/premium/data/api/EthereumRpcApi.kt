package cash.p.terminal.premium.data.api

import cash.p.terminal.premium.data.model.JsonRpcResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class EthereumRpcApi(
    private val httpClient: HttpClient
) {
    companion object {
        // ERC-20 function selectors
        private const val BALANCE_OF_SELECTOR = "0x70a08231" // balanceOf(address)
    }

    suspend fun getTokenBalance(
        rpcUrl: String,
        contractAddress: String,
        walletAddress: String
    ): String? {
        val paddedAddress = walletAddress.removePrefix("0x").padStart(64, '0')
        val data = BALANCE_OF_SELECTOR + paddedAddress

        val requestBody = """
            {
                "jsonrpc": "2.0",
                "method": "eth_call",
                "params": [
                    {
                        "to": "$contractAddress",
                        "data": "$data"
                    },
                    "latest"
                ],
                "id": 1
            }
        """.trimIndent()

        val response: JsonRpcResponse = httpClient.post(rpcUrl) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()

        return if (response.error == null) {
            response.result
        } else {
            null
        }
    }
} 