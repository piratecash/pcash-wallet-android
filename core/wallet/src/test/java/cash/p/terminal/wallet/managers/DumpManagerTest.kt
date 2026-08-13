package cash.p.terminal.wallet.managers

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.models.BlockchainEntity
import cash.p.terminal.wallet.models.TokenEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DumpManagerTest {

    private val tablesCreation =
        "CREATE TABLE IF NOT EXISTS `BlockchainEntity` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`eip3091url` TEXT, PRIMARY KEY(`uid`));\n" +
                "CREATE TABLE IF NOT EXISTS `Coin` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, " +
                "`marketCapRank` INTEGER, `coinGeckoId` TEXT, `image` TEXT, `priority` INTEGER NULL, " +
                "PRIMARY KEY(`uid`));\n" +
                "CREATE TABLE IF NOT EXISTS `TokenEntity` (`coinUid` TEXT NOT NULL, `blockchainUid` TEXT NOT " +
                "NULL, `type` TEXT NOT NULL, `decimals` INTEGER, `reference` TEXT NOT NULL, PRIMARY KEY(`coinUid`, " +
                "`blockchainUid`, `type`, `reference`), " +
                "FOREIGN KEY(`coinUid`) REFERENCES `Coin`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`blockchainUid`) REFERENCES `BlockchainEntity`(`uid`) ON UPDATE NO ACTION ON DELETE " +
                "CASCADE )\n"

    private val expectedDump = tablesCreation +
            "INSERT OR REPLACE INTO BlockchainEntity VALUES('chain''s-uid','Chain''s Name'," +
            "'https://etherscan.io/tx''s');\n" +
            "INSERT OR REPLACE INTO BlockchainEntity VALUES('no-url-chain','No Url Chain',null);\n" +
            "INSERT OR REPLACE INTO Coin VALUES('coin''s-uid', 'Coin''s Name', 'cus', 5, 'gecko''s-id', " +
            "'https://img''s.png', 3);\n" +
            "INSERT OR REPLACE INTO Coin VALUES('null-coin', 'Null Coin', 'nul', null, null, null, null);\n" +
            "INSERT OR REPLACE INTO TokenEntity VALUES('token''s-coin','token''s-chain','eip20''s-type',18," +
            "'0xAbc''s123');\n" +
            "INSERT OR REPLACE INTO TokenEntity VALUES('coin2','chain2','native',null,'');\n"

    @Test
    fun getInitialDump_quotedAndNullFields_escapesQuotesAndFormatsNulls() {
        val blockchainWithQuotesAndUrl = BlockchainEntity(
            uid = "chain's-uid",
            name = "Chain's Name",
            eip3091url = "https://etherscan.io/tx's"
        )
        val blockchainWithNullUrl = BlockchainEntity(
            uid = "no-url-chain",
            name = "No Url Chain",
            eip3091url = null
        )
        val coinWithQuotesAndValues = Coin(
            uid = "coin's-uid",
            name = "Coin's Name",
            code = "cus",
            marketCapRank = 5,
            coinGeckoId = "gecko's-id",
            image = "https://img's.png",
            priority = 3
        )
        val coinWithNullFields = Coin(
            uid = "null-coin",
            name = "Null Coin",
            code = "nul",
            marketCapRank = null,
            coinGeckoId = null,
            image = null,
            priority = null
        )
        val tokenWithQuotesEverywhere = TokenEntity(
            coinUid = "token's-coin",
            blockchainUid = "token's-chain",
            type = "eip20's-type",
            decimals = 18,
            reference = "0xAbc's123"
        )
        val tokenWithNullDecimalsAndEmptyReference = TokenEntity(
            coinUid = "coin2",
            blockchainUid = "chain2",
            type = "native",
            decimals = null,
            reference = ""
        )

        val dump = DumpManager.getInitialDump(
            blockchains = listOf(blockchainWithQuotesAndUrl, blockchainWithNullUrl),
            coins = listOf(coinWithQuotesAndValues, coinWithNullFields),
            tokens = listOf(tokenWithQuotesEverywhere, tokenWithNullDecimalsAndEmptyReference)
        )

        assertEquals(expectedDump, dump)
    }

    @Test
    fun getInitialDump_emptyLists_returnsOnlyTablesCreation() {
        val dump = DumpManager.getInitialDump(emptyList(), emptyList(), emptyList())

        assertEquals(tablesCreation, dump)
    }
}
