package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
object Migration_104_105 : Migration(104, 105) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS OfflineSignedTransaction (
                accountId TEXT NOT NULL,
                txHash TEXT NOT NULL,
                blockchainTypeUid TEXT NOT NULL,
                coinCode TEXT NOT NULL,
                tokenDecimals INTEGER NOT NULL,
                amount TEXT NOT NULL,
                toAddress TEXT NOT NULL,
                rawHex TEXT NOT NULL,
                pcashPayload TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY (accountId, txHash),
                FOREIGN KEY (accountId) REFERENCES AccountRecord(id)
                    ON UPDATE CASCADE ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_OfflineSignedTransaction_accountId_createdAt
            ON OfflineSignedTransaction(accountId, createdAt)
            """.trimIndent()
        )
    }
}
