package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapAmountAccuracy
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

/**
 * Optional capability for providers that can quote a requested output amount natively.
 * Pair support does not imply support for this execution mode.
 */
interface IExactOutSwapProvider {
    val exactOutAccuracy: SwapAmountAccuracy
        get() = SwapAmountAccuracy.Exact

    suspend fun supportsExactOut(tokenIn: Token, tokenOut: Token): Boolean

    suspend fun fetchQuoteExactOut(
        tokenIn: Token,
        tokenOut: Token,
        amountOut: BigDecimal,
        settings: Map<String, Any?>,
    ): ISwapQuote

    suspend fun fetchFinalQuoteExactOut(
        tokenIn: Token,
        tokenOut: Token,
        amountOut: BigDecimal,
        swapSettings: Map<String, Any?>,
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: ISwapQuote,
    ): ISwapFinalQuote
}
