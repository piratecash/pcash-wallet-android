package cash.p.terminal.network.unstoppable.data.repository

import cash.p.terminal.network.swaprepository.SwapProviderStatusRequest
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusResult
import cash.p.terminal.network.unstoppable.api.UnstoppableApi
import cash.p.terminal.network.unstoppable.data.entity.request.RateRequestDto
import cash.p.terminal.network.unstoppable.data.entity.request.SwapRequestDto
import cash.p.terminal.network.unstoppable.data.entity.request.TrackRequestDto
import cash.p.terminal.network.unstoppable.data.mapper.UnstoppableMapper
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderInfo
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderTokens
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableRoute
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

internal class UnstoppableRepositoryImpl(
    private val api: UnstoppableApi,
    private val mapper: UnstoppableMapper,
) : UnstoppableRepository, SwapProviderTransactionStatusRepository {

    override suspend fun rate(
        sellAsset: String,
        buyAsset: String,
        sellAmount: String,
        slippage: BigDecimal,
        providers: Set<String>,
        chainId: String?,
    ): List<UnstoppableRoute> = withContext(Dispatchers.IO) {
        api.rate(
            RateRequestDto(
                sellAsset = sellAsset,
                buyAsset = buyAsset,
                sellAmount = sellAmount,
                slippage = slippage.toDouble(),
                providers = providers,
                chainId = chainId,
            )
        ).routes.map(mapper::mapRoute)
    }

    override suspend fun swap(
        sellAsset: String,
        buyAsset: String,
        sellAmount: String,
        slippage: BigDecimal,
        provider: String,
        destinationAddress: String,
        refundAddress: String?,
        sourceAddress: String?,
        chainId: String?,
    ): UnstoppableRoute = withContext(Dispatchers.IO) {
        api.swap(
            SwapRequestDto(
                sellAsset = sellAsset,
                buyAsset = buyAsset,
                sellAmount = sellAmount,
                slippage = slippage.toDouble(),
                provider = provider,
                destinationAddress = destinationAddress,
                refundAddress = refundAddress,
                sourceAddress = sourceAddress,
                chainId = chainId,
            )
        ).let(mapper::mapRoute)
    }

    override suspend fun getTokens(providerApiId: String): UnstoppableProviderTokens = withContext(Dispatchers.IO) {
        api.tokens(providerApiId).let(mapper::mapTokens)
    }

    override suspend fun getProviders(): List<UnstoppableProviderInfo> = withContext(Dispatchers.IO) {
        api.providers().map(mapper::mapProvider)
    }

    override suspend fun getTransactionStatus(
        request: SwapProviderStatusRequest
    ): SwapProviderTransactionStatusResult? = withContext(Dispatchers.IO) {
        val track = api.track(
            TrackRequestDto(
                uuid = request.transactionId,
                inboundTxHash = request.inboundTxHash,
            )
        )
        val status = mapper.mapTrackStatus(track.status) ?: return@withContext null

        SwapProviderTransactionStatusResult(
            status = status,
            amountOutReal = track.toAmount?.takeIf { it > BigDecimal.ZERO },
            // v2 track response carries no completion timestamp.
            finishedAt = null,
        )
    }
}
