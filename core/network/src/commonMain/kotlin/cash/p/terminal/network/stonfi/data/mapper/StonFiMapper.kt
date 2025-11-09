package cash.p.terminal.network.stonfi.data.mapper

import cash.p.terminal.network.stonfi.data.entity.AssetDto
import cash.p.terminal.network.stonfi.data.entity.AssetMetaDto
import cash.p.terminal.network.stonfi.data.entity.GasParamsDto
import cash.p.terminal.network.stonfi.data.entity.RouterDto
import cash.p.terminal.network.stonfi.data.entity.SimulateSwapDto
import cash.p.terminal.network.stonfi.data.entity.SwapStatusDto
import cash.p.terminal.network.stonfi.domain.entity.Asset
import cash.p.terminal.network.stonfi.domain.entity.AssetMeta
import cash.p.terminal.network.stonfi.domain.entity.GasParams
import cash.p.terminal.network.stonfi.domain.entity.RouterInfo
import cash.p.terminal.network.stonfi.domain.entity.SimulateSwap
import cash.p.terminal.network.stonfi.domain.entity.SwapStatus
import java.math.BigInteger

internal class StonFiMapper {

    fun mapSimulateSwapDto(dto: SimulateSwapDto): SimulateSwap {
        return SimulateSwap(
            offerAddress = dto.offer_address,
            askAddress = dto.ask_address,
            offerJettonWallet = dto.offer_jetton_wallet,
            askJettonWallet = dto.ask_jetton_wallet,
            routerAddress = dto.router_address,
            poolAddress = dto.pool_address,
            offerUnits = dto.offer_units.toBigInteger(),
            askUnits = dto.ask_units,
            slippageTolerance = dto.slippage_tolerance,
            minAskUnits = dto.min_ask_units,
            recommendedSlippageTolerance = dto.recommended_slippage_tolerance,
            recommendedMinAskUnits = dto.recommended_min_ask_units,
            swapRate = dto.swap_rate,
            priceImpact = dto.price_impact,
            feeAddress = dto.fee_address,
            feeUnits = dto.fee_units,
            feePercent = dto.fee_percent,
            gasParams = mapGasParamsDto(dto.gas_params)
        )
    }

    fun mapSwapStatusDto(dto: SwapStatusDto): SwapStatus {
        return SwapStatus(
            address = dto.address,
            queryId = dto.query_id,
            exitCode = dto.exit_code,
            coins = dto.coins,
            logicalTime = dto.logical_time,
            txHash = dto.tx_hash,
            balanceDeltas = dto.balance_deltas
        )
    }

    fun mapAssetDto(dto: AssetDto): Asset {
        return Asset(
            contractAddress = dto.contract_address,
            kind = dto.kind,
            balance = dto.balance,
            dexPriceUsd = dto.dex_price_usd,
            extensions = dto.extensions,
            meta = dto.meta?.let(::mapAssetMetaDto),
            pairPriority = dto.pair_priority,
            popularityIndex = dto.popularity_index,
            tags = dto.tags,
            walletAddress = dto.wallet_address
        )
    }

    fun mapRouterDto(dto: RouterDto): RouterInfo {
        return RouterInfo(
            address = dto.address,
            majorVersion = dto.majorVersion,
            minorVersion = dto.minorVersion,
            ptonMasterAddress = dto.ptonMasterAddress,
            ptonWalletAddress = dto.ptonWalletAddress,
            ptonVersion = dto.ptonVersion,
            routerType = dto.routerType,
            poolCreationEnabled = dto.poolCreationEnabled
        )
    }

    private fun mapGasParamsDto(dto: GasParamsDto): GasParams {
        return GasParams(
            forwardGas = dto.forwardGas?.toBigIntegerOrNull() ?: BigInteger.ZERO,
            estimatedGasConsumption = dto.estimatedGasConsumption?.toBigIntegerOrNull() ?: BigInteger.ZERO,
            gasBudget = dto.gasBudget?.toBigIntegerOrNull() ?: BigInteger.ZERO
        )
    }

    private fun mapAssetMetaDto(dto: AssetMetaDto): AssetMeta {
        return AssetMeta(
            customPayloadApiUri = dto.custom_payload_api_uri,
            decimals = dto.decimals,
            displayName = dto.display_name,
            imageUrl = dto.image_url,
            symbol = dto.symbol
        )
    }
}
