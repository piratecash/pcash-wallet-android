package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.modules.multiswap.EvmBlockchainHelper
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapAmountDirection
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.SwapQuoteUniswapV3
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
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.uniswapkit.UniswapV3Kit
import io.horizontalsystems.uniswapkit.models.DexType
import io.horizontalsystems.uniswapkit.models.TradeOptions
import io.horizontalsystems.uniswapkit.v3.TradeDataV3
import java.math.BigDecimal

abstract class BaseUniswapV3Provider(
    dexType: DexType,
) : EvmSwapProvider(), IExactOutSwapProvider {
    private val uniswapV3Kit by lazy { UniswapV3Kit.getInstance(dexType) }

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
        val amountIn = requireNotNull(bestTrade.tradeData.tokenAmountIn.decimalAmount)
        val amountInMax = bestTrade.tradeData.amountInMax(direction)
        val inputRequired = requiredInput(amountIn, amountInMax)
        val routerAddress = uniswapV3Kit.routerAddress(bestTrade.chain)
        val allowance = getAllowance(tokenIn, routerAddress)

        return SwapQuoteUniswapV3(
            tradeDataV3 = bestTrade.tradeData,
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
        val amountIn = requireNotNull(tradeData.tokenAmountIn.decimalAmount)
        val amountInMax = tradeData.amountInMax(direction)
        val inputRequired = requiredInput(amountIn, amountInMax)
        val routerAddress = uniswapV3Kit.routerAddress(bestTrade.chain)
        val allowanceCaution = direction.takeIf { it == SwapAmountDirection.Out }
            ?.let { insufficientAllowanceCaution(getAllowance(tokenIn, routerAddress), inputRequired) }

        return SwapFinalQuoteEvm(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            amountOut = requireNotNull(tradeData.tokenAmountOut.decimalAmount),
            amountOutMin = tradeData.amountOutMin(direction, bestTrade.settingSlippage),
            sendTransactionData = SendTransactionData.Evm(
                transactionData = uniswapV3Kit.transactionData(
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
    ): UniswapV3BestTrade {
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
        val tradeData = when (direction) {
            SwapAmountDirection.In -> uniswapV3Kit.bestTradeExactIn(
                helper.getRpcSourceHttp(),
                chain,
                uniswapToken(tokenIn, chain),
                uniswapToken(tokenOut, chain),
                amount,
                options,
            )
            SwapAmountDirection.Out -> uniswapV3Kit.bestTradeExactOut(
                helper.getRpcSourceHttp(),
                chain,
                uniswapToken(tokenIn, chain),
                uniswapToken(tokenOut, chain),
                amount,
                options,
            )
        }
        return UniswapV3BestTrade(recipient, slippage, deadline, tradeData, chain)
    }

    private fun quoteFields(
        trade: UniswapV3BestTrade,
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

    private fun finalFields(trade: UniswapV3BestTrade, tokenOut: Token): List<DataField> = buildList {
        trade.settingRecipient.value?.let {
            add(DataFieldRecipientExtended(it, tokenOut.blockchainType))
        }
        trade.settingSlippage.value?.let { add(DataFieldSlippage(it)) }
    }

    private fun isUniswapToken(token: Token): Boolean =
        token.type == TokenType.Native || token.type is TokenType.Eip20

    private fun uniswapToken(token: Token, chain: Chain) = when (val type = token.type) {
        TokenType.Native -> when (token.blockchainType) {
            BlockchainType.Ethereum,
            BlockchainType.BinanceSmartChain,
            BlockchainType.Polygon,
            BlockchainType.Optimism,
            BlockchainType.Base,
            BlockchainType.ZkSync,
            BlockchainType.ArbitrumOne,
            -> uniswapV3Kit.etherToken(chain)
            else -> error("Invalid coin for swap: $token")
        }
        is TokenType.Eip20 -> uniswapV3Kit.token(Address(type.address), token.decimals)
        else -> error("Invalid coin for swap: $token")
    }

    private fun TradeDataV3.amountInMax(direction: SwapAmountDirection): BigDecimal? =
        tokenAmountInMaximum.decimalAmount.takeIf { direction == SwapAmountDirection.Out }

    private fun TradeDataV3.amountOutMin(
        direction: SwapAmountDirection,
        slippage: SwapSettingSlippage,
    ): BigDecimal {
        val amountOut = requireNotNull(tokenAmountOut.decimalAmount)
        return when (direction) {
            SwapAmountDirection.In ->
                amountOut - amountOut / BigDecimal(100) * slippage.valueOrDefault()
            SwapAmountDirection.Out -> amountOut
        }
    }
}

private data class UniswapV3BestTrade(
    val settingRecipient: SwapSettingRecipient,
    val settingSlippage: SwapSettingSlippage,
    val settingDeadline: SwapSettingDeadline,
    val tradeData: TradeDataV3,
    val chain: Chain,
) {
    val settings = listOf(settingRecipient, settingSlippage, settingDeadline)
}
