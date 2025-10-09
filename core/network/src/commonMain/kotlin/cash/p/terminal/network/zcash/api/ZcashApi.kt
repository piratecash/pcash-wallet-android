package cash.p.terminal.network.zcash.api

import cash.p.terminal.network.api.parseResponse
import cash.p.terminal.network.zcash.data.dto.ZcashBlocksResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal class ZcashApi(
    private val httpClient: HttpClient
) {
    private companion object {
        const val BASE_URL = "https://api.blockchair.com/zcash/blocks"
        const val SORTING_BEGINNING = "time(asc)"
        const val SORTING_LAST = "time(desc)"
    }

    suspend fun getBlockHeight(date: LocalDate, sorting: String = SORTING_BEGINNING): Long? =
        withContext(Dispatchers.IO) {
            httpClient.get {
                url(BASE_URL)
                parameter("q", "time(${date})")
                parameter("s", sorting)
                parameter("limit", 1)
            }.parseResponse<ZcashBlocksResponse>()
                .data.firstOrNull()
                ?.blockHeight
        }

    suspend fun getLatestBlockHeight(): Long? = getBlockHeight(
        date = LocalDate.now(),
        sorting = SORTING_LAST
    )
}
