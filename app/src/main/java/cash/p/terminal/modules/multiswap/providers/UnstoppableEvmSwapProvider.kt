package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.cache.accountScoped
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.SwapQuoteOffChain
import cash.p.terminal.modules.multiswap.SwapRouteNotFound
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableExecution
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableRoute
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableSignableTx
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.core.stripHexPrefix
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.TransactionData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Wraps Unstoppable's EVM sub-providers (Barter, Circle CCTP), whose committed route is a
 * server-built `signed_transaction` the wallet signs and broadcasts directly (no deposit address,
 * unlike the transfer-based sub-providers in [UnstoppableSwapProvider]).
 *
 * Because the server assembles the raw call data, [validateExecution] re-derives every field this
 * app trusts (chain, method, kind, `from`/`to`, approval spender) from the route itself before a
 * transaction is ever built, and throws rather than silently sending on any mismatch.
 */
class UnstoppableEvmSwapProvider(
    private val descriptor: UnstoppableProvider,
    override val walletUseCase: WalletUseCase,
    private val repository: UnstoppableRepository,
    marketKit: MarketKitWrapper,
    accountManager: IAccountManager,
    private val dispatcherProvider: DispatcherProvider,
    private val providerSupport: OffChainSwapProviderSupport,
) : OffChainSwapProvider {
    override val id = descriptor.id
    override val title = descriptor.title
    override val icon = descriptor.icon
    override val riskType = descriptor.riskType
    override val mevProtectionAvailable: Boolean = false

    private val tokenResolver = UnstoppableTokenResolver(descriptor, repository, marketKit)
    private var cachedRoute: CachedRoute? by accountManager.accountScoped()
    private val mutex = Mutex()

    private data class CachedRoute(val request: RouteRequest, val response: UnstoppableRoute, val timestamp: Long)
    private data class RouteRequest(val sellAsset: String, val buyAsset: String, val sellAmount: String, val destinationAddress: String)

    override suspend fun supports(tokenFrom: Token, tokenTo: Token) = supports(tokenFrom) && supports(tokenTo)

    override suspend fun supports(token: Token): Boolean = withContext(dispatcherProvider.io) {
        tokenResolver.supports(token)
    }

    override suspend fun fetchQuote(tokenIn: Token, tokenOut: Token, amountIn: BigDecimal, settings: Map<String, Any?>): ISwapQuote =
        withContext(dispatcherProvider.io) {
            val assetIn = requireAsset(tokenIn)
            val assetOut = requireAsset(tokenOut)
            val route = repository.rate(
                sellAsset = assetIn.identifier, buyAsset = assetOut.identifier, sellAmount = amountIn.toPlainString(),
                slippage = SLIPPAGE, providers = setOf(descriptor.apiId), chainId = assetIn.chainId,
            ).maxByOrNull { it.expectedBuyAmount ?: BigDecimal.ZERO } ?: throw SwapRouteNotFound()

            val actionRequired = getCreateTokenActionRequired(listOf(tokenIn, tokenOut))
                ?: route.resolvedApprovalSpender?.let { spender ->
                    EvmSwapHelper.actionApprove(EvmSwapHelper.getAllowance(tokenIn, spender), amountIn, spender, tokenIn)
                }

            SwapQuoteOffChain(
                amountOut = route.expectedBuyAmount ?: BigDecimal.ZERO, priceImpact = null, fields = emptyList(), settings = emptyList(),
                tokenIn = tokenIn, tokenOut = tokenOut, amountIn = amountIn,
                actionRequired = actionRequired,
                estimationTime = route.estimatedTimeSeconds,
            )
        }

    override suspend fun fetchFinalQuote(
        tokenIn: Token, tokenOut: Token, amountIn: BigDecimal,
        swapSettings: Map<String, Any?>, sendTransactionSettings: SendTransactionSettings?, swapQuote: ISwapQuote,
    ): ISwapFinalQuote = withContext(dispatcherProvider.io) {
        mutex.withLock {
            val assetIn = requireAsset(tokenIn)
            val assetOut = requireAsset(tokenOut)
            val sourceAddress = walletUseCase.getReceiveAddress(tokenIn)
            val request = RouteRequest(assetIn.identifier, assetOut.identifier, amountIn.toPlainString(), sourceAddress)

            val cached = cachedRoute
            val route = if (
                cached != null &&
                cached.request == request &&
                System.currentTimeMillis() - cached.timestamp < CACHE_FINAL_QUOTE_DURATION
            ) {
                cached.response
            } else {
                repository.swap(
                    sellAsset = request.sellAsset, buyAsset = request.buyAsset, sellAmount = request.sellAmount,
                    slippage = SLIPPAGE, provider = descriptor.apiId, destinationAddress = request.destinationAddress,
                    refundAddress = sourceAddress, sourceAddress = sourceAddress, chainId = assetIn.chainId,
                ).also { cachedRoute = CachedRoute(request, it, System.currentTimeMillis()) }
            }

            val signable = validateExecution(route, tokenIn, sourceAddress)
            val transactionId = route.uuid ?: error("$id: swap response has no uuid, not trackable")
            val amountOut = route.expectedBuyAmount ?: amountIn

            val transactionData = TransactionData(
                to = Address(requireNotNull(signable.to)),
                value = parseHexBigInteger(signable.value),
                input = signable.data.orEmpty().hexStringToByteArray(),
            )
            val gasLimit = signable.gas?.let { parseHexBigInteger(it).toLong() }

            val swapProviderTransaction = providerSupport.buildSwapProviderTransaction(
                provider = SwapProvider.UNSTOPPABLE, transactionId = transactionId, tokenIn = tokenIn, tokenOut = tokenOut,
                amountIn = amountIn, amountOut = amountOut, subProviderId = descriptor.apiId,
            )

            SwapFinalQuoteEvm(
                tokenIn = tokenIn, tokenOut = tokenOut, amountIn = amountIn, amountOut = amountOut,
                amountOutMin = route.minBuyAmount,
                sendTransactionData = SendTransactionData.Evm(transactionData, gasLimit, amount = amountIn),
                priceImpact = null,
                fields = emptyList(),
                swapProviderTransaction = swapProviderTransaction,
            )
        }
    }

    override fun onTransactionCompleted(transaction: SwapProviderTransaction, result: SendTransactionResult) {
        val depositTransactionHash = result.getCanonicalTxHash()?.let { "0x${it.stripHexPrefix()}" }
        providerSupport.onTransactionCompleted(transaction, result, depositTransactionHash = depositTransactionHash)
    }

    /**
     * Re-validates the server-built execution before it is trusted to build a transaction:
     * method/kind must match what this wrapper knows how to send, the execution's chain must be
     * the chain [tokenIn] actually lives on, `from` (when present) must be the sending wallet, and
     * the approval spender must agree between the route's top level and its execution — silently
     * preferring one over the other (as [UnstoppableRoute.resolvedApprovalSpender] does for display
     * purposes) is not acceptable once real funds are about to move.
     */
    private fun validateExecution(route: UnstoppableRoute, tokenIn: Token, sourceAddress: String): UnstoppableSignableTx {
        val execution = route.execution ?: error("$id: no execution in swap response")
        check(execution.method == UnstoppableExecution.METHOD_SIGNED_TRANSACTION) {
            "$id: unexpected execution method ${execution.method}"
        }

        val signable = execution.primarySignable ?: error("$id: no signable transaction in execution")
        check(signable.kind == KIND_EVM) { "$id: unexpected signable kind ${signable.kind}" }
        check(!signable.to.isNullOrBlank()) { "$id: signable transaction has no `to` address" }

        val expectedChainId = tokenResolver.chainId(tokenIn.blockchainType)
        check(expectedChainId != null && execution.chain == expectedChainId) {
            "$id: execution chain ${execution.chain} does not match tokenIn chain $expectedChainId"
        }

        signable.from?.let { from ->
            check(from.equals(sourceAddress, ignoreCase = true)) {
                "$id: signable `from` $from does not match sending address $sourceAddress"
            }
        }

        val topLevelSpender = route.approvalSpender
        val executionSpender = execution.approvalSpender
        if (topLevelSpender != null && executionSpender != null) {
            check(topLevelSpender.equals(executionSpender, ignoreCase = true)) {
                "$id: approval spender mismatch: route=$topLevelSpender execution=$executionSpender"
            }
        }

        return signable
    }

    private fun parseHexBigInteger(hex: String?): BigInteger {
        val stripped = hex?.stripHexPrefix()
        return if (stripped.isNullOrEmpty()) BigInteger.ZERO else BigInteger(stripped, 16)
    }

    private suspend fun requireAsset(token: Token) = tokenResolver.resolve(token) ?: error("$id: no identifier for $token")

    private companion object {
        val SLIPPAGE = BigDecimal("1")
        const val CACHE_FINAL_QUOTE_DURATION = 1000L * 60 * 5
        const val KIND_EVM = "evm"
    }
}
