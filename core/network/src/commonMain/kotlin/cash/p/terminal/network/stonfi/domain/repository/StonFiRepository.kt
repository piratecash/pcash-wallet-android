package cash.p.terminal.network.stonfi.domain.repository

import cash.p.terminal.network.stonfi.domain.entity.Asset
import cash.p.terminal.network.stonfi.domain.entity.RouterInfo
import cash.p.terminal.network.stonfi.domain.entity.SimulateSwap
import cash.p.terminal.network.stonfi.domain.entity.SwapStatus
import java.math.BigDecimal

interface StonFiRepository {

    suspend fun getJettonAddress(
        contractAddress: String,
        ownerAddress: String
    ): String

    suspend fun getRouter(addrStr: String): RouterInfo

    suspend fun getCustomPayload(uri: String): String

    suspend fun simulateSwap(
        offerAddress: String,
        askAddress: String,
        units: String,
        slippageTolerance: BigDecimal,
        poolAddress: String? = null,
        referralAddress: String? = null,
        referralFeeBps: Int? = null,
        dexV2: Boolean? = true,
        dexVersion: List<String>? = null
    ): SimulateSwap

    suspend fun getSwapStatus(
        routerAddress: String,
        ownerAddress: String,
        queryId: String
    ): SwapStatus

    suspend fun getAssets(): List<Asset>

    suspend fun getAssetByAddress(address: String): Asset?
}
