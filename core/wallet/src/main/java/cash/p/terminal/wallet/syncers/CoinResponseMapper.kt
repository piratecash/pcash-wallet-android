package cash.p.terminal.wallet.syncers

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.models.BlockchainEntity
import cash.p.terminal.wallet.models.BlockchainResponse
import cash.p.terminal.wallet.models.CoinResponse
import cash.p.terminal.wallet.models.TokenEntity
import cash.p.terminal.wallet.models.TokenResponse
import io.horizontalsystems.core.entities.BlockchainType

/**
 * Result of mapping raw API responses to storage entities, ready for CoinStorage.update.
 */
data class MappedCoinData(
    val coins: List<Coin>,
    val blockchains: List<BlockchainEntity>,
    val tokens: List<TokenEntity>
)

/**
 * Single source of truth for turning raw coin-list API responses into storage entities.
 * Used both by the live [CoinSyncer] and by the initial-coins-list generator, so the
 * mapping pipeline (map -> transform -> filterValidTokens -> injectVirtualTokens) exists
 * in exactly one place.
 */
object CoinResponseMapper {

    fun mapFetched(
        coinsResponse: List<CoinResponse>,
        blockchainsResponse: List<BlockchainResponse>,
        tokensResponse: List<TokenResponse>,
        virtualCoinMapper: VirtualCoinMapper
    ): MappedCoinData {
        val coins = coinsResponse.map { coinEntity(it) }
        val blockchains = blockchainsResponse.map { blockchainEntity(it) }
        val pipelineTokens = tokenPipeline(coins, blockchains, tokensResponse, virtualCoinMapper)
        // The API occasionally returns duplicate rows for the same token primary key;
        // the database keeps the last one via INSERT OR REPLACE. Apply the same rule to
        // the final list — after order-sensitive transform has seen the raw rows — so
        // the result is exactly what the app's database would hold.
        val dedupedTokens = pipelineTokens.associateBy(::primaryKey).values.toList()

        return MappedCoinData(coins, blockchains, dedupedTokens)
    }

    /**
     * Token pipeline before the final keep-last dedup. Exposed separately so the dump
     * generator can detect primary-key collisions introduced by [transform] or
     * [injectVirtualTokens], as opposed to collisions already present upstream.
     */
    internal fun tokenPipeline(
        coins: List<Coin>,
        blockchains: List<BlockchainEntity>,
        tokensResponse: List<TokenResponse>,
        virtualCoinMapper: VirtualCoinMapper
    ): List<TokenEntity> {
        val tokens = tokensResponse.map { tokenEntity(it) }
        val transformedTokens = transform(tokens)
        val validTokens = filterValidTokens(transformedTokens, blockchains)
        return injectVirtualTokens(coins, validTokens, virtualCoinMapper)
    }

    internal fun primaryKey(token: TokenEntity): List<String> =
        listOf(token.coinUid, token.blockchainUid, token.type, token.reference)

    internal fun coinEntity(response: CoinResponse): Coin =
        Coin(
            response.uid,
            response.name,
            response.code.uppercase(),
            response.market_cap_rank,
            response.coingecko_id,
            response.image,
            response.priority
        )

    internal fun blockchainEntity(response: BlockchainResponse): BlockchainEntity =
        BlockchainEntity(response.uid, response.name, response.url)

    internal fun tokenEntity(response: TokenResponse): TokenEntity =
        TokenEntity(
            response.coin_uid,
            response.blockchain_uid,
            response.type,
            response.decimals,
            response.address ?: ""
        )

    internal fun injectVirtualTokens(
        coins: List<Coin>,
        tokens: List<TokenEntity>,
        virtualCoinMapper: VirtualCoinMapper
    ): List<TokenEntity> {
        val coinsMap = coins.associateBy { it.code }
        val coinsUidMap = coins.associateBy { it.uid }
        val tokensIndex = tokens.associateBy { it.coinUid to it.blockchainUid }

        val virtualTokens = virtualCoinMapper.allMappings.mapNotNull { mapping ->
            coinsUidMap[mapping.virtualCoinUid] ?: return@mapNotNull null
            val realCoin = coinsMap[mapping.realCoinCode] ?: return@mapNotNull null
            val realToken = tokensIndex[realCoin.uid to mapping.blockchainType.uid]
                ?: return@mapNotNull null

            realToken.copy(coinUid = mapping.virtualCoinUid)
        }

        return tokens + virtualTokens
    }

    internal fun transform(tokenEntities: List<TokenEntity>): List<TokenEntity> {
        val derivationReferences = TokenType.Derivation.values().map { it.name }
        val addressTypes = TokenType.AddressType.values().map { it.name }
        val addressSpecTypes = TokenType.AddressSpecType.values().map { it.name }

        var result = tokenEntities
        result = transform(
            tokenEntities = result,
            blockchainUid = BlockchainType.Bitcoin.uid,
            transformedType = "derived",
            references = derivationReferences
        )
        result = transform(
            tokenEntities = result,
            blockchainUid = BlockchainType.Zcash.uid,
            transformedType = "address_spec_type",
            references = addressSpecTypes
        )
        result = transform(
            tokenEntities = result,
            blockchainUid = BlockchainType.Litecoin.uid,
            transformedType = "derived",
            references = derivationReferences
        )
        result = transform(
            tokenEntities = result,
            blockchainUid = BlockchainType.BitcoinCash.uid,
            transformedType = "address_type",
            references = addressTypes
        )

        return result
    }

    private fun transform(
        tokenEntities: List<TokenEntity>,
        blockchainUid: String,
        transformedType: String,
        references: List<String>
    ): List<TokenEntity> {
        val tokenEntitiesMutable = tokenEntities.toMutableList()
        val indexOfFirst = tokenEntitiesMutable.indexOfFirst {
            it.blockchainUid == blockchainUid
        }
        if (indexOfFirst != -1) {
            val tokenEntity = tokenEntitiesMutable.removeAt(indexOfFirst)
            val entities = references.map {
                tokenEntity.copy(type = transformedType, reference = it)
            }
            tokenEntitiesMutable.addAll(entities)
        }
        return tokenEntitiesMutable
    }

    internal fun filterValidTokens(
        tokens: List<TokenEntity>,
        blockchainEntities: List<BlockchainEntity>
    ): List<TokenEntity> {
        val blockchainUids = blockchainEntities.map { it.uid }.toSet()
        return tokens.filter { it.blockchainUid in blockchainUids }
    }
}
