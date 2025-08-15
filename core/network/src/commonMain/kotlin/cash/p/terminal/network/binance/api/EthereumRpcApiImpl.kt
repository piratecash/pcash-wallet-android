package cash.p.terminal.network.binance.api

import cash.p.terminal.network.binance.data.JsonRpcResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class EthereumRpcApiImpl(
    private val httpClient: HttpClient
): EthereumRpcApi {
    companion object Companion {
        // ERC-20 function selectors
        private const val BALANCE_OF_SELECTOR = "0x70a08231" // balanceOf(address)
    }

    override suspend fun getTokenBalance(
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

    override suspend fun getCode(
        rpcUrl: String,
        address: String
    ): String? {
        val requestBody = """
            {
                "jsonrpc": "2.0",
                "method": "eth_getCode",
                "params": [
                    "$address",
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