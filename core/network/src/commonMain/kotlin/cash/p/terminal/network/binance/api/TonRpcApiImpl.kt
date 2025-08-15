package cash.p.terminal.network.binance.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class TonRpcApiImpl(
    private val httpClient: HttpClient
) : TonRpcApi {

    override suspend fun getAddressState(address: String): TonAddressState? {
        val url = "https://toncenter.com/api/v2/getExtendedAddressInformation?address=$address"

        return httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.body<TonApiResponse>()
            .takeIf { it.ok == true }
            ?.result
            ?.let {
                TonAddressState(
                    code = it.accountState?.code.orEmpty()
                )
            }
    }

    @Serializable
    private data class TonApiResponse(
        val ok: Boolean? = null,
        val result: TonFullAccountState? = null,
    )

    @Serializable
    private data class TonFullAccountState(
        @SerialName("account_state")
        val accountState: TonRawAccountState? = null,
    )

    @Serializable
    private data class TonRawAccountState(
        val code: String? = null,
    )
}
