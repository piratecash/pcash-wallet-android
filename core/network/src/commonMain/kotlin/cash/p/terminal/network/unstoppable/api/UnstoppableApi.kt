package cash.p.terminal.network.unstoppable.api

import cash.p.terminal.network.data.EncodedSecrets
import cash.p.terminal.network.data.setJsonBody
import cash.p.terminal.network.unstoppable.data.entity.BackendUnstoppableResponseError
import cash.p.terminal.network.unstoppable.data.entity.ProviderDto
import cash.p.terminal.network.unstoppable.data.entity.RateResponseDto
import cash.p.terminal.network.unstoppable.data.entity.RouteDto
import cash.p.terminal.network.unstoppable.data.entity.TokensDto
import cash.p.terminal.network.unstoppable.data.entity.TrackResponseDto
import cash.p.terminal.network.unstoppable.data.entity.request.RateRequestDto
import cash.p.terminal.network.unstoppable.data.entity.request.SwapRequestDto
import cash.p.terminal.network.unstoppable.data.entity.request.TrackRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.isSuccess

internal class UnstoppableApi(
    private val httpClient: HttpClient,
) {
    private companion object {
        // The provided API key is authorized for the production host only; the dev host rejects it
        // with 401, so every build uses production until a separate dev key is available.
        const val BASE_URL = "https://swap-api.unstoppable.money/v2/"
    }

    suspend fun rate(request: RateRequestDto): RateResponseDto {
        return httpClient.post {
            url(BASE_URL +"rate")
            authorize()
            accept(ContentType.Application.Json)
            setJsonBody(request)
        }.parseUnstoppableResponse()
    }

    suspend fun swap(request: SwapRequestDto): RouteDto {
        return httpClient.post {
            url(BASE_URL +"swap")
            authorize()
            accept(ContentType.Application.Json)
            setJsonBody(request)
        }.parseUnstoppableResponse()
    }

    suspend fun tokens(provider: String): TokensDto {
        return httpClient.get {
            url(BASE_URL +"tokens")
            authorize()
            accept(ContentType.Application.Json)
            parameter("provider", provider)
        }.parseUnstoppableResponse()
    }

    suspend fun providers(): List<ProviderDto> {
        return httpClient.get {
            url(BASE_URL +"providers")
            authorize()
            accept(ContentType.Application.Json)
        }.parseUnstoppableResponse()
    }

    suspend fun track(request: TrackRequestDto): TrackResponseDto {
        return httpClient.post {
            url(BASE_URL +"track")
            authorize()
            accept(ContentType.Application.Json)
            setJsonBody(request)
        }.parseUnstoppableResponse()
    }

    private fun HttpRequestBuilder.authorize() {
        header("x-api-key", EncodedSecrets.UNSTOPPABLE_DEX_API_KEY)
    }
}

internal suspend inline fun <reified T : Any> HttpResponse.parseUnstoppableResponse(): T {
    return if (status.isSuccess()) {
        body<T>()
    } else {
        throw body<BackendUnstoppableResponseError>()
    }
}
