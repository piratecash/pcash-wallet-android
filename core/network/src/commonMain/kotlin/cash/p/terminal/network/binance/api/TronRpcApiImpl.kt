package cash.p.terminal.network.binance.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

internal class TronRpcApiImpl(
    private val httpClient: HttpClient
) : TronRpcApi {

    override suspend fun getContractInfo(address: String): TronContractInfo? {
        val url = "https://apilist.tronscan.org/api/account?address=$address"

        val response: TronApiResponse = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.body()

        return TronContractInfo(
            contractMap = response.contractMap.orEmpty()
        )
    }

    @Serializable
    private data class TronApiResponse(
        val contractMap: Map<String, Boolean>? = null
    )
}
