package cash.p.terminal.network.alphaaml.api

import cash.p.terminal.network.alphaaml.data.AlphaAmlResponse
import cash.p.terminal.network.api.parseResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class AlphaAmlApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val apiKey: String
) {
    suspend fun getRiskGrade(address: String): AlphaAmlResponse? = withContext(Dispatchers.IO) {
        try {
            httpClient.get {
                url("$baseUrl/api/risk-grade")
                parameter("address", address)
                parameter("apiKey", apiKey)
            }.parseResponse<AlphaAmlResponse>()
        } catch (e: Exception) {
            Timber.d("AlphaAmlApi getRiskGrade error: ${e.localizedMessage}")
            null
        }
    }
}
