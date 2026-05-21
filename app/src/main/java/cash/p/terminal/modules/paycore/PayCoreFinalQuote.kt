package cash.p.terminal.modules.paycore

import cash.p.terminal.core.HSCaution
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

data class PayCoreFinalQuote(
    override val tokenIn: Token,
    override val tokenOut: Token,
    override val amountIn: BigDecimal,
    override val amountOut: BigDecimal,
    override val sendTransactionData: SendTransactionData,
    override val priceImpact: BigDecimal?,
    override val fields: List<DataField>,
    val payCoreTransactionId: String? = null,
    override val amountOutMin: BigDecimal? = null,
    override val cautions: List<HSCaution> = emptyList()
) : ISwapFinalQuote
