package cash.p.terminal.wallet.managers

import android.content.Context
import android.database.DatabaseUtils
import cash.p.terminal.wallet.storage.MarketDatabase
import java.io.File
import java.io.FileOutputStream

class DumpManager(private val marketDatabase: MarketDatabase) {

    private val tablesCreation =
        "CREATE TABLE IF NOT EXISTS `BlockchainEntity` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `eip3091url` TEXT, PRIMARY KEY(`uid`));\n" +
                "CREATE TABLE IF NOT EXISTS `Coin` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, `marketCapRank` INTEGER, `coinGeckoId` TEXT, `image` TEXT, `priority` INTEGER NULL, PRIMARY KEY(`uid`));\n" +
                "CREATE TABLE IF NOT EXISTS `TokenEntity` (`coinUid` TEXT NOT NULL, `blockchainUid` TEXT NOT NULL, `type` TEXT NOT NULL, `decimals` INTEGER, `reference` TEXT NOT NULL, PRIMARY KEY(`coinUid`, `blockchainUid`, `type`, `reference`), FOREIGN KEY(`coinUid`) REFERENCES `Coin`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`blockchainUid`) REFERENCES `BlockchainEntity`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE )\n"

    fun getInitialDump(): String {
        val insertQueries = StringBuilder()
        insertQueries.append(tablesCreation)

        // Step 1: Insert Blockchains (no dependencies)
        val blockchains = marketDatabase.blockchainEntityDao().getAll()
        blockchains.forEach { blockchain ->
            val eipUrl = blockchain.eip3091url?.let { "'$it'" } ?: "null"
            val insertQuery =
                "INSERT OR REPLACE INTO BlockchainEntity VALUES('${blockchain.uid}',${DatabaseUtils.sqlEscapeString(blockchain.name)},$eipUrl);"
            insertQueries.append(insertQuery).append("\n")
        }

        // Step 2: Insert Coins (depend on nothing)
        val coins = marketDatabase.coinDao().getAllCoins()
        coins.forEach { coin ->
            val uid = DatabaseUtils.sqlEscapeString(coin.uid)
            val name = DatabaseUtils.sqlEscapeString(coin.name)
            val code = DatabaseUtils.sqlEscapeString(coin.code)
            val coinGeckoId = coin.coinGeckoId?.let { DatabaseUtils.sqlEscapeString(it) } ?: "null"
            val image = coin.image?.let { DatabaseUtils.sqlEscapeString(it) } ?: "null"
            val priority = coin.priority ?: "null"
            val rank = coin.marketCapRank?.toString() ?: "null"

            val insertQuery = "INSERT OR REPLACE INTO Coin VALUES($uid, $name, $code, $rank, $coinGeckoId, $image, $priority);".trimIndent()

            insertQueries.append(insertQuery).append("\n")
        }


        // Step 3: Insert Tokens (depend on Coins and Blockchains)
        val tokens = marketDatabase.tokenEntityDao().getAll()
        tokens.forEach { token ->
            val reference = token.reference?.let { "'$it'" } ?: "null"
            val insertQuery =
                "INSERT OR REPLACE INTO TokenEntity VALUES('${token.coinUid}','${token.blockchainUid}','${token.type}',${token.decimals},$reference);"
            insertQueries.append(insertQuery).append("\n")
        }

        return insertQueries.toString()
    }

    /**
     * Exports the current database dump to app's internal cache directory.
     * The file will be saved to app's cache directory (accessible via adb).
     *
     * Usage: Call this method from a debug screen or temporary button.
     * After export, you can pull the file via adb:
     * adb pull /data/data/cash.p.terminal.dev/cache/initial_coins_list core/wallet/src/main/assets/initial_coins_list
     * or use: adb exec-out run-as cash.p.terminal.dev cat cache/initial_coins_list > core/wallet/src/main/assets/initial_coins_list
     */
    fun exportDumpToFile(context: Context): String {
        val dump = getInitialDump()
        val file = File(context.cacheDir, "initial_coins_list")

        FileOutputStream(file).use { output ->
            output.write(dump.toByteArray())
        }

        return file.absolutePath
    }
}