package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.HSCaution
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.modules.multiswap.settings.ISwapSetting
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.wallet.Token
import io.horizontalsystems.uniswapkit.models.TradeData
import io.horizontalsystems.uniswapkit.v3.TradeDataV3
import java.math.BigDecimal

interface ISwapQuote {
    val amountOut: BigDecimal
    val priceImpact: BigDecimal?
    val fields: List<DataField>
    val settings: List<ISwapSetting>
    val tokenIn: Token
    val tokenOut: Token
    val amountIn: BigDecimal
    val actionRequired: ISwapProviderAction?
    val cautions: List<HSCaution>
}

class SwapQuoteUniswap(
    val tradeData: TradeData,
    override val fields: List<DataField>,
    override val settings: List<ISwapSetting>,
    override val tokenIn: Token,
    override val tokenOut: Token,
    override val amountIn: BigDecimal,
    override val actionRequired: ISwapProviderAction?,
    override val cautions: List<HSCaution> = listOf()
) : ISwapQuote {
    override val amountOut: BigDecimal = tradeData.amountOut!!
    override val priceImpact: BigDecimal? = tradeData.priceImpact
}

class SwapQuoteUniswapV3(
    val tradeDataV3: TradeDataV3,
    override val fields: List<DataField>,
    override val settings: List<ISwapSetting>,
    override val tokenIn: Token,
    override val tokenOut: Token,
    override val amountIn: BigDecimal,
    override val actionRequired: ISwapProviderAction?,
    override val cautions: List<HSCaution> = listOf()
) : ISwapQuote {
    override val amountOut = tradeDataV3.tokenAmountOut.decimalAmount!!
    override val priceImpact = tradeDataV3.priceImpact
}

class SwapQuoteOneInch(
    override val amountOut: BigDecimal,
    override val priceImpact: BigDecimal?,
    override val fields: List<DataField>,
    override val settings: List<ISwapSetting>,
    override val tokenIn: Token,
    override val tokenOut: Token,
    override val amountIn: BigDecimal,
    override val actionRequired: ISwapProviderAction?,
    override val cautions: List<HSCaution> = listOf()
) : ISwapQuote

class SwapQuoteChangeNow(
    override val amountOut: BigDecimal,
    override val priceImpact: BigDecimal?,
    override val fields: List<DataField>,
    override val settings: List<ISwapSetting>,
    override val tokenIn: Token,
    override val tokenOut: Token,
    override val amountIn: BigDecimal,
    override val actionRequired: ISwapProviderAction?,
    override val cautions: List<HSCaution> = listOf()
) : ISwapQuote

class SwapQuoteThorChain(
    override val amountOut: BigDecimal,
    override val priceImpact: BigDecimal?,
    override val fields: List<DataField>,
    override val settings: List<ISwapSetting>,
    override val tokenIn: Token,
    override val tokenOut: Token,
    override val amountIn: BigDecimal,
    override val actionRequired: ISwapProviderAction?,
    override val cautions: List<HSCaution>,
    val slippageThreshold: BigDecimal,
) : ISwapQuote