package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.cache.accountScoped
import cash.p.terminal.core.derivation
import cash.p.terminal.core.isEvm
import cash.p.terminal.core.isUtxoBased
import cash.p.terminal.core.nativeTokenQueries
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.SwapQuoteOffChain
import cash.p.terminal.modules.multiswap.SwapRouteNotFound
import cash.p.terminal.modules.multiswap.action.ActionCreate
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.ui.DataFieldRecipientExtended
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableRoute
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/**
 * Resolves which tokens a given Unstoppable sub-provider supports and what identifier/chainId
 * to send the v2 API for a token, backed by `GET /v2/tokens?provider=<apiId>`.
 *
 * The v2 response has two shapes (mirrors upstream `USwapProvider.ProviderData`):
 * - a full `tokens` list -> build an exact [Token] -> identifier map ([ProviderData.TokenMap]);
 * - an empty `tokens` list with only `supportedChainIds` -> the provider supports whole chains and
 *   identifiers must be derived per-token ([ProviderData.ChainIds]).
 *
 * Fetched lazily (first [supports]/[resolve] call, not at `start()`) and cached with a TTL so a
 * disabled sub-provider never calls the network, per-descriptor single-flight via [mutex].
 */
internal class UnstoppableTokenResolver(
    private val descriptor: UnstoppableProvider,
    private val repository: UnstoppableRepository,
    private val marketKit: MarketKitWrapper,
) {
    private sealed class ProviderData {
        data class TokenMap(val map: Map<Token, String>) : ProviderData()
        data class ChainIds(val ids: Set<BlockchainType>) : ProviderData()
    }

    data class ResolvedAsset(val identifier: String, val chainId: String?)

    private val mutex = Mutex()
    private var data: ProviderData? = null
    private var fetchedAt: Long = 0L

    suspend fun supports(token: Token): Boolean = when (val d = getData()) {
        is ProviderData.TokenMap -> d.map.containsKey(token)
        is ProviderData.ChainIds -> token.blockchainType in d.ids
    }

    /** Chain-id string for [blockchainType] in the same space as `/v2/tokens`' `chainId` field. */
    fun chainId(blockchainType: BlockchainType): String? = chainIdByBlockchainType[blockchainType]

    suspend fun resolve(token: Token): ResolvedAsset? = when (val d = getData()) {
        is ProviderData.TokenMap -> d.map[token]?.let { ResolvedAsset(it, chainId = null) }
        is ProviderData.ChainIds -> {
            if (token.blockchainType !in d.ids) {
                null
            } else {
                deriveIdentifier(token)?.let {
                    ResolvedAsset(it, chainId = chainIdByBlockchainType[token.blockchainType])
                }
            }
        }
    }

    private suspend fun getData(): ProviderData = mutex.withLock {
        val cached = data
        if (cached != null && System.currentTimeMillis() - fetchedAt < CACHE_DURATION) {
            cached
        } else {
            fetchProviderData().also {
                data = it
                fetchedAt = System.currentTimeMillis()
            }
        }
    }

    private suspend fun fetchProviderData(): ProviderData {
        val response = repository.getTokens(descriptor.apiId)
        if (response.tokens.isEmpty()) {
            val ids = response.supportedChainIds.mapNotNull { blockchainTypeByChainId[it] }.toSet()
            return ProviderData.ChainIds(ids)
        }

        val map = mutableMapOf<Token, String>()
        for (remoteToken in response.tokens) {
            val blockchainType = blockchainTypeByChainId[remoteToken.chainId] ?: continue
            addTokens(map, blockchainType, remoteToken.address, remoteToken.identifier)
        }
        return ProviderData.TokenMap(map)
    }

    private fun addTokens(
        map: MutableMap<Token, String>,
        blockchainType: BlockchainType,
        address: String?,
        identifier: String,
    ) {
        when {
            blockchainType.isEvm || blockchainType == BlockchainType.Tron ->
                addSingle(map, blockchainType, contractOrNative(address, TokenType::Eip20), identifier)

            blockchainType == BlockchainType.Solana ->
                addSingle(map, blockchainType, contractOrNative(address, TokenType::Spl), identifier)

            blockchainType == BlockchainType.Ton ->
                addSingle(map, blockchainType, contractOrNative(address, TokenType::Jetton), identifier)

            // Non-native Stellar assets aren't exposed here in a decomposable (code, issuer) shape by
            // this endpoint, so only native XLM is resolved.
            blockchainType == BlockchainType.Stellar ->
                if (address.isNullOrBlank()) addSingle(map, blockchainType, TokenType.Native, identifier)

            blockchainType == BlockchainType.Monero -> addSingle(map, blockchainType, TokenType.Native, identifier)

            blockchainType.isUtxoBased -> addUtxoTokens(map, blockchainType, identifier)

            else -> Unit
        }
    }

    private fun contractOrNative(address: String?, contract: (String) -> TokenType): TokenType =
        address?.let(contract) ?: TokenType.Native

    private fun addSingle(
        map: MutableMap<Token, String>,
        blockchainType: BlockchainType,
        tokenType: TokenType,
        identifier: String,
    ) {
        marketKit.token(TokenQuery(blockchainType, tokenType))?.let { map[it] = identifier }
    }

    private fun addUtxoTokens(map: MutableMap<Token, String>, blockchainType: BlockchainType, identifier: String) {
        val queries = blockchainType.nativeTokenQueries.let { queries ->
            // Taproot (Bip86) is excluded, matching BaseThorChainProvider's convention.
            if (blockchainType == BlockchainType.Litecoin) {
                queries.filterNot { it.tokenType.derivation == TokenType.Derivation.Bip86 }
            } else {
                queries
            }
        }
        marketKit.tokens(queries).forEach { map[it] = identifier }
    }

    private fun deriveIdentifier(token: Token): String? {
        val type = token.type
        return when {
            type is TokenType.Eip20 -> type.address
            type == TokenType.Native && token.blockchainType.isEvm -> NATIVE_EVM_PLACEHOLDER
            else -> null
        }
    }

    private companion object {
        const val CACHE_DURATION = 1000L * 60 * 30
        const val NATIVE_EVM_PLACEHOLDER = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"

        val blockchainTypeByChainId = mapOf(
            "1" to BlockchainType.Ethereum,
            "10" to BlockchainType.Optimism,
            "56" to BlockchainType.BinanceSmartChain,
            "100" to BlockchainType.Gnosis,
            "137" to BlockchainType.Polygon,
            "8453" to BlockchainType.Base,
            "42161" to BlockchainType.ArbitrumOne,
            "43114" to BlockchainType.Avalanche,
            "728126428" to BlockchainType.Tron,
            "solana" to BlockchainType.Solana,
            "bitcoin" to BlockchainType.Bitcoin,
            "bitcoincash" to BlockchainType.BitcoinCash,
            "litecoin" to BlockchainType.Litecoin,
            "zcash" to BlockchainType.Zcash,
            "dash" to BlockchainType.Dash,
            "ecash" to BlockchainType.ECash,
            "stellar" to BlockchainType.Stellar,
            "ton" to BlockchainType.Ton,
            "monero" to BlockchainType.Monero,
        )
        val chainIdByBlockchainType = blockchainTypeByChainId.entries.associate { (k, v) -> v to k }
    }
}

/**
 * Wraps the transfer/deposit-style Unstoppable sub-providers (`execution.method == "transfer"`):
 * NEAR, LetsExchange, StealthEX, CCE, Swapuz, Pegasus. One instance per [descriptor].
 *
 * A deposit address is quoted by `/v2/swap`, the user sends [amountIn] to it like a regular
 * on-chain transfer (built generically by [OffChainSwapProviderSupport.buildTransactionData]), and
 * the swap is tracked asynchronously via the shared [OffChainSwapProvider] uuid-preserving flow.
 */
class UnstoppableSwapProvider(
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

    // SwapConfirmViewModel calls final quote too many times, so cache the committed route.
    private var cachedRoute: CachedRoute? by accountManager.accountScoped()
    private val mutex = Mutex()

    private data class CachedRoute(
        val request: RouteRequest,
        val response: UnstoppableRoute,
        val timestamp: Long,
    )

    private data class RouteRequest(
        val sellAsset: String,
        val buyAsset: String,
        val sellAmount: String,
        val destinationAddress: String,
    )

    override suspend fun supports(tokenFrom: Token, tokenTo: Token) =
        supports(tokenFrom) && supports(tokenTo)

    override suspend fun supports(token: Token): Boolean {
        if (token.isZcashShielded) return false
        return withContext(dispatcherProvider.io) {
            tokenResolver.supports(token)
        }
    }

    override suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        settings: Map<String, Any?>,
    ): ISwapQuote = withContext(dispatcherProvider.io) {
        val assetIn = requireAsset(tokenIn)
        val assetOut = requireAsset(tokenOut)
        val route = repository.rate(
            sellAsset = assetIn.identifier,
            buyAsset = assetOut.identifier,
            sellAmount = amountIn.toPlainString(),
            slippage = SLIPPAGE,
            providers = setOf(descriptor.apiId),
            chainId = assetIn.chainId,
        ).maxByOrNull { it.expectedBuyAmount ?: BigDecimal.ZERO } ?: throw SwapRouteNotFound()

        SwapQuoteOffChain(
            amountOut = route.expectedBuyAmount ?: BigDecimal.ZERO,
            priceImpact = null,
            fields = emptyList(),
            settings = emptyList(),
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            actionRequired = getCreateTokenActionRequired(listOf(tokenIn, tokenOut)),
            estimationTime = route.estimatedTimeSeconds,
        )
    }

    override fun getCreateTokenActionRequired(tokens: List<Token>): ActionCreate? =
        providerSupport.getCreateTokenActionRequired(tokens)

    override suspend fun getWarningMessage(tokenIn: Token, tokenOut: Token): TranslatableString? =
        withContext(dispatcherProvider.io) {
            providerSupport.getWarningMessage(tokenIn)
        }

    override suspend fun fetchFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        swapSettings: Map<String, Any?>,
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: ISwapQuote,
    ): ISwapFinalQuote = withContext(dispatcherProvider.io) {
        mutex.withLock {
            val assetIn = requireAsset(tokenIn)
            val assetOut = requireAsset(tokenOut)
            val request = RouteRequest(
                sellAsset = assetIn.identifier,
                buyAsset = assetOut.identifier,
                sellAmount = amountIn.toPlainString(),
                destinationAddress = walletUseCase.getReceiveAddress(tokenOut),
            )

            val route = commitSwap(tokenIn, request, assetIn.chainId)

            val execution = route.execution
                ?: error("$id: no execution in swap response")
            val depositAddress = execution.resolvedDepositAddress()
                ?: error("$id: no deposit address in swap response")
            val transactionId = route.uuid
                ?: error("$id: swap response has no uuid, not trackable")
            val amountOut = route.expectedBuyAmount ?: amountIn

            val swapProviderTransaction = providerSupport.buildSwapProviderTransaction(
                provider = SwapProvider.UNSTOPPABLE,
                transactionId = transactionId,
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                amountIn = amountIn,
                amountOut = amountOut,
                subProviderId = descriptor.apiId,
            )

            SwapFinalQuoteEvm(
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                amountIn = amountIn,
                amountOut = amountOut,
                amountOutMin = route.minBuyAmount,
                sendTransactionData = providerSupport.buildTransactionData(
                    tokenIn = tokenIn,
                    amountIn = amountIn,
                    depositAddress = depositAddress,
                    memo = execution.resolvedMemo(),
                ),
                priceImpact = null,
                fields = listOf(DataFieldRecipientExtended(Address(depositAddress), tokenIn.blockchainType)),
                swapProviderTransaction = swapProviderTransaction,
            )
        }
    }

    override fun onTransactionCompleted(transaction: SwapProviderTransaction, result: SendTransactionResult) =
        providerSupport.onTransactionCompleted(
            transaction, result,
            depositTransactionHash = result.getCanonicalTxHash(),
        )

    private suspend fun commitSwap(tokenIn: Token, request: RouteRequest, chainId: String?): UnstoppableRoute {
        val cached = cachedRoute
        if (cached != null && cached.request == request &&
            System.currentTimeMillis() - cached.timestamp < CACHE_FINAL_QUOTE_DURATION
        ) {
            return cached.response
        }
        return repository.swap(
            sellAsset = request.sellAsset,
            buyAsset = request.buyAsset,
            sellAmount = request.sellAmount,
            slippage = SLIPPAGE,
            provider = descriptor.apiId,
            destinationAddress = request.destinationAddress,
            refundAddress = providerSupport.getRefundAddress(tokenIn),
            chainId = chainId,
        ).also { cachedRoute = CachedRoute(request, it, System.currentTimeMillis()) }
    }

    private suspend fun requireAsset(token: Token) =
        tokenResolver.resolve(token) ?: error("$id: no identifier for $token")

    private companion object {
        val SLIPPAGE = BigDecimal("1")
        const val CACHE_FINAL_QUOTE_DURATION = 1000L * 60 * 5
    }
}
