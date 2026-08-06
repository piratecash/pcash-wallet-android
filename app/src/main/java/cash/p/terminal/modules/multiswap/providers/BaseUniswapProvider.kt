package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.modules.multiswap.EvmBlockchainHelper
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapAmountDirection
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.SwapQuoteUniswap
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.settings.SwapSettingDeadline
import cash.p.terminal.modules.multiswap.settings.SwapSettingRecipient
import cash.p.terminal.modules.multiswap.settings.SwapSettingSlippage
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.multiswap.ui.DataFieldAllowance
import cash.p.terminal.modules.multiswap.ui.DataFieldRecipient
import cash.p.terminal.modules.multiswap.ui.DataFieldRecipientExtended
import cash.p.terminal.modules.multiswap.ui.DataFieldSlippage
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.uniswapkit.UniswapKit
import io.horizontalsystems.uniswapkit.models.TradeData
import io.horizontalsystems.uniswapkit.models.TradeOptions
import kotlinx.coroutines.rx2.await
import java.math.BigDecimal

abstract class BaseUniswapProvider : EvmSwapProvider(), IExactOutSwapProvider {
    private val uniswapKit by lazy { UniswapKit.getInstance() }

    override val mevProtectionAvailable: Boolean = true

    final override suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        settings: Map<String, Any?>,
    ): ISwapQuote = fetchQuote(tokenIn, tokenOut, amountIn, settings, SwapAmountDirection.In)

    final override suspend fun fetchQuoteExactOut(
        tokenIn: Token,
        tokenOut: Token,
        amountOut: BigDecimal,
        settings: Map<String, Any?>,
    ): ISwapQuote = fetchQuote(tokenIn, tokenOut, amountOut, settings, SwapAmountDirection.Out)

    final override suspend fun supportsExactOut(tokenIn: Token, tokenOut: Token): Boolean =
        supports(tokenIn, tokenOut) && isUniswapToken(tokenIn) && isUniswapToken(tokenOut)

    private suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amount: BigDecimal,
        settings: Map<String, Any?>,
        direction: SwapAmountDirection,
    ): ISwapQuote {
        val bestTrade = fetchBestTrade(tokenIn, tokenOut, amount, settings, direction)
        val amountIn = requireNotNull(bestTrade.tradeData.amountIn)
        val inputRequired = requiredInput(amountIn, bestTrade.tradeData.amountInMax(direction))
        val routerAddress = uniswapKit.routerAddress(bestTrade.chain)
        val allowance = getAllowance(tokenIn, routerAddress)

        return SwapQuoteUniswap(
            tradeData = bestTrade.tradeData,
            fields = quoteFields(bestTrade, allowance, inputRequired, tokenIn),
            settings = bestTrade.settings,
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            actionRequired = getCreateTokenActionRequired(listOf(tokenIn, tokenOut))
                ?: actionApprove(allowance, inputRequired, routerAddress, tokenIn),
        )
    }

    final override suspend fun fetchFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        swapSettings: Map<String, Any?>,
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: ISwapQuote,
    ): ISwapFinalQuote = fetchFinalQuote(
        tokenIn,
        tokenOut,
        amountIn,
        swapSettings,
        sendTransactionSettings,
        SwapAmountDirection.In,
    )

    final override suspend fun fetchFinalQuoteExactOut(
        tokenIn: Token,
        tokenOut: Token,
        amountOut: BigDecimal,
        swapSettings: Map<String, Any?>,
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: ISwapQuote,
    ): ISwapFinalQuote = fetchFinalQuote(
        tokenIn,
        tokenOut,
        amountOut,
        swapSettings,
        sendTransactionSettings,
        SwapAmountDirection.Out,
    )

    private suspend fun fetchFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amount: BigDecimal,
        settings: Map<String, Any?>,
        sendSettings: SendTransactionSettings?,
        direction: SwapAmountDirection,
    ): ISwapFinalQuote {
        check(sendSettings is SendTransactionSettings.Evm)
        val bestTrade = fetchBestTrade(tokenIn, tokenOut, amount, settings, direction)
        val tradeData = bestTrade.tradeData
        val amountIn = requireNotNull(tradeData.amountIn)
        val amountInMax = tradeData.amountInMax(direction)
        val inputRequired = requiredInput(amountIn, amountInMax)
        val routerAddress = uniswapKit.routerAddress(bestTrade.chain)
        val allowanceCaution = direction.takeIf { it == SwapAmountDirection.Out }
            ?.let { insufficientAllowanceCaution(getAllowance(tokenIn, routerAddress), inputRequired) }

        return SwapFinalQuoteEvm(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            amountOut = requireNotNull(tradeData.amountOut),
            amountOutMin = tradeData.amountOutMin(direction, bestTrade.settingSlippage),
            sendTransactionData = SendTransactionData.Evm(
                transactionData = uniswapKit.transactionData(
                    sendSettings.receiveAddress,
                    bestTrade.chain,
                    tradeData,
                ),
                gasLimit = null,
                amount = inputRequired,
            ),
            priceImpact = tradeData.priceImpact,
            fields = finalFields(bestTrade, tokenOut),
            amountInMax = amountInMax,
            cautions = listOfNotNull(allowanceCaution),
        )
    }

    private suspend fun fetchBestTrade(
        tokenIn: Token,
        tokenOut: Token,
        amount: BigDecimal,
        settings: Map<String, Any?>,
        direction: SwapAmountDirection,
    ): UniswapBestTrade {
        val helper = EvmBlockchainHelper(tokenIn.blockchainType)
        val chain = helper.chain
        val recipient = SwapSettingRecipient(settings, tokenOut)
        val slippage = SwapSettingSlippage(settings, TradeOptions.defaultAllowedSlippage)
        val deadline = SwapSettingDeadline(settings, TradeOptions.defaultTtl)
        val options = TradeOptions(
            allowedSlippagePercent = slippage.valueOrDefault(),
            ttl = deadline.valueOrDefault(),
            recipient = recipient.getEthereumKitAddress(),
        )
        val swapData = uniswapKit.swapData(
            helper.getRpcSourceHttp(),
            chain,
            uniswapToken(tokenIn, chain),
            uniswapToken(tokenOut, chain),
        ).await()
        val tradeData = when (direction) {
            SwapAmountDirection.In -> uniswapKit.bestTradeExactIn(swapData, amount, options)
            SwapAmountDirection.Out -> uniswapKit.bestTradeExactOut(swapData, amount, options)
        }
        return UniswapBestTrade(recipient, slippage, deadline, tradeData, chain)
    }

    private fun quoteFields(
        trade: UniswapBestTrade,
        allowance: BigDecimal?,
        inputRequired: BigDecimal,
        tokenIn: Token,
    ): List<DataField> = buildList {
        trade.settingRecipient.value?.let { add(DataFieldRecipient(it)) }
        trade.settingSlippage.value?.let { add(DataFieldSlippage(it)) }
        if (allowance != null && allowance < inputRequired) {
            add(DataFieldAllowance(allowance, tokenIn))
        }
    }

    private fun finalFields(trade: UniswapBestTrade, tokenOut: Token): List<DataField> = buildList {
        trade.settingRecipient.value?.let {
            add(DataFieldRecipientExtended(it, tokenOut.blockchainType))
        }
        trade.settingSlippage.value?.let { add(DataFieldSlippage(it)) }
    }

    private fun isUniswapToken(token: Token): Boolean =
        token.type == TokenType.Native || token.type is TokenType.Eip20

    private fun uniswapToken(token: Token, chain: Chain) = when (val type = token.type) {
        TokenType.Native -> uniswapKit.etherToken(chain)
        is TokenType.Eip20 -> uniswapKit.token(Address(type.address), token.decimals)
        else -> error("Invalid coin for swap: $token")
    }

    private fun TradeData.amountInMax(direction: SwapAmountDirection): BigDecimal? =
        amountInMax.takeIf { direction == SwapAmountDirection.Out }

    private fun TradeData.amountOutMin(
        direction: SwapAmountDirection,
        slippage: SwapSettingSlippage,
    ): BigDecimal {
        val amountOut = requireNotNull(amountOut)
        return when (direction) {
            SwapAmountDirection.In ->
                amountOut - amountOut / BigDecimal(100) * slippage.valueOrDefault()
            SwapAmountDirection.Out -> amountOut
        }
    }
}

private data class UniswapBestTrade(
    val settingRecipient: SwapSettingRecipient,
    val settingSlippage: SwapSettingSlippage,
    val settingDeadline: SwapSettingDeadline,
    val tradeData: TradeData,
    val chain: Chain,
) {
    val settings = listOf(settingRecipient, settingSlippage, settingDeadline)
}
