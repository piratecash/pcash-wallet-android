package cash.p.terminal.network.stonfi.data.repository

import cash.p.terminal.network.stonfi.api.StonFiApi
import cash.p.terminal.network.stonfi.data.mapper.StonFiMapper
import cash.p.terminal.network.stonfi.domain.entity.Asset
import cash.p.terminal.network.stonfi.domain.entity.RouterInfo
import cash.p.terminal.network.stonfi.domain.entity.SimulateSwap
import cash.p.terminal.network.stonfi.domain.entity.SwapStatus
import cash.p.terminal.network.stonfi.domain.repository.StonFiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal

internal class StonFiRepositoryImpl(
    private val stonFiApi: StonFiApi,
    private val stonFiMapper: StonFiMapper
) : StonFiRepository {

    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, String>()

    override suspend fun getJettonAddress(
        contractAddress: String,
        ownerAddress: String
    ): String = withContext(Dispatchers.IO) {
        val key = buildCacheKey(contractAddress, ownerAddress)
        cacheMutex.withLock {
            cache.getOrPut(key) {
                stonFiApi.getWalletAddress(contractAddress, ownerAddress)
                    .address
            }
        }
    }

    private fun buildCacheKey(contractAddress: String, ownerAddress: String): String {
        return "$contractAddress|$ownerAddress"
    }

    override suspend fun getRouter(addrStr: String): RouterInfo = withContext(Dispatchers.IO) {
        stonFiApi.getRouter(addrStr).router.let(stonFiMapper::mapRouterDto)
    }

    override suspend fun getCustomPayload(uri: String): String = withContext(Dispatchers.IO) {
        stonFiApi.getCustomPayload(uri)
    }

    override suspend fun simulateSwap(
        offerAddress: String,
        askAddress: String,
        units: String,
        slippageTolerance: BigDecimal,
        poolAddress: String?,
        referralAddress: String?,
        referralFeeBps: Int?,
        dexV2: Boolean?,
        dexVersion: List<String>?
    ): SimulateSwap = withContext(Dispatchers.IO) {
        stonFiApi.simulateSwap(
            offerAddress = offerAddress,
            askAddress = askAddress,
            units = units,
            slippageTolerance = slippageTolerance.divide(BigDecimal(100)).toString(),
            poolAddress = poolAddress,
            referralAddress = referralAddress,
            referralFeeBps = referralFeeBps,
            dexV2 = dexV2,
            dexVersion = dexVersion
        ).let(stonFiMapper::mapSimulateSwapDto)
    }

    override suspend fun getSwapStatus(
        routerAddress: String,
        ownerAddress: String,
        queryId: String
    ): SwapStatus = withContext(Dispatchers.IO) {
        stonFiApi.getSwapStatus(
            routerAddress = routerAddress,
            ownerAddress = ownerAddress,
            queryId = queryId
        ).let(stonFiMapper::mapSwapStatusDto)
    }

    override suspend fun getAssets(): List<Asset> = withContext(Dispatchers.IO) {
        stonFiApi.getAssets().asset_list.map(stonFiMapper::mapAssetDto)
    }

    override suspend fun getAssetByAddress(address: String): Asset? = withContext(Dispatchers.IO) {
        try {
            stonFiApi.getAssetByAddress(address).asset.let(stonFiMapper::mapAssetDto)
        } catch (e: Exception) {
            null
        }
    }
}
