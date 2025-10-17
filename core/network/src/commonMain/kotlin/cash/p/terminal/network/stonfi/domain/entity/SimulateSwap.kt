package cash.p.terminal.network.stonfi.domain.entity

import java.math.BigDecimal
import java.math.BigInteger

data class SimulateSwap(
    val offerAddress: String,
    val askAddress: String,
    val offerJettonWallet: String,
    val askJettonWallet: String,
    val routerAddress: String,
    val poolAddress: String,
    val offerUnits: BigInteger,
    val askUnits: String,
    val slippageTolerance: String,
    val minAskUnits: String,
    val recommendedSlippageTolerance: String,
    val recommendedMinAskUnits: String,
    val swapRate: String,
    val priceImpact: String,
    val feeAddress: String,
    val feeUnits: String,
    val feePercent: String,
    val gasParams: GasParams
)

data class GasParams(
    val forwardGas: BigInteger,
    val estimatedGasConsumption: BigInteger,
    val gasBudget: BigInteger
)
