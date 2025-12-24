package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_85_86 : Migration(85, 86) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Create a new table with unique constraint on (accountId, tokenQueryId)
        db.execSQL(
            """
            CREATE TABLE EnabledWallet_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                tokenQueryId TEXT NOT NULL,
                accountId TEXT NOT NULL,
                walletOrder INTEGER,
                coinName TEXT,
                coinCode TEXT,
                coinDecimals INTEGER,
                coinImage TEXT,
                FOREIGN KEY(accountId) REFERENCES AccountRecord(id) ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
            )
            """
        )

        // Step 2: Copy data, keeping only the first occurrence of each (accountId, tokenQueryId) pair
        // This deduplicates existing data
        db.execSQL(
            """
            INSERT INTO EnabledWallet_new (id, tokenQueryId, accountId, walletOrder, coinName, coinCode, coinDecimals, coinImage)
            SELECT id, tokenQueryId, accountId, walletOrder, coinName, coinCode, coinDecimals, coinImage
            FROM EnabledWallet
            WHERE id IN (
                SELECT MIN(id) FROM EnabledWallet GROUP BY accountId, tokenQueryId
            )
            """
        )

        // Step 3: Drop old table
        db.execSQL("DROP TABLE EnabledWallet")

        // Step 4: Rename new table
        db.execSQL("ALTER TABLE EnabledWallet_new RENAME TO EnabledWallet")

        // Step 5: Create indexes (including unique constraint)
        db.execSQL("CREATE INDEX index_EnabledWallet_accountId ON EnabledWallet(accountId)")
        db.execSQL("CREATE UNIQUE INDEX index_EnabledWallet_accountId_tokenQueryId ON EnabledWallet(accountId, tokenQueryId)")
    }
}
