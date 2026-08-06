package cash.p.terminal.core.managers

import cash.p.terminal.core.isSupported
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.isLegacyZcash
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

data class TrustedTokenMetadata(
    val coinName: String?,
    val coinCode: String?,
    val decimals: Int?,
)

data class StoredToken(
    val tokenQueryId: String,
    val metadata: TrustedTokenMetadata?,
    val trustedDecimals: Boolean,
)

fun storedToken(
    tokenQueryId: String,
    trustedDecimals: Boolean,
    coinName: String?,
    coinCode: String?,
    decimals: Int?,
): StoredToken = StoredToken(
    tokenQueryId = tokenQueryId,
    metadata = TrustedTokenMetadata(coinName, coinCode, decimals),
    trustedDecimals = trustedDecimals,
)

// SQLite caps bind params at 999; ~3 args/query keeps 300-query chunks safely under that.
private const val MARKET_KIT_QUERY_CHUNK = 300

/** Synchronous SQLite call under the hood, so this pins [Dispatchers.IO] regardless of caller. */
suspend fun MarketKitWrapper.tokensChunked(queries: List<TokenQuery>): List<Token> =
    withContext(Dispatchers.IO) {
        queries.chunked(MARKET_KIT_QUERY_CHUNK).flatMap { tokens(it) }
    }

/**
 * Matches by [TokenType.values] rather than equality: Stellar tokens can round-trip as either
 * [TokenType.Asset] or [TokenType.Unsupported], and both must match the same catalog entry.
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

/** Folds EVM address casing the same way the curated catalog does. */
private val TokenQuery.canonical: TokenQuery
    get() = (tokenType as? TokenType.Eip20)
        ?.let { TokenQuery.eip20(blockchainType, it.address) }
        ?: this

/**
 * Deliberately stricter than `CoinStorage.getTokens`'s `reference LIKE '%<ref>'` match, which can
 * resolve a truncated or case-different reference to the wrong asset.
 */
private val TokenQuery.curatedIdentity: String
    get() {
        val (type, reference) = canonical.tokenType.values
        return "${blockchainType.uid}|$type|$reference"
    }

/**
 * [TokenType.fromId] drops the reference segment of an `Unsupported` id, so an id that doesn't
 * round-trip would collapse onto a different token on the next load.
 */
private fun TokenQuery.roundTrips(): Boolean = TokenQuery.fromId(id) == this

private fun persistableId(curated: TokenQuery, stored: TokenQuery): String? =
    listOf(curated, stored.canonical).firstOrNull { it.roundTrips() }?.id

/** A token whose row was restorable but not trusted enough to enable without the user's say. */
data class DeclinedToken(
    val tokenQueryId: String,
    val coinName: String,
    val coinCode: String,
    val decimals: Int? = null,
)

/** Control characters, bidi overrides and line/paragraph separators — never legitimate in a label. */
private val UNSAFE_LABEL_CHARS = Regex("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]+")
private val WHITESPACE_RUN = Regex("\\s+")

/** Source is untrusted: strip bidi/control chars so nothing downstream can be broken by them. */
fun String.normalizedTokenLabel(): String =
    replace(UNSAFE_LABEL_CHARS, " ").replace(WHITESPACE_RUN, " ").trim()

private const val STORED_LABEL_MAX_LENGTH = 64

/** Bounded, and null (not blank) when nothing legible survives normalising. */
private fun String.storableTokenLabel(): String? =
    normalizedTokenLabel().take(STORED_LABEL_MAX_LENGTH).trim().ifBlank { null }

/** The outcome of curating a batch of stored rows — see [curatedEnabledWallets]. */
data class CurationResult(
    val enabled: List<EnabledWallet>,
    val declined: List<DeclinedToken>,
)

/** A row resolved to a wallet, and whether persisting it needs the user's approval first. */
private class Candidate(val wallet: EnabledWallet, val needsApproval: Boolean)

/**
 * A row the catalog resolves always takes name/code/image/decimals from the catalog, never from
 * the caller's row, since that row is attacker-controlled in a backup file or airdropped account.
 * A row the catalog can't resolve (manually added) lands in [CurationResult.declined] instead of
 * [CurationResult.enabled] unless already in [approvedTokenQueryIds].
 */
suspend fun MarketKitWrapper.curatedEnabledWallets(
    accountId: String,
    storedTokens: Collection<StoredToken>,
    approvedTokenQueryIds: Set<String>,
): CurationResult {
    val parsed = storedTokens.distinctBy { it.tokenQueryId }
        .mapNotNull { stored -> TokenQuery.fromId(stored.tokenQueryId)?.let { it to stored } }
    val curatedByIdentity = tokensChunked(parsed.map { it.first })
        .associateBy { it.tokenQuery.curatedIdentity }

    // Rows differing only by EVM address casing collapse onto one curated id; EnabledWallet
    // has a unique index on (accountId, tokenQueryId).
    val candidates = parsed
        .mapNotNull { (query, stored) -> candidate(accountId, query, stored, curatedByIdentity) }
        .distinctBy { it.wallet.tokenQueryId }

    val (enabled, declined) = candidates.partition {
        !it.needsApproval || it.wallet.tokenQueryId in approvedTokenQueryIds
    }
    return CurationResult(
        enabled = enabled.map { it.wallet },
        declined = declined.map { it.wallet.toDeclinedToken() },
    )
}

/**
 * The manually-added branch always needs approval; [StoredToken.trustedDecimals] only decides
 * whether its stored decimals may fill the gap for a catalog [TokenType.Unsupported] token.
 */
private fun candidate(
    accountId: String,
    query: TokenQuery,
    stored: StoredToken,
    curatedByIdentity: Map<String, Token>,
): Candidate? {
    val token = curatedByIdentity[query.curatedIdentity]
    return when {
        token != null -> {
            val trustedDecimals = if (stored.trustedDecimals) stored.metadata?.decimals else null
            token.toEnabledWallet(accountId, query, trustedDecimals)
                ?.let { Candidate(it, needsApproval = false) }
        }

        // Legacy Zcash carries no metadata: WalletStorage expands it into curated tokens on load.
        query.isLegacyZcash -> Candidate(
            wallet = EnabledWallet(tokenQueryId = query.canonical.id, accountId = accountId, coinImage = null),
            needsApproval = false,
        )

        else -> stored.metadata?.toEnabledWallet(accountId, query)
            ?.let { Candidate(it, needsApproval = true) }
    }
}

private fun EnabledWallet.toDeclinedToken() = DeclinedToken(
    tokenQueryId = tokenQueryId,
    coinName = coinName.orEmpty(),
    coinCode = coinCode.orEmpty(),
    decimals = coinDecimals,
)

/**
 * Rebuilds a manually-added token's row. Dropped if the chain/type pair isn't [isSupported] — it
 * would load as a permanently unsynced wallet — or if name/code/id fail to round-trip.
 */
private fun TrustedTokenMetadata.toEnabledWallet(
    accountId: String,
    storedQuery: TokenQuery,
): EnabledWallet? {
    if (decimals == null) return null
    if (!storedQuery.isSupported) return null
    val name = coinName?.storableTokenLabel() ?: return null
    val code = coinCode?.storableTokenLabel() ?: return null
    val id = storedQuery.canonical.takeIf { it.roundTrips() }?.id ?: return null
    return EnabledWallet(
        tokenQueryId = id,
        accountId = accountId,
        coinName = name,
        coinCode = code,
        coinDecimals = decimals,
        coinImage = null
    )
}

private fun Token.toEnabledWallet(
    accountId: String,
    storedQuery: TokenQuery,
    trustedDecimals: Int?,
): EnabledWallet? {
    // Caller-supplied decimals only fill the gap for the one type the catalog has none for.
    val coinDecimals = if (type is TokenType.Unsupported) trustedDecimals else decimals
    if (coinDecimals == null) return null
    val id = persistableId(tokenQuery, storedQuery) ?: return null
    return EnabledWallet(
        tokenQueryId = id,
        accountId = accountId,
        coinName = coin.name,
        coinCode = coin.code,
        coinDecimals = coinDecimals,
        coinImage = coin.image
    )
}

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
