package cash.p.terminal.network.stonfi.api

import cash.p.terminal.network.api.parseResponse
import cash.p.terminal.network.stonfi.data.entity.AssetResponseDto
import cash.p.terminal.network.stonfi.data.entity.AssetsDto
import cash.p.terminal.network.stonfi.data.entity.CustomPayloadDto
import cash.p.terminal.network.stonfi.data.entity.RouterResponseDto
import cash.p.terminal.network.stonfi.data.entity.SimulateSwapDto
import cash.p.terminal.network.stonfi.data.entity.SwapStatusDto
import cash.p.terminal.network.stonfi.data.entity.WalletAddressDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.url

internal class StonFiApi(
    private val httpClient: HttpClient
) {
    private companion object {
        const val BASE_URL = "https://api.ston.fi/"
    }

    suspend fun simulateSwap(
        offerAddress: String,
        askAddress: String,
        units: String,
        slippageTolerance: String,
        poolAddress: String? = null,
        referralAddress: String? = null,
        referralFeeBps: Int? = null,
        dexV2: Boolean? = true,
        dexVersion: List<String>? = null
    ): SimulateSwapDto {
        return httpClient.post {
            url(BASE_URL + "v1/swap/simulate")
            parameter("offer_address", offerAddress)
            parameter("ask_address", askAddress)
            parameter("units", units)
            parameter("slippage_tolerance", slippageTolerance)
            poolAddress?.let { parameter("pool_address", it) }
            referralAddress?.let { parameter("referral_address", it) }
            referralFeeBps?.let { parameter("referral_fee_bps", it) }
            dexV2?.let { parameter("dex_v2", it) }
            dexVersion?.let { parameter("dex_version", it) }
        }.parseResponse()
    }

    suspend fun getSwapStatus(
        routerAddress: String,
        ownerAddress: String,
        queryId: String
    ): SwapStatusDto {
        return httpClient.get {
            url(BASE_URL + "v1/swap/status")
            parameter("router_address", routerAddress)
            parameter("owner_address", ownerAddress)
            parameter("query_id", queryId)
        }.parseResponse()
    }

    suspend fun getAssets(): AssetsDto {
        return httpClient.get {
            url(BASE_URL + "v1/assets")
        }.parseResponse()
    }

    suspend fun getAssetByAddress(address: String): AssetResponseDto {
        return httpClient.get {
            url(BASE_URL + "v1/assets/$address")
        }.parseResponse()
    }

    suspend fun getWalletAddress(
        addrStr: String,
        ownerAddress: String
    ): WalletAddressDto {
        return httpClient.get {
            url(BASE_URL + "v1/jetton/$addrStr/address")
            parameter("owner_address", ownerAddress)
        }.parseResponse()
    }

    suspend fun getRouter(addrStr: String): RouterResponseDto {
        return httpClient.get {
            url(BASE_URL + "v1/routers/$addrStr")
        }.parseResponse()
    }

    suspend fun getCustomPayload(uri: String): String {
        return kotlin.runCatching {
            httpClient.get(uri).parseResponse<CustomPayloadDto>().payload
        }.getOrElse {
            httpClient.get(uri).body()
        }
    }
}
