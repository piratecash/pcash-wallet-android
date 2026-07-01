package cash.p.terminal.modules.paycore

import cash.p.terminal.core.HSCaution
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.modules.multiswap.settings.ISwapSetting
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

class PayCoreQuote(
    override val amountOut: BigDecimal,
    override val priceImpact: BigDecimal?,
    override val fields: List<DataField>,
    override val tokenIn: Token,
    override val tokenOut: Token,
    override val amountIn: BigDecimal,
    val serviceFee: BigDecimal,
    override val actionRequired: ISwapProviderAction?,
    override val settings: List<ISwapSetting> = emptyList(),
    override val cautions: List<HSCaution> = emptyList()
) : ISwapQuote
