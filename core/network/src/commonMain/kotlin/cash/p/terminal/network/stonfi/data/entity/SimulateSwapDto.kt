package cash.p.terminal.network.stonfi.data.entity

import kotlinx.serialization.Serializable

@Serializable
internal data class SimulateSwapDto(
    val offer_address: String,
    val ask_address: String,
    val offer_jetton_wallet: String,
    val ask_jetton_wallet: String,
    val router_address: String,
    val pool_address: String,
    val offer_units: String,
    val ask_units: String,
    val slippage_tolerance: String,
    val min_ask_units: String,
    val recommended_slippage_tolerance: String,
    val recommended_min_ask_units: String,
    val swap_rate: String,
    val price_impact: String,
    val fee_address: String,
    val fee_units: String,
    val fee_percent: String,
    val gas_params: GasParamsDto
)

@Serializable
internal data class GasParamsDto(
    val forward_gas: String,
    val estimated_gas_consumption: String,
    val gas_budget: String? = null
)
