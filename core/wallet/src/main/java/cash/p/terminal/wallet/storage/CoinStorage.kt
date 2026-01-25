package cash.p.terminal.wallet.storage

import androidx.sqlite.db.SimpleSQLiteQuery
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.Blockchain
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.models.BlockchainEntity
import cash.p.terminal.wallet.models.TokenEntity

class CoinStorage(val marketDatabase: MarketDatabase) {

    private val coinDao = marketDatabase.coinDao()

    fun coin(coinUid: String): Coin? =
        coinDao.getCoin(coinUid)

    fun coins(coinUids: List<String>): List<Coin> =
        coinDao.getCoins(coinUids)

    fun getCoinGeckoIds(uids: List<String>): Map<String, String> =
        coinDao.getCoinGeckoIds(uids)
            .mapNotNull { mapping ->
                mapping.coinGeckoId?.let { mapping.uid to it }
            }
            .toMap()

    fun getCoinGeckoId(uid: String): String? =
        coinDao.getCoinGeckoId(uid)

    fun allCoins(): List<Coin> = coinDao.getAllCoins()

    fun fullCoins(filter: String, limit: Int): List<FullCoin> {
        val sql = """
            SELECT * FROM Coin
            WHERE ${filterWhereStatement()}
            ORDER BY ${filterOrderByStatement()}
            LIMIT ?
        """.trimIndent()

        val args = filterArgs(filter) + limit.toString()
        return coinDao.getFullCoins(SimpleSQLiteQuery(sql, args)).map { it.fullCoin }
    }

    fun fullCoin(uid: String): FullCoin? =
        coinDao.getFullCoin(uid)?.fullCoin

    fun fullCoins(uids: List<String>): List<FullCoin> =
        coinDao.getFullCoins(uids).map { it.fullCoin }

    fun getToken(query: TokenQuery): Token? {
        val (clause, args) = buildTokenQueryClause(query)
        // Order by marketCapRank to prefer canonical coin when duplicates exist
        val sql = """
            SELECT * FROM TokenEntity
            JOIN Coin ON Coin.uid = TokenEntity.coinUid
            WHERE $clause
            ORDER BY ${canonicalCoinOrderBy()}
            LIMIT 1
        """.trimIndent()
        return coinDao.getToken(SimpleSQLiteQuery(sql, args.toTypedArray()))?.token
    }

    fun getTokens(queries: List<TokenQuery>): List<Token> {
        if (queries.isEmpty()) return listOf()

        val uniqueQueries = queries.toSet().toList()
        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        uniqueQueries.forEach { query ->
            val (clause, queryArgs) = buildTokenQueryClause(query)
            whereClauses.add(clause)
            args.addAll(queryArgs)
        }

        // Order by marketCapRank to prefer canonical coin when duplicates exist
        val sql = """
            SELECT * FROM TokenEntity
            JOIN Coin ON Coin.uid = TokenEntity.coinUid
            WHERE ${whereClauses.joinToString(" OR ")}
            ORDER BY ${canonicalCoinOrderBy()}
        """.trimIndent()

        // Deduplicate by tokenQuery - keep only first (canonical) token per contract
        return coinDao.getTokens(SimpleSQLiteQuery(sql, args.toTypedArray()))
            .map { it.token }
            .distinctBy { it.tokenQuery }
    }

    fun getTokens(reference: String): List<Token> {
        val sql = "SELECT * FROM TokenEntity WHERE `TokenEntity`.`reference` LIKE ?"
        val args = arrayOf("%$reference")

        return coinDao.getTokens(SimpleSQLiteQuery(sql, args)).map { it.token }
    }

    fun getTokens(blockchainType: BlockchainType, filter: String, limit: Int): List<Token> {
        val sql = """
            SELECT * FROM TokenEntity
            JOIN Coin ON `Coin`.`uid` = `TokenEntity`.`coinUid`
            WHERE 
              `TokenEntity`.`blockchainUid` = ?
              AND (${filterWhereStatement()})
            ORDER BY ${filterOrderByStatement()}
            LIMIT ?
        """.trimIndent()

        val args = arrayOf(blockchainType.uid) + filterArgs(filter) + limit.toString()
        return coinDao.getTokens(SimpleSQLiteQuery(sql, args)).map { it.token }
    }

    fun getBlockchain(uid: String): Blockchain? =
        coinDao.getBlockchain(uid)?.blockchain

    fun getBlockchains(uids: List<String>): List<Blockchain> =
        coinDao.getBlockchains(uids).map { it.blockchain }

    fun getAllBlockchains(): List<Blockchain> =
        coinDao.getAllBlockchains().map { it.blockchain }

    private fun buildTokenQuerySql(query: TokenQuery, limit: Int? = null): Pair<String, Array<Any>> {
        val (clause, args) = buildTokenQueryClause(query)
        val sql = "SELECT * FROM TokenEntity WHERE $clause" +
                (if (limit != null) " LIMIT $limit" else "")
        return Pair(sql, args.toTypedArray())
    }

    private fun buildTokenQueryClause(query: TokenQuery): Pair<String, List<Any>> {
        val (type, reference) = query.tokenType.values

        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()

        conditions.add("`TokenEntity`.`blockchainUid` = ?")
        args.add(query.blockchainType.uid)

        conditions.add("`TokenEntity`.`type` = ?")
        args.add(type)

        if (reference.isNotBlank()) {
            conditions.add("`TokenEntity`.`reference` LIKE ?")
            args.add("%$reference")
        }

        val clause = conditions.joinToString(" AND ", "(", ")")
        return Pair(clause, args)
    }

    private fun filterWhereStatement() =
        "`Coin`.`name` LIKE ? OR `Coin`.`code` LIKE ?"

    /**
     * Orders tokens to prefer canonical coins (lower marketCapRank = more authoritative).
     * Used to ensure consistent token resolution when multiple coins map to same contract.
     */
    private fun canonicalCoinOrderBy() = """
        CASE WHEN Coin.marketCapRank IS NULL THEN 1 ELSE 0 END,
        Coin.marketCapRank ASC
    """.trimIndent()

    private fun filterOrderByStatement() = """
        priority ASC,
        CASE
            WHEN `Coin`.`code` IS NULL OR `Coin`.`code`  = '' THEN 5
            WHEN `Coin`.`name` LIKE ? THEN 1
            WHEN `Coin`.`code` LIKE ? THEN 2
            WHEN `Coin`.`name` LIKE ? THEN 3
            WHEN `Coin`.`code` LIKE ? THEN 4
            ELSE 5
        END,
        CASE
            WHEN `Coin`.`marketCapRank` IS NULL THEN 1
            ELSE 0
        END,
        `Coin`.`marketCapRank` ASC,
        `Coin`.`name` ASC 
    """

    private fun filterArgs(filter: String): Array<String> {
        val filterParam = "%$filter%"
        val filterStartParam = "$filter%"
        return arrayOf(
            // For WHERE conditions
            filterParam, filterParam,
            // For ORDER BY conditions
            filterStartParam, filterStartParam, filter, filter
        )
    }

    fun update(coins: List<Coin>, blockchainEntities: List<BlockchainEntity>, tokenEntities: List<TokenEntity>) {
        marketDatabase.runInTransaction {
            // TODO It's not good solution for update information
            coinDao.deleteAllCoins()
            coinDao.deleteAllBlockchains()
            coinDao.deleteAllTokens()
            coins.forEach { coinDao.insert(it) }
            blockchainEntities.forEach { coinDao.insert(it) }
            tokenEntities.forEach { coinDao.insert(it) }
        }
    }
}