package cash.p.terminal.core.managers

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AutoEnableTokenInfo(
    val type: TokenType,
    val coinName: String,
    val coinCode: String,
    val coinDecimals: Int,
    val coinImage: String?,
)

// CoinStorage builds ~3 SQLite bind args per query, and SQLite caps host params at 999.
// 300 queries → ~900 args, well under the limit and safe across SQLite versions.
private const val MARKET_KIT_QUERY_CHUNK = 300

/**
 * Looks up [queries] in MarketKit, chunking to stay below the SQLite host-parameter limit
 * so a large suspicious-token batch can't overflow and force the whole catch-block fallback.
 *
 * MarketKit's `tokens(...)` is synchronous and hits SQLite directly, so this wrapper pins
 * the call to [Dispatchers.IO] regardless of the caller's dispatcher.
 */
suspend fun MarketKitWrapper.tokensChunked(queries: List<TokenQuery>): List<Token> =
    withContext(Dispatchers.IO) {
        queries.chunked(MARKET_KIT_QUERY_CHUNK).flatMap { tokens(it) }
    }

/**
 * Drops [tokenTypes] not present in [knownTokens] (the curated MarketKit database) so
 * spoof / look-alike tokens never reach auto-enable. [distinct] guards against callers
 * that don't deduplicate before passing.
 *
 * Matches by [TokenType.values] (the storage-layer pair) instead of [TokenType] equality:
 * Stellar tokens are stored as type "stellar" and may round-trip via either
 * [TokenType.Asset] or [TokenType.Unsupported]("stellar", "..."). Matching by [values]
 * keeps both forms equivalent, so legitimate curated assets are never dropped regardless
 * of which form the storage layer produces.
 */
fun filterKnownAutoEnableTokens(
    tokenTypes: List<TokenType>,
    knownTokens: List<Token>,
): List<AutoEnableTokenInfo> {
    val knownByValues = knownTokens.associateBy { it.type.values }
    return tokenTypes.distinct().mapNotNull { type ->
        val token = knownByValues[type.values] ?: return@mapNotNull null
        AutoEnableTokenInfo(
            type = type,
            coinName = token.coin.name,
            coinCode = token.coin.code,
            coinDecimals = token.decimals,
            coinImage = token.coin.image,
        )
    }
}

/**
 * Converts each [AutoEnableTokenInfo] into an [EnabledWallet], skipping tokens the user
 * explicitly deleted. Suspends per item to query [UserDeletedWalletManager].
 */
suspend fun Iterable<AutoEnableTokenInfo>.toEnabledWallets(
    accountId: String,
    blockchainType: BlockchainType,
    userDeletedWalletManager: UserDeletedWalletManager,
): List<EnabledWallet> = mapNotNull { tokenInfo ->
    val tokenQueryId = TokenQuery(blockchainType, tokenInfo.type).id
    if (userDeletedWalletManager.isDeletedByUser(accountId, tokenQueryId)) {
        return@mapNotNull null
    }
    EnabledWallet(
        tokenQueryId = tokenQueryId,
        accountId = accountId,
        coinName = tokenInfo.coinName,
        coinCode = tokenInfo.coinCode,
        coinDecimals = tokenInfo.coinDecimals,
        coinImage = tokenInfo.coinImage,
    )
}
