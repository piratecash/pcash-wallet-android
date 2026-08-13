package cash.p.terminal.wallet.managers

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.models.BlockchainEntity
import cash.p.terminal.wallet.models.TokenEntity

object DumpManager {

    private const val tablesCreation =
        "CREATE TABLE IF NOT EXISTS `BlockchainEntity` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`eip3091url` TEXT, PRIMARY KEY(`uid`));\n" +
                "CREATE TABLE IF NOT EXISTS `Coin` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT " +
                "NULL, `marketCapRank` INTEGER, `coinGeckoId` TEXT, `image` TEXT, `priority` INTEGER NULL, " +
                "PRIMARY KEY(`uid`));\n" +
                "CREATE TABLE IF NOT EXISTS `TokenEntity` (`coinUid` TEXT NOT NULL, `blockchainUid` TEXT NOT " +
                "NULL, `type` TEXT NOT NULL, `decimals` INTEGER, `reference` TEXT NOT NULL, PRIMARY KEY(`coinUid`, " +
                "`blockchainUid`, `type`, `reference`), FOREIGN KEY(`coinUid`) REFERENCES `Coin`(`uid`) ON UPDATE " +
                "NO ACTION ON DELETE CASCADE , FOREIGN KEY(`blockchainUid`) REFERENCES `BlockchainEntity`(`uid`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )\n"

    fun getInitialDump(
        blockchains: List<BlockchainEntity>,
        coins: List<Coin>,
        tokens: List<TokenEntity>
    ): String {
        val insertQueries = StringBuilder()
        insertQueries.append(tablesCreation)

        // Step 1: Insert Blockchains (no dependencies)
        blockchains.forEach { blockchain ->
            val eipUrl = blockchain.eip3091url?.let { sqlEscape(it) } ?: "null"
            val insertQuery =
                "INSERT OR REPLACE INTO BlockchainEntity VALUES(${sqlEscape(blockchain.uid)},${
                    sqlEscape(
                        blockchain.name
                    )
                },$eipUrl);"
            insertQueries.append(insertQuery).append("\n")
        }

        // Step 2: Insert Coins (depend on nothing)
        coins.forEach { coin ->
            val uid = sqlEscape(coin.uid)
            val name = sqlEscape(coin.name)
            val code = sqlEscape(coin.code)
            val coinGeckoId = coin.coinGeckoId?.let { sqlEscape(it) } ?: "null"
            val image = coin.image?.let { sqlEscape(it) } ?: "null"
            val priority = coin.priority ?: "null"
            val rank = coin.marketCapRank?.toString() ?: "null"

            val insertQuery =
                "INSERT OR REPLACE INTO Coin VALUES($uid, $name, $code, $rank, $coinGeckoId, $image, " +
                        "$priority);"

            insertQueries.append(insertQuery).append("\n")
        }


        // Step 3: Insert Tokens (depend on Coins and Blockchains)
        tokens.forEach { token ->
            val reference = sqlEscape(token.reference)
            val insertQuery =
                "INSERT OR REPLACE INTO TokenEntity VALUES(${sqlEscape(token.coinUid)},${
                    sqlEscape(
                        token.blockchainUid
                    )
                }," +
                        "${sqlEscape(token.type)},${token.decimals},$reference);"
            insertQueries.append(insertQuery).append("\n")
        }

        return insertQueries.toString()
    }

    private fun sqlEscape(value: String) = "'" + value.replace("'", "''") + "'"
}
