package cash.p.terminal.network.zcash.api

import cash.p.terminal.network.api.parseResponse
import cash.p.terminal.network.zcash.data.dto.ZcashBlocksResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ZcashApi(
    private val httpClient: HttpClient
) {
    private companion object {
        const val BASE_URL = "https://api.blockchair.com/zcash/blocks"
    }

    suspend fun getBlockHeight(date: LocalDate): Long? = withContext(Dispatchers.IO) {
        httpClient.get {
            url(BASE_URL)
            parameter("q", "time(${date})")
            parameter("s", "time(asc)")
            parameter("limit", 1)
        }.parseResponse<ZcashBlocksResponse>()
            .data.firstOrNull()
            ?.blockHeight
    }
}
