package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.HSCaution
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.modules.multiswap.settings.ISwapSetting
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

class SwapQuoteStonFi(
    override val amountOut: BigDecimal,
    override val priceImpact: BigDecimal?,
    override val fields: List<DataField>,
    override val settings: List<ISwapSetting>,
    override val tokenIn: Token,
    override val tokenOut: Token,
    override val amountIn: BigDecimal,
    override val actionRequired: ISwapProviderAction?,
    override val cautions: List<HSCaution> = listOf(),
    val swapData: StonFiSwapData
) : ISwapQuote

data class StonFiSwapData(
    val offerAddress: String,
    val askAddress: String,
    val offerJettonWallet: String,
    val askJettonWallet: String,
    val routerAddress: String,
    val poolAddress: String,
    val offerUnits: BigDecimal,
    val askUnits: String,
    val slippageTolerance: String,
    val minAskUnits: String,
    val swapRate: String,
    val priceImpact: String,
    val feeAddress: String,
    val feeUnits: String,
    val feePercent: String,
    val gasParams: StonFiGasParams
)

data class StonFiGasParams(
    val forwardGas: BigDecimal?,
    val estimatedGasConsumption: BigDecimal?,
    val gasBudget: String? = null
)
