package cash.p.terminal.network.stonfi.data.entity

import kotlinx.serialization.SerialName
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
    @SerialName("forward_gas")
    val forwardGas: String? = null,
    @SerialName("estimated_gas_consumption")
    val estimatedGasConsumption: String? = null,
    @SerialName("gas_budget")
    val gasBudget: String? = null
)
