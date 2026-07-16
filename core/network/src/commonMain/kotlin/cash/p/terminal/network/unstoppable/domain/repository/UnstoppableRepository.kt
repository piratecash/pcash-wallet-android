package cash.p.terminal.network.unstoppable.domain.repository

import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderInfo
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderTokens
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableRoute
import java.math.BigDecimal

interface UnstoppableRepository {
    suspend fun rate(
        sellAsset: String,
        buyAsset: String,
        sellAmount: String,
        slippage: BigDecimal,
        providers: Set<String>,
        chainId: String? = null,
    ): List<UnstoppableRoute>

    suspend fun swap(
        sellAsset: String,
        buyAsset: String,
        sellAmount: String,
        slippage: BigDecimal,
        provider: String,
        destinationAddress: String,
        refundAddress: String? = null,
        sourceAddress: String? = null,
        chainId: String? = null,
    ): UnstoppableRoute

    suspend fun getTokens(providerApiId: String): UnstoppableProviderTokens

    suspend fun getProviders(): List<UnstoppableProviderInfo>
}
