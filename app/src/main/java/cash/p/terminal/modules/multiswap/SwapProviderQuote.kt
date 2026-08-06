package cash.p.terminal.modules.multiswap

import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider

data class SwapProviderQuote(
    val provider: IMultiSwapProvider,
    val swapQuote: ISwapQuote,
    val executionMode: SwapExecutionMode = SwapExecutionMode.ExactIn,
    val amountOutAccuracy: SwapAmountAccuracy = SwapAmountAccuracy.Exact,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val tokenIn by swapQuote::tokenIn
    val tokenOut by swapQuote::tokenOut
    val amountIn by swapQuote::amountIn
    val amountOut by swapQuote::amountOut
    val amountInMax by swapQuote::amountInMax
    val fields by swapQuote::fields
    val priceImpact by swapQuote::priceImpact
    val actionRequired by swapQuote::actionRequired
    val cautions by swapQuote::cautions
    val estimationTime by swapQuote::estimationTime
}

fun Iterable<SwapProviderQuote>.sortedByBest(
    direction: SwapAmountDirection,
): List<SwapProviderQuote> = when (direction) {
    SwapAmountDirection.In -> sortedByDescending(SwapProviderQuote::amountOut)
    SwapAmountDirection.Out -> sortedBy(SwapProviderQuote::amountIn)
}
